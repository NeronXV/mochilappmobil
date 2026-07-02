const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const stripe = require("stripe");

/**
 * Callable function to create a Stripe PaymentIntent for Mochilapp.
 * Requires STRIPE_MOCHILAPP_SECRET_KEY to be set in Firebase Secrets.
 */
exports.createPaymentIntent = functions
    .runWith({ secrets: ["STRIPE_MOCHILAPP_SECRET_KEY"] })
    .https.onCall(async (data, context) => {
  // 1. Authentication Check
  if (!context.auth) {
    throw new functions.https.HttpsError(
        "unauthenticated",
        "The function must be called while authenticated."
    );
  }

  // 2. Input Validation
  const {
    amount,
    currency = "mxn",
    bookingId,
    serviceId,
    ownerEmail,
    travelerEmail,
    promoCode = "",
    discountAmount = 0,
  } = data;

  if (!amount || amount <= 0) {
    throw new functions.https.HttpsError(
        "invalid-argument",
        "The amount must be a positive integer."
    );
  }

  if (!bookingId) {
    throw new functions.https.HttpsError(
        "invalid-argument",
        "Booking ID is required."
    );
  }

  try {
    const stripeClient = stripe(process.env.STRIPE_MOCHILAPP_SECRET_KEY);

    // 3. Create PaymentIntent
    const paymentIntent = await stripeClient.paymentIntents.create({
      amount: Math.round(amount), // Ensure integer (cents)
      currency: currency.toLowerCase(),
      automatic_payment_methods: {
        enabled: true,
      },
      metadata: {
        app: "mochilapp",
        bookingId: bookingId,
        serviceId: serviceId || "N/A",
        ownerEmail: ownerEmail || "N/A",
        travelerEmail: travelerEmail || "N/A",
        promoCode: promoCode || "NONE",
        discountAmount: discountAmount.toString(),
        environment: "test",
      },
    });

    // 4. Return clientSecret to the app
    return {
      clientSecret: paymentIntent.client_secret,
      paymentIntentId: paymentIntent.id,
    };
  } catch (error) {
    console.error("Stripe Error:", error);
    throw new functions.https.HttpsError(
        "internal",
        error.message || "Error creating PaymentIntent"
    );
  }
});

/**
 * Firestore trigger: notify the service owner (business) when a booking is
 * PAID. Creating a booking alone (status PENDING, before Stripe) no longer
 * notifies: it was premature and fired for abandoned checkouts too.
 */
exports.onBookingPaid = functions.firestore
    .document("bookings/{bookingId}")
    .onUpdate(async (change) => {
      const before = change.before.data();
      const after = change.after.data();
      if (before.status === "PAID" || after.status !== "PAID") return null;
      const booking = after;
      if (!booking.ownerEmail) return null;

      const db = admin.firestore();
      const owners = await db.collection("users")
          .where("email", "==", booking.ownerEmail)
          .limit(1)
          .get();
      if (owners.empty) return null;

      const token = owners.docs[0].data().fcmToken;
      if (!token) return null;

      const traveler = booking.travelerName || booking.travelerEmail || "Un viajero";
      const slots = booking.slots || 1;
      try {
        return await admin.messaging().send({
          token: token,
          notification: {
            title: "¡Nueva reserva pagada! 🎒",
            body: `${traveler} reservó ${slots} lugar(es)` +
                  (booking.serviceName ? ` en ${booking.serviceName}` : "") +
                  (booking.date ? ` para el ${booking.date}` : "") + ".",
          },
        });
      } catch (error) {
        console.error("Error sending booking notification:", error);
        return null;
      }
    });

/**
 * Firestore trigger: when a flash promo is created, broadcast a push
 * notification to all travelers subscribed to the "promos" topic.
 */
exports.onPromoCreated = functions.firestore
    .document("promos/{promoId}")
    .onCreate(async (snap) => {
      const promo = snap.data();
      // Docs legado guardan el flag como "active" (mapeo Kotlin sin @JvmField)
      const promoActive = promo.isActive !== undefined ? promo.isActive : promo.active;
      if (!promoActive || !promo.content) return null;

      try {
        return await admin.messaging().send({
          topic: "promos",
          notification: {
            title: `⚡ Promo relámpago de ${promo.companyName || "Mochilapp"}`,
            body: promo.content,
          },
        });
      } catch (error) {
        console.error("Error sending promo notification:", error);
        return null;
      }
    });

/**
 * Firestore trigger: when a business publishes an operational notice,
 * push it to travelers with affected bookings. Notices with a date target
 * that day's bookings; general notices target upcoming bookings.
 */
