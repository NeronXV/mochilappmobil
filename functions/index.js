const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const stripe = require("stripe");
const pricing = require("./pricing");

/**
 * Callable function to create a Stripe PaymentIntent for Mochilapp.
 * Requires STRIPE_MOCHILAPP_SECRET_KEY to be set in Firebase Secrets.
 *
 * The amount is ALWAYS computed server-side from the booking document;
 * anything the client sends is ignored so a tampered app cannot pay less.
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

  const {bookingId, currency = "mxn"} = data;
  if (!bookingId) {
    throw new functions.https.HttpsError(
        "invalid-argument",
        "Booking ID is required."
    );
  }

  // 2. Load the booking: it is the single source of truth for the charge
  const bookingSnap = await admin.firestore()
      .collection("bookings").doc(bookingId).get();
  if (!bookingSnap.exists) {
    throw new functions.https.HttpsError(
        "not-found",
        "La reserva no existe."
    );
  }
  const booking = bookingSnap.data();

  // 3. Only the traveler who owns the booking can pay for it
  const callerEmail = (context.auth.token.email || "").toLowerCase();
  const travelerEmail = (booking.travelerEmail || "").toLowerCase();
  if (!callerEmail || callerEmail !== travelerEmail) {
    throw new functions.https.HttpsError(
        "permission-denied",
        "Esta reserva no pertenece a tu cuenta."
    );
  }

  if (booking.status === "PAID") {
    throw new functions.https.HttpsError(
        "failed-precondition",
        "Esta reserva ya está pagada."
    );
  }
  if (booking.status === "CANCELLED") {
    throw new functions.https.HttpsError(
        "failed-precondition",
        "Esta reserva expiró o fue cancelada. Crea una nueva reserva."
    );
  }

  // 4. Server-side amount: SIEMPRE recalculado desde el servicio (pricing.js).
  // El totalPrice que escribió el cliente al crear la reserva no se usa.
  const serviceSnap = await admin.firestore()
      .collection("services").doc(booking.serviceId || "").get();
  if (!serviceSnap.exists) {
    throw new functions.https.HttpsError(
        "not-found",
        "El servicio de esta reserva ya no existe."
    );
  }
  const service = serviceSnap.data();
  const db = admin.firestore();
  const now = Date.now();

  const esPedidoComida = Array.isArray(booking.orderItems) &&
      booking.orderItems.length > 0;
  const personas = Number(booking.personas) || Number(booking.slots) || 1;
  const numNoches = pricing.noches(booking.date, booking.checkOutDate);

  let servicePricing = null;
  let originalTotal;
  if (esPedidoComida) {
    // Pedido de puesto de comida: el precio sale del menú actual del servicio
    try {
      originalTotal = pricing.calcularTotalPedido(
          service, booking.orderItems, booking.fulfillmentType);
    } catch (error) {
      throw new functions.https.HttpsError(
          "failed-precondition",
          "El menú del puesto cambió desde que creaste el pedido. " +
          "Vuelve a crearlo para ver los precios vigentes."
      );
    }
  } else {
    try {
      servicePricing = pricing.resolverPricing(service);
    } catch (error) {
      throw new functions.https.HttpsError(
          "failed-precondition",
          "El servicio tiene una configuración de precio inválida. " +
          "Contacta al prestador."
      );
    }

    // App vieja intentando pagar un servicio privado como si fuera colectivo:
    // se rechaza aquí (seguridad server-side; la prueba cerrada se actualiza)
    if (servicePricing.modalidad === "PRIVADA" && booking.modalidad !== "PRIVADA") {
      throw new functions.https.HttpsError(
          "failed-precondition",
          "Este servicio se reserva completo para tu grupo. " +
          "Actualiza Mochilapp para poder reservarlo."
      );
    }

    if (servicePricing.capacidadMaxima > 0 &&
        personas > servicePricing.capacidadMaxima) {
      throw new functions.https.HttpsError(
          "failed-precondition",
          `Este servicio admite máximo ${servicePricing.capacidadMaxima} personas.`
      );
    }

    originalTotal = pricing.calcularTotal(servicePricing, personas, numNoches);
  }

  // 5. Promo server-side: solo porcentaje simple, validada contra su doc
  let descuento = 0;
  let promoAplicadaId = "";
  if (booking.promoId) {
    const promoSnap = await db.collection("promos").doc(booking.promoId).get();
    if (promoSnap.exists) {
      const promo = promoSnap.data();
      const promoActiva = promo.isActive !== undefined ? promo.isActive : promo.active;
      const expira = promo.expiresAt > 0 ? promo.expiresAt :
          (Number(promo.timestamp) || 0) + 24 * 60 * 60 * 1000;
      if (promoActiva && expira > now && promo.discountPercent > 0) {
        const res = pricing.aplicarDescuento(originalTotal, promo.discountPercent);
        descuento = res.descuento;
        promoAplicadaId = booking.promoId;
      }
    }
    // Promo inexistente/vencida: se cobra el total completo, sin error
  }
  const montoTotal = pricing.redondear(originalTotal - descuento);
  const amount = Math.round(montoTotal * 100); // centavos
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new functions.https.HttpsError(
        "failed-precondition",
        "La reserva tiene un total inválido."
    );
  }

  // 6. Disponibilidad + escritura autoritativa, en transacción.
  // PRIVADA: la salida completa debe estar libre; el claim (sello de servidor)
  // desempata a dos viajeros compitiendo. COLECTIVA con aforo: no sobrevender.
  const bookingRef = db.collection("bookings").doc(bookingId);
  try {
    await db.runTransaction(async (tx) => {
      const salidaQuery = db.collection("bookings")
          .where("serviceId", "==", booking.serviceId)
          .where("date", "==", booking.date || "");
      const salidaSnap = await tx.get(salidaQuery);
      const otras = salidaSnap.docs
          .filter((d) => d.id !== bookingId)
          .map((d) => ({id: d.id, data: d.data()}))
          .filter((o) => (o.data.departureTime || "") === (booking.departureTime || ""));

      if (servicePricing && servicePricing.modalidad === "PRIVADA") {
        const mia = {id: bookingId, data: booking};
        const bloqueada = otras.some((o) =>
          pricing.bloqueaSalidaPrivada(o, mia, now));
        if (bloqueada) {
          throw new functions.https.HttpsError(
              "failed-precondition",
              "Esta salida ya fue reservada por otro viajero. " +
              "Elige otro horario o fecha."
          );
        }
      } else if (servicePricing && servicePricing.capacidadMaxima > 0 &&
          numNoches === 0) {
        // Colectiva de salida única (hospedaje por rangos queda fuera v1)
        const ocupados = otras
            .filter((o) => pricing.retieneCupo(o.data, now))
            .reduce((sum, o) =>
              sum + (Number(o.data.personas) || Number(o.data.slots) || 1), 0);
        if (ocupados + personas > servicePricing.capacidadMaxima) {
          throw new functions.https.HttpsError(
              "failed-precondition",
              "Ya no hay lugares suficientes para esta salida."
          );
        }
      }

      const updates = {
        montoTotal: montoTotal,
        totalPrice: montoTotal, // compat: tickets/KPIs actuales leen totalPrice
        originalTotal: pricing.redondear(originalTotal),
        discountAmount: descuento,
        promoId: promoAplicadaId,
        personas: personas,
        modalidad: servicePricing ? servicePricing.modalidad : "COLECTIVA",
      };
      if (servicePricing && servicePricing.modalidad === "PRIVADA") {
        // Sello del servidor: desempate inmune al reloj del cliente
        updates.salidaClaimedAt = admin.firestore.FieldValue.serverTimestamp();
      }
      tx.update(bookingRef, updates);
    });
  } catch (error) {
    if (error instanceof functions.https.HttpsError) throw error;
    console.error("Error en transacción de disponibilidad:", error);
    throw new functions.https.HttpsError(
        "internal",
        "No se pudo verificar la disponibilidad. Intenta de nuevo."
    );
  }

  try {
    const stripeClient = stripe(process.env.STRIPE_MOCHILAPP_SECRET_KEY);

    // 5. Create PaymentIntent
    const paymentIntent = await stripeClient.paymentIntents.create({
      amount: amount,
      currency: currency.toLowerCase(),
      automatic_payment_methods: {
        enabled: true,
      },
      metadata: {
        app: "mochilapp",
        bookingId: bookingId,
        serviceId: booking.serviceId || "N/A",
        ownerEmail: booking.ownerEmail || "N/A",
        travelerEmail: booking.travelerEmail || "N/A",
        promoCode: promoAplicadaId ? (booking.promoCode || "NONE") : "NONE",
        discountAmount: String(descuento),
        modalidad: servicePricing ? servicePricing.modalidad : "N/A",
        personas: String(personas),
      },
    });

    // 6. Return clientSecret to the app
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
 * Stripe webhook: the ONLY writer of status=PAID. The app no longer marks
 * bookings as paid (and security rules block it); Stripe notifies us here
 * when the charge actually went through, so a crash or lost connection on
 * the phone can never leave a charged booking stuck in PENDING.
 *
 * Setup: Stripe Dashboard → Developers → Webhooks → endpoint for
 * payment_intent.succeeded / payment_intent.payment_failed, then store the
 * signing secret as STRIPE_MOCHILAPP_WEBHOOK_SECRET in Firebase Secrets.
 */
