package com.mochilapp.mobile.utils

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase

// Telemetría central: Analytics para uso/embudos y Crashlytics para
// diagnóstico. Todo es fire-and-forget: nunca debe romper un flujo de usuario.
object Telemetry {
    private val analytics get() = Firebase.analytics
    private val crashlytics get() = Firebase.crashlytics

    // Identifica la sesión con el uid (nunca el email) para poder rastrear un
    // crash o un embudo hasta el usuario afectado.
    fun setUser(uid: String, role: String) {
        crashlytics.setUserId(uid)
        crashlytics.setCustomKey("role", role)
        analytics.setUserId(uid)
        analytics.setUserProperty("role", role)
    }

    fun clearUser() {
        crashlytics.setUserId("")
        analytics.setUserId(null)
    }

    fun logLogin(method: String) {
        analytics.logEvent(FirebaseAnalytics.Event.LOGIN, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        })
    }

    fun logSignUp(method: String, role: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SIGN_UP, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
            putString("role", role)
        })
    }

    // Reserva o pedido creado (aún sin pagar): inicio del embudo de compra.
    fun logBookingCreated(
        serviceId: String,
        serviceName: String,
        slots: Int,
        total: Double,
        isFoodOrder: Boolean
    ) {
        analytics.logEvent("reserva_creada", Bundle().apply {
            putString("service_id", serviceId)
            putString("service_name", serviceName.take(100))
            putLong("slots", slots.toLong())
            putDouble("total", total)
            putString("tipo", if (isFoodOrder) "pedido_comida" else "reserva")
        })
    }

    // Pago confirmado por Stripe. Evento estándar de ecommerce: alimenta los
    // reportes de ingresos de Analytics sin configuración extra.
    fun logPurchase(bookingId: String, amount: Double) {
        analytics.logEvent(FirebaseAnalytics.Event.PURCHASE, Bundle().apply {
            putString(FirebaseAnalytics.Param.TRANSACTION_ID, bookingId)
            putDouble(FirebaseAnalytics.Param.VALUE, amount)
            putString(FirebaseAnalytics.Param.CURRENCY, "MXN")
        })
    }

    // Errores atrapados que hoy solo van a Logcat: como no-fatales quedan
    // agregados y buscables en Crashlytics.
    fun recordError(where: String, e: Throwable) {
        crashlytics.setCustomKey("where", where)
        crashlytics.recordException(e)
    }
}
