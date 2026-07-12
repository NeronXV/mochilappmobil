# Spec: Modalidad de servicio — Privada vs Colectiva

**Proyecto:** Mochilapp (app Android Kotlin/Compose + Firebase, repos: app principal y mochilapp-admin)
**Origen:** Retroalimentación de prestador real (Mario Diaz Tours, tours en lancha).
**Fecha:** 2026-07-11

---

## 1. Contexto y problema

El modelo actual de servicio asume implícitamente una venta **colectiva**: un precio (interpretado por persona) y una capacidad máxima (asientos individuales). Un prestador con lancha privada no puede expresar su modelo real:

- Tarifa base **$8,400 MXN** que cubre hasta **6 personas** (el precio es por la embarcación, no por persona).
- **$1,200 MXN por persona adicional**, hasta un máximo de **20 personas**.
- La reserva es de la **unidad completa**: si reservan 2 personas, la lancha ya no está disponible para nadie más en esa salida.

Problemas derivados observados:

1. El campo `precio` es semánticamente ambiguo (¿por persona o por servicio?).
2. La métrica "Cupos libres" del panel empresarial suma asientos de servicios privados y colectivos, lo cual no tiene sentido (un servicio privado tiene 1 unidad reservable por salida, no N asientos).
3. El workaround del usuario (crear servicios escalonados por número de personas) indica que el esquema no puede expresar el concepto.

## 2. Decisión de diseño

Se introduce el atributo **`modalidad`** en el servicio, **ortogonal a la categoría**:

- `categoria` describe *qué es* la actividad (BOAT_TOUR, etc.).
- `modalidad` describe *cómo se vende*: `COLECTIVA` o `PRIVADA`.

**No se crean categorías nuevas.** Cualquier categoría puede en principio venderse en cualquier modalidad (aplicará a futuras categorías como transporte en van).

## 3. Modelo de datos (Firestore)

### 3.1 `services/{id}` — campos nuevos/modificados

```
modalidad: "COLECTIVA" | "PRIVADA"   // string enum, requerido en servicios nuevos

pricing: {
  // Si modalidad == COLECTIVA:
  precioPorPersona: number            // MXN, entero (centavos no, pesos enteros por ahora)
  capacidadMaxima: number

  // Si modalidad == PRIVADA:
  precioBase: number                  // MXN, cubre hasta personasIncluidas
  personasIncluidas: number           // ej. 6
  precioPersonaExtra: number          // MXN por persona arriba de personasIncluidas, ej. 1200
  capacidadMaxima: number             // ej. 20
}
```

Notas:
- El campo `precio` legado se conserva por compatibilidad de lectura en apps viejas, pero la fuente de verdad para servicios nuevos es `pricing`. Documentar en código que `precio` está deprecado.
- Validar en escritura: `personasIncluidas <= capacidadMaxima`, todos los montos > 0.

### 3.2 Modelo en Kotlin (app)

Usar sealed class para forzar manejo exhaustivo de ambas modalidades en cada `when`:

```kotlin
sealed class Pricing {
    data class Colectiva(
        val precioPorPersona: Int,
        val capacidadMaxima: Int
    ) : Pricing()

    data class Privada(
        val precioBase: Int,
        val personasIncluidas: Int,
        val precioPersonaExtra: Int,
        val capacidadMaxima: Int
    ) : Pricing()

    fun calcularTotal(personas: Int): Int = when (this) {
        is Colectiva -> precioPorPersona * personas
        is Privada -> precioBase +
            maxOf(0, personas - personasIncluidas) * precioPersonaExtra
    }
}
```

El mapper Firestore → Kotlin decide la subclase leyendo `modalidad`. Si `modalidad` es null (documento legado), mapear a `Colectiva` usando el campo `precio` legado.

### 3.3 `bookings/{id}` — campos nuevos

```
personas: number          // cuántas personas incluye la reserva
modalidad: string         // copia denormalizada del servicio al momento de reservar
montoTotal: number        // calculado por el servidor, NUNCA por el cliente
```

La copia denormalizada de `modalidad` y el desglose de precio en la reserva protege el histórico: si el prestador cambia su pricing después, las reservas viejas conservan lo que se cobró.

## 4. Semántica de inventario

- **COLECTIVA:** el inventario por salida (horario) son **asientos**. Ocupación = suma de `personas` de reservas activas de esa salida. Disponible = `capacidadMaxima - ocupación`.
- **PRIVADA:** el inventario por salida es **1 unidad**. Una reserva activa (PENDING con hold vigente o PAID) bloquea la salida completa, sin importar cuántas personas incluya. `capacidadMaxima` solo limita el máximo de personas de esa única reserva.

Reutilizar la lógica de expiración existente (`expireStalePendingBookings`) para liberar salidas privadas bloqueadas por reservas PENDING vencidas.

## 5. Cálculo de precio del lado del servidor (crítico)

La Cloud Function que crea el PaymentIntent de Stripe DEBE:

