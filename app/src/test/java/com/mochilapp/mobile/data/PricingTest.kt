package com.mochilapp.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Espejo Kotlin de functions/test/pricing.test.js: mismas fórmulas, mismos
// casos clave. Si un caso pasa aquí y falla allá (o viceversa), las fórmulas
// divergieron y la UI mostraría un precio distinto al cobrado.
class PricingTest {

    private val mario = Pricing.Privada(
        precioBase = 8400.0,
        personasIncluidas = 6,
        precioPersonaExtra = 1200.0,
        capacidadMaxima = 20
    )

    @Test
    fun `privada base cubre a los incluidos`() {
        assertEquals(8400.0, mario.calcularTotal(6), 0.0)
        assertEquals(8400.0, mario.calcularTotal(2), 0.0)
        assertEquals(8400.0, mario.calcularTotal(1), 0.0)
    }

    @Test
    fun `privada 8 personas - criterio de aceptacion del spec`() {
        assertEquals(10800.0, mario.calcularTotal(8), 0.0)
    }

    @Test
    fun `privada capacidad maxima`() {
        assertEquals(8400.0 + 14 * 1200.0, mario.calcularTotal(20), 0.0)
    }

    @Test
    fun `privada con noches multiplica la formula`() {
        assertEquals(10800.0 * 3, mario.calcularTotal(8, noches = 3), 0.0)
    }

    @Test
    fun `colectiva por persona`() {
        val c = Pricing.Colectiva(precioPorPersona = 850.0, capacidadMaxima = 12)
        assertEquals(3400.0, c.calcularTotal(4), 0.0)
    }

    @Test
    fun `colectiva hospedaje cobra por noche - formula historica`() {
        val c = Pricing.Colectiva(precioPorPersona = 850.0, capacidadMaxima = 12)
        assertEquals(2550.0, c.calcularTotal(4, noches = 3), 0.0)
    }

    // --- mapper tolerante ---

    @Test
    fun `servicio legado sin modalidad mapea a colectiva con price`() {
        val service = ServiceFirestore(price = 850.0, capacity = 12)
        val p = service.pricingModel()
        assertTrue(p is Pricing.Colectiva)
        assertEquals(850.0, (p as Pricing.Colectiva).precioPorPersona, 0.0)
        assertEquals(12, p.capacidadMaxima)
    }

    @Test
    fun `servicio privado mapea desde el map de pricing`() {
        val service = ServiceFirestore(
            price = 8400.0,
            modalidad = "PRIVADA",
            // Firestore entrega números como Long: el mapper debe tolerarlo
            pricing = mapOf(
                "precioBase" to 8400L,
                "personasIncluidas" to 6L,
                "precioPersonaExtra" to 1200L,
                "capacidadMaxima" to 20L
            )
        )
        val p = service.pricingModel()
        assertTrue(p is Pricing.Privada)
        assertEquals(10800.0, p.calcularTotal(8), 0.0)
    }

    @Test
    fun `privada con pricing corrupto cae a colectiva legado sin crashear`() {
        val service = ServiceFirestore(
            price = 8400.0,
            capacity = 20,
            modalidad = "PRIVADA",
            pricing = mapOf("precioBase" to 0L) // inválido
        )
        val p = service.pricingModel()
        assertTrue(p is Pricing.Colectiva)
    }

    @Test
    fun `colectiva nueva prioriza pricing sobre price legado`() {
        val service = ServiceFirestore(
            price = 1.0,
            modalidad = "COLECTIVA",
            pricing = mapOf("precioPorPersona" to 850.0, "capacidadMaxima" to 10L)
        )
        val p = service.pricingModel() as Pricing.Colectiva
        assertEquals(850.0, p.precioPorPersona, 0.0)
        assertEquals(10, p.capacidadMaxima)
    }
}
