package com.mochilapp.mobile.data

// Modalidad de venta de un servicio (spec-modalidad-privada-colectiva §3.2).
//
// IMPORTANTE: este cálculo es SOLO para mostrar precios en UI. El cobro real
// lo calcula el servidor con la misma fórmula (functions/pricing.js) leyendo
// el servicio de Firestore; cualquier discrepancia se resuelve a favor del
// servidor. Si cambias una fórmula aquí, cámbiala también allá.
sealed class Pricing {
    abstract val capacidadMaxima: Int

    data class Colectiva(
        val precioPorPersona: Double,
        override val capacidadMaxima: Int
    ) : Pricing()

    data class Privada(
        val precioBase: Double,
        val personasIncluidas: Int,
        val precioPersonaExtra: Double,
        override val capacidadMaxima: Int
    ) : Pricing()

    // Total sin promo. noches > 0 = reserva por rango (hospedaje):
    // colectiva cobra por noche (fórmula histórica de la app) y privada
    // multiplica su fórmula por noches.
    fun calcularTotal(personas: Int, noches: Int = 0): Double = when (this) {
        is Colectiva -> {
            val unidades = if (noches > 0) noches else maxOf(1, personas)
            precioPorPersona * unidades
        }
        is Privada -> {
            val extras = maxOf(0, personas - personasIncluidas)
            (precioBase + extras * precioPersonaExtra) * maxOf(1, noches)
        }
    }
}

private fun Any?.asDouble(): Double = (this as? Number)?.toDouble() ?: 0.0
private fun Any?.asInt(): Int = (this as? Number)?.toInt() ?: 0

// Lectura tolerante (espejo de resolverPricing en functions/pricing.js):
// documentos legado sin `modalidad` se tratan como colectiva usando los
// campos `price`/`capacity` de siempre.
fun ServiceFirestore.pricingModel(): Pricing {
    if (modalidad == "PRIVADA") {
        val base = pricing["precioBase"].asDouble()
        val incluidas = pricing["personasIncluidas"].asInt()
        val extra = pricing["precioPersonaExtra"].asDouble()
        val max = pricing["capacidadMaxima"].asInt()
        if (base > 0 && incluidas > 0 && max >= incluidas) {
            return Pricing.Privada(base, incluidas, extra, max)
        }
        // Pricing privado corrupto: caer a colectiva legado antes que crashear
    }
    val porPersona = pricing["precioPorPersona"].asDouble().takeIf { it > 0 } ?: price
    val max = pricing["capacidadMaxima"].asInt().takeIf { it > 0 } ?: capacity
    return Pricing.Colectiva(porPersona, max)
}

val ServiceFirestore.esPrivado: Boolean
    get() = modalidad == "PRIVADA"