1. Leer el documento del servicio desde Firestore (no confiar en datos del cliente).
2. Validar `personas` contra `capacidadMaxima` y, en privada, verificar que la salida no esté ya bloqueada.
3. Calcular `montoTotal` con la fórmula según `modalidad`.
4. Crear el PaymentIntent con ese monto y escribir `montoTotal` en la reserva.

El cliente calcula el precio SOLO para mostrarlo en UI. Cualquier discrepancia entre lo mostrado y lo cobrado se resuelve a favor del cálculo del servidor. Mantener la fórmula en un solo módulo del backend (evitar duplicarla en varias functions).

## 6. Reglas de Firestore

- El cliente NO puede escribir `montoTotal`, `modalidad` (en bookings) ni, como ya está vigente, `status: PAID`. Extender las reglas actuales endurecidas.
- En `services`, validar que si `modalidad == "PRIVADA"` existan los campos de pricing privado, y análogo para colectiva (reglas con `hasAll` sobre `pricing`).

## 7. UI — App empresa (creación/edición de servicio)

1. **Selector de modalidad al inicio del formulario** (antes de los campos de precio): dos opciones con descripción corta:
   - "Colectivo — cada viajero compra su lugar"
   - "Privado — el viajero renta el servicio completo"
2. Los campos de pricing mostrados dependen de la selección:
   - Colectiva: precio por persona, capacidad máxima.
   - Privada: tarifa base, personas incluidas, precio por persona extra, capacidad máxima.
3. Mostrar un preview en vivo del precio en modalidad privada, ej.: "6 personas → $8,400 · 8 personas → $10,800", para que el prestador verifique que capturó bien su tarifa.
4. La modalidad NO es editable después de que el servicio tenga reservas activas (cambiarla rompería la semántica de inventario). Si no hay reservas, permitir el cambio.

## 8. UI — App viajero

1. **Badge "Privado"** en la tarjeta del servicio en resultados de búsqueda. Opcional: filtro por modalidad.
2. En el detalle de servicio privado:
   - Selector de número de personas (1 a `capacidadMaxima`).
   - Precio total actualizándose en vivo al cambiar personas, con desglose visible: "Tarifa base (hasta 6): $8,400 + 2 adicionales × $1,200 = $10,800".
   - Texto claro: "Reservas la embarcación completa para tu grupo".
3. En servicios privados, los horarios ya reservados aparecen como no disponibles (la salida completa).
4. El flujo de pago no cambia: se sigue creando la reserva PENDING y el PaymentIntent server-side; el webhook confirma PAID (arquitectura existente).

## 9. Panel empresarial (dashboard)

Separar la métrica de disponibilidad por modalidad:

- Servicios colectivos: "Cupos libres (próx. 7 días)" — como hoy.
- Servicios privados: "Salidas disponibles (próx. 7 días)" — número de horarios sin reserva activa.
- NUNCA sumar asientos de privados con colectivos en un solo número.

## 10. Migración y compatibilidad

1. **Regla de lectura tolerante:** si un documento de servicio no tiene `modalidad`, tratarlo como `COLECTIVA` con el `precio` legado como `precioPorPersona`. Esto en el mapper de la app Y en las Cloud Functions.
2. **Script de backfill** (Node.js Admin SDK, en mochilapp-admin): recorrer `services`, escribir `modalidad: "COLECTIVA"` y `pricing.precioPorPersona = precio` donde falte. Correrlo después de desplegar el código tolerante, no antes.
3. Orden de despliegue: (a) Cloud Functions con lectura tolerante y cálculo server-side, (b) reglas de Firestore, (c) backfill, (d) release de app con la UI nueva. Apps viejas siguen funcionando porque solo crean servicios colectivos con el esquema legado, que el servidor sigue entendiendo.
4. Relación con el bug conocido de `boat_tour` (reservas legacy sin `departureTime` inflando ocupación): si este spec toca la lógica de ocupación por salida, aprovechar para corregir ese bug en el mismo cambio o confirmar explícitamente que queda para después.

## 11. Fuera de alcance (NO implementar en esta iteración)

- **Recurso compartido (entidad embarcación):** el caso donde una misma lancha atiende un servicio privado y uno colectivo y una reserva debería bloquear la otra. Registrado en roadmap; se validará con uso real antes de construirlo.
- Política de cancelación/reembolso (pendiente de decisiones de producto).
- Precios por temporada, descuentos por volumen, o modalidades adicionales.

## 12. Decisiones de implementación (resueltas 2026-07-11)

Resoluciones acordadas al iniciar la implementación, para que quede versionado el porqué:

