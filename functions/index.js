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