exports.stripeWebhook = functions
    .runWith({
      secrets: ["STRIPE_MOCHILAPP_SECRET_KEY", "STRIPE_MOCHILAPP_WEBHOOK_SECRET"],
    })
    .https.onRequest(async (req, res) => {
      if (req.method !== "POST") {
        res.status(405).send("Method Not Allowed");
        return;
      }

      let event;
      try {
        const stripeClient = stripe(process.env.STRIPE_MOCHILAPP_SECRET_KEY);
        event = stripeClient.webhooks.constructEvent(
            req.rawBody,
            req.headers["stripe-signature"],
            process.env.STRIPE_MOCHILAPP_WEBHOOK_SECRET,
        );
      } catch (error) {
        console.error("Webhook signature verification failed:", error.message);
        res.status(400).send(`Webhook Error: ${error.message}`);
        return;
      }

      const intent = event.data.object;
      const metadata = intent.metadata || {};
      const bookingId = metadata.bookingId;
      if (metadata.app !== "mochilapp" || !bookingId) {
        // Not ours (the Stripe account may serve other apps); acknowledge it.
        res.json({received: true});
        return;
      }

      if (event.type === "payment_intent.succeeded") {
        const bookingRef = admin.firestore()
            .collection("bookings").doc(bookingId);
        try {
          await admin.firestore().runTransaction(async (tx) => {
            const doc = await tx.get(bookingRef);
            if (!doc.exists) {
              console.error(
                  `Pago ${intent.id} recibido para reserva inexistente ` +
                  `${bookingId}. Revisar manualmente en Stripe.`);
              return;
            }
            const current = doc.data();
            if (current.status === "PAID") return; // Retry de Stripe: ya procesado
            if (current.status === "CANCELLED") {
              // Pagó justo cuando expiraba el hold: el pago manda, se reinstala.
              console.warn(
                  `Reserva ${bookingId} estaba CANCELLED pero el pago ` +
                  `${intent.id} se completó; se reinstala como PAID.`);
            }
            tx.update(bookingRef, {
              status: "PAID",
              paidAt: Date.now(),
              paymentIntentId: intent.id,
              amountPaid: intent.amount,
            });
          });
        } catch (error) {
          console.error(`Error marcando ${bookingId} como PAID:`, error);
          // 500 → Stripe reintenta el evento, no se pierde el pago
          res.status(500).send("Error updating booking");
          return;
        }
      } else if (event.type === "payment_intent.payment_failed") {
        const message = (intent.last_payment_error &&
            intent.last_payment_error.message) || "unknown";
        console.warn(`Pago fallido para reserva ${bookingId}: ${message}`);
      }

      res.json({received: true});
    });