1. **Apps viejas vs servicios privados.** Un viajero con app vieja podría reservar un servicio privado como colectivo. Resolución: `createPaymentIntent` rechaza el pago (`failed-precondition`, "Este servicio se reserva completo para tu grupo. Actualiza Mochilapp para poder reservarlo."). Los binarios viejos muestran su mensaje genérico (capturan la excepción y no son corregibles); a partir del siguiente release la app propaga el mensaje del servidor tal cual. Aceptado porque la app está en prueba cerrada (2 testers): las validaciones server-side son seguridad, no compatibilidad, y no se invierte en UX de transición.
2. **Promos.** El servidor valida la promo (`promos/{id}` activa y vigente) y aplica solo porcentaje simple, server-side. Escribe denormalizado en la reserva: `promoId` aplicado, `discountAmount` y `originalTotal` autoritativos. Promo inválida/vencida → se cobra el total completo sin error.
3. **Desempate de exclusividad privada.** Dos PENDING simultáneos se desempatan por `createdAt` (empate secundario: id de documento). Además, al iniciar el pago la transacción escribe `salidaClaimedAt = FieldValue.serverTimestamp()` en la reserva (sello del servidor, inmune al reloj del cliente): un claim vigente bloquea a cualquier competidor aunque su `createdAt` diga lo contrario. Nota: migrar `createdAt` mismo a serverTimestamp implica cambiar su tipo (Long → Timestamp) con impacto en `holdsSeats`, la expiración y los módulos; el claim cubre la garantía "nunca reloj del cliente" donde importa (el dinero) sin esa migración. Smoke test incluye dos dispositivos compitiendo por la misma salida.
4. **`precio` legado en servicios privados** = `precioBase` (las apps viejas muestran "$8,400"; combinado con la decisión 1, sin riesgo de cobro incorrecto).
5. **Panel web fuera de alcance total** (nadie lo usa actualmente). Sin guards ni ajustes.
6. **Menores:** montos en pesos manteniendo `Double` en los campos Firestore existentes; el servidor escribe `montoTotal` y además sobreescribe `totalPrice` (tickets/KPIs actuales siguen funcionando); filtro por modalidad del §8.1 pospuesto; el bug de `boat_tour` del §10.4 ya estaba corregido (commit `ceb4d68`).
7. **Descubiertos durante implementación:**
   - *Hospedaje:* la fórmula histórica del cliente es `precio × noches` (no por persona). El módulo server-side la reproduce: colectiva con `checkOutDate` cobra por noches. Privada con rango de noches: `fórmula privada × noches` (interpretación v1 para renta vacacional privada, validar con uso real).
   - *Pedidos de comida:* al dejar de confiar en `totalPrice` del cliente, el servidor recalcula pedidos desde el menú vigente del servicio (match por nombre); si un producto ya no existe se rechaza con "el menú cambió, vuelve a crear el pedido".
   - *Sobreventa colectiva:* la misma transacción de disponibilidad valida aforo en colectivas de salida única (cierra la carrera de sobreventa del roadmap en el punto donde hay dinero). Hospedaje por rango de noches queda fuera de la validación server-side en v1.
   - `createPaymentIntent` deja de confiar en `booking.totalPrice` (que escribía el cliente al crear la reserva): cierra el hueco residual del endurecimiento de pagos de julio 2026.
   - *Desviación del §6:* el cliente SÍ escribe `modalidad` al CREAR la reserva — es el marcador de que la app entiende la semántica privada (sin él, el servidor no podría distinguir apps viejas y el rechazo de la decisión 1 no funcionaría). Lo que el cliente nunca escribe: `montoTotal` y `salidaClaimedAt`; y el servidor sobreescribe `modalidad` con el valor real del servicio al calcular el pago.
   - *Regla anti retro-fechado:* en la creación de reservas, `createdAt` debe estar presente y dentro de ±10 min de `request.time` — el desempate entre PENDINGs no puede ganarse con un reloj manipulado, y de paso se cierra el hueco de reservas sin `createdAt` que nunca expiraban (holdsSeats las trataba como bloqueantes permanentes y el cron las ignoraba). Además `status` debe nacer `PENDING` (antes se podía CREAR una reserva directamente en PAID; el endurecimiento previo solo cubría updates).

## 13. Criterios de aceptación (smoke test antes de distribuir)

1. Crear servicio privado con tarifa base $8,400 / 6 incluidas / $1,200 extra / máx 20. Verificar preview de precio en formulario.
2. Como viajero: reservar 8 personas → total mostrado $10,800; pagar en modo test; verificar en Stripe dashboard, functions log y documento de booking que `montoTotal == 10800` y que lo escribió el servidor.
3. Verificar que esa salida quedó bloqueada para otros viajeros.
4. Intentar manipular el monto desde el cliente (o simular request con monto alterado) → el servidor debe ignorarlo y cobrar el monto correcto.
5. Servicio colectivo existente (sin campo `modalidad`) sigue funcionando: se puede ver, reservar y pagar.
5b. **Carrera de exclusividad:** dos dispositivos crean reserva PENDING para la misma salida privada e intentan pagar casi simultáneamente → exactamente uno cobra; el otro recibe "Esta salida ya fue reservada por otro viajero."
6. Dashboard muestra "Salidas disponibles" para el privado y "Cupos libres" para el colectivo, sin sumarlos.
7. Checklist permanente: build release APK, instalar en dispositivo real, login completo, verificar Crashlytics, y solo entonces distribuir.