exports.onNoticeCreated = functions.firestore
    .document("notices/{noticeId}")
    .onCreate(async (snap) => {
      const notice = snap.data();
      // Docs legado guardan el flag como "active" (mapeo Kotlin sin @JvmField)
      const noticeActive = notice.isActive !== undefined ? notice.isActive : notice.active;
      if (!noticeActive || !notice.message || !notice.serviceId) return null;

      const db = admin.firestore();
      let query = db.collection("bookings")
          .where("serviceId", "==", notice.serviceId)
          .where("status", "in", ["PAID", "PENDING", "CHECKED_IN"]);
      if (notice.date) {
        query = query.where("date", "==", notice.date);
      }
      const bookingsSnap = await query.get();
      if (bookingsSnap.empty) return null;

      // Aviso general: solo reservas de hoy en adelante
      const today = new Date().toLocaleDateString("en-CA", {
        timeZone: "America/Mazatlan",
      });
      const travelerEmails = [...new Set(
          bookingsSnap.docs
              .map((doc) => doc.data())
              .filter((b) => notice.date || (b.date && b.date >= today))
              .map((b) => b.travelerEmail)
              .filter(Boolean)
      )];
      if (travelerEmails.length === 0) return null;

      const icons = {URGENT: "🚨", IMPORTANT: "⚠️", INFO: "📢"};
      const icon = icons[notice.severity] || icons.INFO;
      const title = `${icon} Aviso de ${notice.companyName || "tu reserva"}`;
      const body = (notice.serviceName ? `${notice.serviceName}: ` : "") +
          notice.message;

      const sends = travelerEmails.map(async (email) => {
        const users = await db.collection("users")
            .where("email", "==", email)
            .limit(1)
            .get();
        if (users.empty) return null;
        const token = users.docs[0].data().fcmToken;
        if (!token) return null;
        try {
          return await admin.messaging().send({
            token: token,
            notification: {title: title, body: body},
          });
        } catch (error) {
          console.error(`Error sending notice to ${email}:`, error);
          return null;
        }
      });
      return Promise.all(sends);
    });

/**
 * Scheduled (8:00 AM daily, La Paz timezone): remind each business owner
 * about today's bookings so they can prepare for their trips/guests.
 */
exports.dailyTripReminders = functions.pubsub
    .schedule("0 8 * * *")
    .timeZone("America/Mazatlan")
    .onRun(async () => {
      // Fecha de hoy en formato YYYY-MM-DD en la zona horaria local
      const today = new Date().toLocaleDateString("en-CA", {
        timeZone: "America/Mazatlan",
      });

      const db = admin.firestore();
      const snap = await db.collection("bookings")
          .where("date", "==", today)
          .where("status", "in", ["PAID", "PENDING", "CHECKED_IN"])
          .get();
      if (snap.empty) return null;

      // Agrupar viajeros y reservas por empresa
      const byOwner = {};
      snap.docs.forEach((doc) => {
        const b = doc.data();
        if (!b.ownerEmail) return;
        if (!byOwner[b.ownerEmail]) {
          byOwner[b.ownerEmail] = {travelers: 0, bookings: 0};
        }
        byOwner[b.ownerEmail].travelers += Number(b.slots || 1);
        byOwner[b.ownerEmail].bookings += 1;
      });

      const sends = Object.entries(byOwner).map(async ([email, info]) => {
        const users = await db.collection("users")
            .where("email", "==", email)
            .limit(1)
            .get();
        if (users.empty) return null;
        const token = users.docs[0].data().fcmToken;
        if (!token) return null;
        try {
          return await admin.messaging().send({
            token: token,
            notification: {
              title: "⚓ ¡Hoy tienes salidas!",
              body: `Te esperan ${info.travelers} viajero(s) en ` +
                    `${info.bookings} reserva(s). Revisa tu panel y prepara todo.`,
            },
          });
        } catch (error) {
          console.error(`Error sending reminder to ${email}:`, error);
          return null;
        }
      });
      return Promise.all(sends);
    });

/**
 * Firestore trigger: when a review is created, recalculate the service's
 * average rating and review count. Runs with Admin SDK because security
 * rules (correctly) block travelers from writing reputation fields.
 */
exports.onReviewCreated = functions.firestore
    .document("reviews/{reviewId}")
    .onCreate(async (snap) => {
      const review = snap.data();
      if (!review.serviceId) return null;

      const rating = Number(review.rating);
      if (!rating || rating < 1 || rating > 5) return null;

      const db = admin.firestore();
      const serviceRef = db.collection("services").doc(review.serviceId);

      return db.runTransaction(async (tx) => {
        const doc = await tx.get(serviceRef);
        if (!doc.exists) return;
        const currentRating = doc.data().rating || 0;
        const currentCount = doc.data().reviewCount || 0;
        const newCount = currentCount + 1;
        const newRating = ((currentRating * currentCount) + rating) / newCount;
        tx.update(serviceRef, {
          rating: Math.round(newRating * 10) / 10,
          reviewCount: newCount,
        });
      });
    });

/**
 * Pasaporte Mochilapp — gamification engine.
 *
 * Points and badges are awarded server-side (Admin SDK) so they cannot be
 * faked from the client, mirroring how onReviewCreated owns service ratings.
 * Keep the level thresholds below in sync with PassportUtils.kt on the app.
 */