/**
 * MochiBot proxy: the Gemini API key lives in Secret Manager, never in the
 * APK (it used to ship in BuildConfig and was extractable). Same pattern as
 * Stripe. The app sends the prompt + chat history; we call Gemini and return
 * the text.
 */
exports.askMochi = functions
    .runWith({ secrets: ["GEMINI_MOCHILAPP_KEY"], timeoutSeconds: 60 })
    .https.onCall(async (data, context) => {
      if (!context.auth) {
        throw new functions.https.HttpsError(
            "unauthenticated",
            "The function must be called while authenticated."
        );
      }

      const prompt = String(data.prompt || "").trim();
      if (!prompt) {
        throw new functions.https.HttpsError(
            "invalid-argument",
            "Prompt is required."
        );
      }

      // Historial acotado: máx 20 turnos y 4000 chars por turno
      const history = (Array.isArray(data.history) ? data.history : [])
          .slice(-20)
          .filter((m) => m && m.text)
          .map((m) => ({
            role: m.role === "model" ? "model" : "user",
            parts: [{text: String(m.text).slice(0, 4000)}],
          }));

      const contents = [
        ...history,
        {role: "user", parts: [{text: prompt.slice(0, 8000)}]},
      ];

      // Alias estable del modelo flash vigente (mismo criterio que la app:
      // evita romperse cuando Google retira una versión específica)
      const model = "gemini-flash-latest";
      const url = "https://generativelanguage.googleapis.com/v1beta/models/" +
          model + ":generateContent?key=" + process.env.GEMINI_MOCHILAPP_KEY;

      try {
        const resp = await fetch(url, {
          method: "POST",
          headers: {"Content-Type": "application/json"},
          body: JSON.stringify({contents}),
        });
        if (!resp.ok) {
          const body = await resp.text();
          console.error("Gemini error", resp.status, body.slice(0, 500));
          throw new functions.https.HttpsError(
              "internal",
              "Gemini respondió " + resp.status
          );
        }
        const json = await resp.json();
        const candidate = (json.candidates || [])[0];
        const parts = (candidate && candidate.content &&
            candidate.content.parts) || [];
        const text = parts.map((p) => p.text || "").join("");
        return {text: text};
      } catch (error) {
        if (error instanceof functions.https.HttpsError) throw error;
        console.error("askMochi error:", error);
        throw new functions.https.HttpsError(
            "internal",
            error.message || "Error consultando a MochiBot"
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
 * Scheduled (every 30 min): release seats held by unpaid PENDING bookings
 * older than the hold window. Mirrors PENDING_HOLD_MILLIS in the app
 * (FirebaseModels.kt) — keep both in sync.
 * Requires a composite index on bookings (status ASC, createdAt ASC); the
 * first run logs a direct link to create it if missing.
 */
exports.expireStalePendingBookings = functions.pubsub
    .schedule("every 30 minutes")
    .timeZone("America/Mazatlan")
    .onRun(async () => {
      const HOLD_MILLIS = 30 * 60 * 1000;
      const cutoff = Date.now() - HOLD_MILLIS;
      const db = admin.firestore();
      const snap = await db.collection("bookings")
          .where("status", "==", "PENDING")
          .where("createdAt", ">", 0)
          .where("createdAt", "<", cutoff)
          .get();
      if (snap.empty) return null;

      const batch = db.batch();
      snap.docs.forEach((doc) => {
        batch.update(doc.ref, {
          status: "CANCELLED",
          cancelReason: "HOLD_EXPIRED",
        });
      });
      console.log(`Expiradas ${snap.size} reservas PENDING sin pagar.`);
      return batch.commit();
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
