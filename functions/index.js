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
 * Firestore trigger: when a booking is created, send a push notification
 * to the service owner (business) using their saved FCM token.
 */
exports.onBookingCreated = functions.firestore
    .document("bookings/{bookingId}")
    .onCreate(async (snap) => {
      const booking = snap.data();
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
            title: "¡Nueva reserva! 🎒",
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
      if (!promo.isActive || !promo.content) return null;

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