const POINTS = {BOOKING: 50, REVIEW: 30, POST: 20, CHECKIN: 40};

// Highest threshold first; levelFor() returns the first match.
const LEVELS = [
  {min: 2000, name: "Viajero Legendario"},
  {min: 1200, name: "Embajador Local"},
  {min: 700, name: "Aventurero"},
  {min: 300, name: "Mochilero"},
  {min: 0, name: "Explorador"},
];

// One milestone badge per kind of action, granted on the first of each.
const MILESTONE_BADGES = {
  tripsBooked: "Primera aventura",
  reviewsGiven: "Crítico viajero",
  postsShared: "Primer relato",
  placesVisited: "Aventura vivida",
};

const levelFor = (points) => LEVELS.find((l) => points >= l.min).name;

// Every level reached (except the starting Explorador) becomes a badge.
const levelBadges = (points) =>
  LEVELS.filter((l) => l.min > 0 && points >= l.min).map((l) => l.name);

/**
 * Add points to a traveler (found by email), recompute their passport level
 * and unlock any newly earned badges. No-op for missing users or companies.
 * @param {FirebaseFirestore.Firestore} db Firestore instance.
 * @param {string} email Traveler email to credit.
 * @param {number} points Points to add.
 * @param {string} counterField User field counting this action (e.g. postsShared).
 * @return {Promise<void>|null} Transaction promise, or null if skipped.
 */
async function awardPoints(db, email, points, counterField) {
  if (!email) return null;

  const usersSnap = await db.collection("users")
      .where("email", "==", email)
      .limit(1)
      .get();
  if (usersSnap.empty) return null;
  const userRef = usersSnap.docs[0].ref;

  return db.runTransaction(async (tx) => {
    const doc = await tx.get(userRef);
    if (!doc.exists) return;
    const data = doc.data();
    // Solo los viajeros acumulan pasaporte; las empresas no.
    if ((data.role || "TRAVELER") !== "TRAVELER") return;

    const newPoints = (data.mochiPoints || 0) + points;
    const newCounter = (data[counterField] || 0) + 1;

    const badges = new Set(data.badges || []);
    levelBadges(newPoints).forEach((b) => badges.add(b));
    if (MILESTONE_BADGES[counterField] && newCounter >= 1) {
      badges.add(MILESTONE_BADGES[counterField]);
    }

    tx.update(userRef, {
      mochiPoints: newPoints,
      passportLevel: levelFor(newPoints),
      badges: Array.from(badges),
      [counterField]: newCounter,
    });
  });
}

/**
 * Firestore trigger: credit a traveler when their booking is paid. Awards
 * once via the pointsAwarded flag; the flag-setting write is ignored by the
 * status guard, so it never loops.
 */
exports.awardPointsOnBookingPaid = functions.firestore
    .document("bookings/{bookingId}")
    .onUpdate(async (change) => {
      const before = change.before.data();
      const after = change.after.data();
      if (before.status === "PAID" || after.status !== "PAID") return null;
      if (after.pointsAwarded) return null;

      await change.after.ref.update({pointsAwarded: true});
      return awardPoints(
          admin.firestore(), after.travelerEmail, POINTS.BOOKING, "tripsBooked",
      );
    });

/**
 * Firestore trigger: credit a traveler when they check in at the place
 * ("Vive"). The GPS-proximity gate runs on the client; here we just award
 * once via the checkInPointsAwarded flag (the flag write doesn't change
 * travelerCheckedInAt, so it never loops).
 */
exports.awardPointsOnTravelerCheckIn = functions.firestore
    .document("bookings/{bookingId}")
    .onUpdate(async (change) => {
      const before = change.before.data();
      const after = change.after.data();
      if ((before.travelerCheckedInAt || 0) > 0) return null;
      if (!(after.travelerCheckedInAt > 0)) return null;
      if (after.checkInPointsAwarded) return null;

      await change.after.ref.update({checkInPointsAwarded: true});
      return awardPoints(
          admin.firestore(), after.travelerEmail, POINTS.CHECKIN, "placesVisited",
      );
    });

/**
 * Firestore trigger: credit a traveler for each review they write.
 */
exports.awardPointsOnReview = functions.firestore
    .document("reviews/{reviewId}")
    .onCreate(async (snap) => {
      const review = snap.data();
      return awardPoints(
          admin.firestore(), review.authorEmail, POINTS.REVIEW, "reviewsGiven",
      );
    });

/**
 * Firestore trigger: credit a traveler for each post they share.
 */
exports.awardPointsOnPost = functions.firestore
    .document("posts/{postId}")
    .onCreate(async (snap) => {
      const post = snap.data();
      return awardPoints(
          admin.firestore(), post.authorEmail, POINTS.POST, "postsShared",
      );
    });
