# Mochilapp — Documento de contexto técnico

*Generado el 2026-07-04 a partir del código fuente. Para revisión con asesor externo.*

Mochilapp conecta viajeros con negocios turísticos locales (mezcla de Booking/Airbnb/TripAdvisor, con panel operativo para el negocio dentro de la misma app). Dos repos:

- **App Android** (este repo): Kotlin + Jetpack Compose, Navigation 3, Firebase (Auth, Firestore, Storage, Functions, FCM), Stripe (PaymentSheet), Google Maps Compose, Gemini (asistente "Mochi"). ~16.700 líneas en 50 archivos.
- **Panel web admin** (`mochilapp-admin`): React 19 + Vite, Firebase Hosting. Contiene el super-admin y el panel de negocio web (en migración hacia la app nativa). Las Cloud Functions (Node 20) y las reglas de Firestore se versionan entre ambos repos (functions aquí, reglas en el repo web).

---

## 1. Categorías de servicio y sus flujos

El enum `CompanyType` (data/User.kt) define 9 categorías activas: `HOTEL`, `HOSTEL`, `PROPERTY_RENTAL`, `RESTAURANT`, `FOOD_STAND`, `BOAT_TOUR`, `TOUR_AGENCY`, `TRANSPORT`, `OTHER` (+ `NONE` interno). La categoría vive en dos lugares: `users.companyType` (giro declarado al registrarse) y `services.type` (categoría de cada servicio publicado; una empresa puede publicar de cualquier tipo).

### Panel de empresa por categoría (CompanyDashboard → módulos)

El dashboard muestra una tarjeta de módulo si la empresa tiene servicios de ese tipo **o** se registró con ese giro (Dashboards.kt ~1563, equivalente al `moduleRegistry.ts` del panel web):

| Categoría(s) | Módulo en app | Pantalla | Qué hace |
|---|---|---|---|
| BOAT_TOUR | Control de Embarcaciones | BoatTourModuleScreen | Mapa de asientos, ocupación por salida, aforo |
| HOTEL, HOSTEL | Gestión de Hospedaje | LodgingModuleScreen | Habitaciones/camas ocupadas y libres por día |
| PROPERTY_RENTAL | Calendario de Rentas | PropertyRentalModuleScreen | Disponibilidad mensual |
| RESTAURANT, FOOD_STAND | Gestión Gastronómica | RestaurantModuleScreen | Menú digital, estado del local, pedidos |
| TOUR_AGENCY | Control de Salidas | TourAgencyModuleScreen | Tablero de salidas por horario |
| TRANSPORT | Control de Rutas | TransportModuleScreen | Corridas, asientos, pasajeros por horario |
| OTHER | *(sin módulo)* | — | Solo lista genérica de reservas del dashboard |

### Dónde comparten código y dónde divergen

**Compartido:**
- **AddServiceScreen** es una sola pantalla para todas las categorías, con secciones condicionales por tipo: capacidad + horarios de salida (BOAT_TOUR/TOUR_AGENCY/TRANSPORT), check-in/out + amenidades + editor de habitaciones (HOTEL/HOSTEL/PROPERTY_RENTAL), switch "abierto ahora" (RESTAURANT/FOOD_STAND), pickup/delivery + costo de envío (FOOD_STAND), ruta/origen/destino/vehículo/chofer (TRANSPORT), guía (TOUR_AGENCY).
- **BookingFlowScreen** es el flujo de reserva único del viajero, con una bifurcación interna: hospedaje usa selector de **rango de fechas** (noches, solapamiento contra reservas existentes) y el resto usa **fecha única + horario de salida** (chips con cupos restantes por horario). La disponibilidad se calcula en cliente con `holdsSeats()` (hold de 30 min para PENDING).
- **PaymentScreen** y **BookingViewModel** son únicos para todo (reserva clásica y pedido de comida).
- **DepartureBoardCommon.kt** comparte componentes entre los módulos de salidas (tour agency / transport).
- Los 6 módulos de empresa repiten el mismo patrón (selector de servicio → fecha → filtrar `myBookings` en cliente), pero son 6 archivos separados con la lógica de filtrado duplicada.

**Divergente:**
- **FOOD_STAND** es la única categoría con flujo de compra propio: `FoodOrderScreen` (carrito por productos del `menu`, pickup o delivery con `deliveryFee`) en vez de BookingFlow. En ServiceDetailScreen el botón cambia: `isFoodStand → onOrderClick`, resto → `onBookClick`. El pedido reutiliza la colección `bookings` con campos extra (`orderItems`, `fulfillmentType`, `orderStatus`).
- **Hospedaje** deriva su capacidad de las habitaciones/camas configuradas (suma de `rooms[].capacity`); el resto usa el campo `capacity` directo.
- **RESTAURANT** reserva "mesa" con el flujo genérico (fecha + personas); no usa el menú para cobrar (el menú con precios solo cobra en FOOD_STAND).

---

## 2. Puntos donde se pide ubicación al usuario

| # | Dónde se pide | Tipo de dato | Campo en Firestore | Quién lo consume |
|---|---|---|---|---|
| 1 | **Registro empresa, paso 1**: "Ubicación del negocio (ciudad, zona)" | Texto libre | `users.businessLocation` | Prefill de `location` al crear servicio nuevo; se muestra en CompanyProfileTab; editable después en el diálogo de perfil |
| 2 | **Registro empresa, paso 1**: "Ubicar mi negocio en el mapa" (LocationPickerDialog) | Pin lat/lng | `users.businessLat`, `users.businessLng` | Prefill del pin al crear servicio nuevo (AddServiceScreen). **No editable después del registro** (el diálogo de editar perfil solo cambia el texto, no el pin) |
| 3 | **Registro empresa, paso 2**: "Punto de encuentro" (solo BOAT_TOUR / TOUR_AGENCY) | Texto libre | `users.meetingPoint` | ⚠️ **Nadie lo lee.** Campo muerto (ver deuda #5). Igual pasa con `users.checkIn` / `users.checkOut` del registro de hoteles |
| 4 | **AddServiceScreen**: "Ciudad o pueblo" | Texto libre | `services.location` | Tarjetas del marketplace, búsqueda, fallback de "Cómo llegar" si no hay pin |
| 5 | **AddServiceScreen**: "Dirección o referencia exacta" (solo giros SIN punto de encuentro: hospedaje, restaurantes, rental, other) | Texto libre | `services.address` | ServiceDetailScreen (bloque ubicación) y BookingDetailScreen ("Cómo llegar" por texto si no hay coordenadas) |
| 6 | **AddServiceScreen → MapPickerScreen**: pin del servicio. Para BOAT_TOUR/TOUR_AGENCY/TRANSPORT el pin **es** el punto de encuentro (decisión de diseño: se eliminó el campo de texto para no duplicar); para el resto es la ubicación del local | Pin lat/lng | `services.latitude`, `services.longitude` | TourismMapScreen (pines del mapa del viajero), ServiceDetailScreen (intent `geo:` a Google Maps), BookingDetailScreen ("Cómo llegar" + **gate de proximidad GPS del check-in "Vive"** que otorga MochiPuntos) |
| 7 | **FoodOrderScreen**: "Dirección de entrega" (solo pedidos DELIVERY) | Texto libre | `bookings.deliveryAddress` | Resumen en PaymentScreen; panel gastronómico del negocio para despachar |

**Nota clave para el asesor:** `services.meetingPoint` existe en el modelo y ServiceDetailScreen todavía lo muestra si viene con datos (servicios legado), pero **ya no hay UI que lo capture** — el draft lo guarda siempre vacío desde que el pin lo reemplazó. Hay tres generaciones de "ubicación" conviviendo: texto de ciudad (`location`), texto de dirección (`address`) y pin (`latitude/longitude`), más los campos muertos del registro.

---

## 3. Modelo de datos (Firestore)

### Colecciones

| Colección | Doc clave | Campos principales |
|---|---|---|
| `users` | uid (doc id) | `email`, `role` (TRAVELER/COMPANY), `companyType`, `name`, gamificación (`mochiPoints`, `passportLevel`, `badges`, `savedServices`), datos de negocio (`businessName/Description/Location/Lat/Lng/Hours`, `phone`, `whatsapp`, `rfc`, `rnt`, `businessVerified`, `status`), `fcmToken` |
| `services` | auto-id | `ownerEmail` ⭐, `ownerUid`, `name`, `price`, `type`, `location/address/latitude/longitude`, `capacity`, `departureTimes[]`, `rooms[]`, `menu[]`, `amenities[]`, `rules[]`, campos de transporte (`routeName/origin/destination/vehicleName/driverName`), `rating`+`reviewCount` (solo Cloud Function), `isVisible`, `isOpen`, `isRecommended`, `offersPickup/offersDelivery/deliveryFee` |
| `bookings` | auto-id | `serviceId`, `travelerEmail` ⭐, `ownerEmail` ⭐, `date` (yyyy-MM-dd), `checkOutDate` (hospedaje), `departureTime`, `slots`, `totalPrice`, `status` (PENDING→PAID→CHECKED_IN→COMPLETED / CANCELLED), `createdAt` (hold 30 min), `confirmationCode` (6 dígitos), promos (`promoCode/discountAmount/...`), pedido de comida (`orderItems[]`, `fulfillmentType`, `deliveryAddress`, `deliveryFee`, `orderStatus`), auditoría de pago (`paidAt`, `paymentIntentId`, `amountPaid` — escritos solo por webhook Stripe) |
| `reviews` | auto-id | `serviceId`, `authorEmail`, `rating` 1-5, `comment`. El promedio lo recalcula la CF `onReviewCreated` |
| `posts` / `comments` | auto-id | Feed social: `authorEmail`, `content`, `likes/likedBy`, `linkedServiceId` (post enlazado a servicio) |
| `stories` | auto-id | Historias 24h de empresas: `ownerEmail`, `imageUrl`, `expiresAt` |
| `promos` | auto-id | Ofertas flash: `ownerEmail`, `discountPercent`, `promoCode`, `expiresAt`, `isActive` |
| `notices` | auto-id | Avisos operativos negocio→viajeros: `ownerEmail`, `serviceId`, `date` opcional, `severity` |
| `admins`, `payments` | uid / auto-id | Solo panel super-admin (web) |

### Relación empresa ↔ servicios ↔ reservas

⭐ **La llave foránea de todo el sistema es el email**, no el uid:
- Empresa → sus servicios: `services.ownerEmail == user.email` (query `getServicesByOwner`). `ownerUid` se guarda pero no se usa como llave.
- Reserva → viajero: `bookings.travelerEmail`; reserva → empresa: `bookings.ownerEmail` (copiado del servicio al reservar).
- Las Cloud Functions localizan usuarios con `where("email","==",...)` para notificar y acreditar puntos.
- Consecuencia: ya hubo bugs por mayúsculas/minúsculas en emails (hay normalización defensiva en el login). Migrar a uid es deuda conocida.

Los documentos denormalizan agresivamente (la reserva copia `serviceName`, `ownerEmail`, precios) — apropiado para Firestore, pero sin proceso de re-sincronización si el servicio cambia.

### Seguridad (firestore.rules, versionadas en el repo web)

Default-deny. Viajero: crea reservas a su nombre, solo puede poner `status=CANCELLED` y marcar `travelerCheckedInAt`. Empresa: opera reservas propias (`CHECKED_IN/COMPLETED/CANCELLED`). **`PAID` solo lo escribe el webhook de Stripe (Admin SDK)**. Reputación (`rating/reviewCount/isRecommended`) y `role/businessVerified` protegidos. Lectura de `bookings` abierta a autenticados (necesaria para calcular cupos en cliente; hay TODO para moverlo a agregados). *Estado: la versión endurecida está commiteada pero pendiente de deploy (validación de pago real en curso).*

### Cloud Functions (functions/index.js, Node 20, 1st gen)

`createPaymentIntent` (callable; monto calculado server-side desde el booking) · `stripeWebhook` (único escritor de PAID) · `onBookingPaid` (push a la empresa) · `onPromoCreated` / `onNoticeCreated` (push a viajeros) · `dailyTripReminders` (cron 8:00) · `expireStalePendingBookings` (cron 30 min, libera holds) · `onReviewCreated` (promedio de rating) · 4 triggers de gamificación (`awardPointsOn...`: 50 pts reserva pagada, 40 check-in, 30 reseña, 20 post; niveles e insignias server-side).

---

## 4. Estado del flujo BOAT_TOUR

### Completo y funcionando

- **Publicación**: categoría, precio, capacidad, horarios de salida (editor con chips + reloj, formato HH:mm ordenado), ciudad + pin de punto de encuentro en mapa.
- **Reserva del viajero**: fecha + selección de horario con **cupos restantes por salida** (cada horario descuenta solo sus propias reservas), hold de 30 min para PENDING, bloqueo si no hay cupo.
- **Módulo de empresa** (BoatTourModuleScreen, alineado visualmente al BoatSeatMap del panel web):
  - Selector de embarcación (múltiples servicios BOAT_TOUR).
  - Fecha + chips de "próximas salidas con reservas" (14 días) + selector de horario.
  - KPIs: capacidad (editable con diálogo), pagados, apartados, libres.
  - Aviso explícito si el aforo no está configurado (no inventa capacidad).
  - **Alerta de sobreventa** si reservas activas > capacidad.
  - Mapa de cubierta visual (proa/motor/asientos en filas de 2, colores por estado).
  - Manifiesto de pasajeros por salida (nombre, lugares, monto, estado de pago).
- **Operación de la reserva**: check-in con código de 6 dígitos y "marcar ejecutado" desde BookingDetailCompanyScreen; check-in "Vive" del viajero con gate GPS.

### A medias / limitaciones

1. **Los asientos del mapa no son reales**: la asignación es secuencial-visual (primeros N = pagados, siguientes = apartados). No hay asignación de asiento por pasajero ni selección de asiento al reservar — es un indicador de ocupación con forma de lancha, igual que el web.
2. **Reservas legado sin `departureTime` cuentan en *todas* las salidas del día**: el filtro (`BoatTourModuleScreen.kt:82`) deja pasar reservas sin horario a cualquier horario seleccionado, inflando la ocupación de cada salida (también pasa en BookingFlow al calcular cupos por horario). Con datos nuevos no ocurre (el horario es obligatorio al reservar cuando existen `departureTimes`).
3. **El manifiesto no tiene acciones**: para hacer check-in de un pasajero hay que salir del módulo e ir al detalle de reserva del dashboard. Un capitán en el muelle querría palomear desde el manifiesto.
4. **Riesgo de sobreventa por carrera**: la disponibilidad se calcula en el cliente y la reserva se escribe sin transacción server-side. El módulo lo *detecta* (alerta) pero no lo *previene*. Mitigado parcialmente por el hold + baja concurrencia actual.
5. **Sin gestión de flota como entidad**: cada "embarcación" es un servicio; no hay noción de lancha física compartida entre varios tours (dos servicios de la misma lancha venden aforo doble).

---

## 5. Deuda técnica e inconsistencias detectadas

**Datos / modelo**
1. **Email como llave foránea universal** (servicios, reservas, funciones). Frágil ante cambios de email y mayúsculas; ya causó bugs. `ownerUid` existe pero no se usa. Migración a uid recomendada.
2. **Tres generaciones de ubicación conviviendo** (`location` texto, `address` texto, `latitude/longitude` pin) + campos muertos de registro (`users.meetingPoint`, `users.checkIn/checkOut`) que se capturan y nadie lee. `services.meetingPoint` se muestra si existe pero ya no se puede capturar.
3. **`businessHours` con dos formas**: string en `users` ("09:00 - 18:00") y `Map<String,String>` en `services` (`{"general": "..."}`) — la clave "general" es la única usada.
4. **Trampa de serialización Firestore con booleanos `is*`**: `isOpen/isVisible/isActive/isRecommended` requieren `@field:JvmField`; los docs legado guardaron `open/active` y las functions tienen que leer ambos. Cualquier boolean nuevo `isX` repetirá el bug si se olvida la anotación.
5. **`capacity` significa cosas distintas por giro**: aforo por salida (lanchas/tours), plazas totales derivadas de camas (hospedaje), sin uso real (restaurante).
6. **Dualidad `status` / `orderStatus`** en pedidos de comida (pago vs preparación) — funciona, pero es fácil confundirse.

**Arquitectura / código**
7. **Room es código muerto**: `AppDatabase` + 4 DAOs + entidades sin un solo uso (y KSP compilándolos). Borrar o usar para offline real.
8. **Dashboards.kt con 2.340 líneas** (dashboard de viajero + empresa + KPIs + diálogos en un archivo).
9. **Lógica de disponibilidad duplicada** en 6 módulos de empresa + BookingFlow, cada uno filtrando `bookings` en cliente con variantes sutiles (algunos usan `holdsSeats()`, los módulos de empresa no — muestran PENDING vencidos como "apartados").
10. **`getAllServices()` escucha la colección completa** sin filtro de `isVisible` ni paginación (el filtro se hace en cliente). Costo y latencia crecen linealmente con el catálogo.
11. Sin tests (solo los Example de plantilla); candidatos ideales: `holdsSeats()`, solapamiento de noches, cupos por salida, descuentos.

**Producto / release**
12. **i18n a medias**: se ofrecen ES/EN/FR pero `Translations.kt` cubre ~90 claves; pantallas enteras hardcodeadas en español; el idioma elegido no persiste (se pierde al reiniciar; DataStore está como dependencia sin usar para esto).
13. **Tema claro forzado** (fix reciente): el modo oscuro del sistema volvía el texto invisible sobre los fondos blancos fijos. Modo oscuro real = auditar ~30 pantallas.
14. **Sin Crashlytics ni Analytics** — cero visibilidad de errores/uso en producción.
15. **Release sin R8/minify** y `GEMINI_API_KEY` embebida en el APK (extraíble); lo correcto es proxearla por Cloud Function. Sin App Check.
16. **El viajero no puede cancelar una reserva** desde la UI (las reglas ya lo permiten; falta el flujo + política de reembolso).
17. El "QR" del ticket de pago es un ícono decorativo, no un QR escaneable.
18. Se permite publicar servicios con precio 0 y sin categoría validada más allá del formulario.
19. Runtime de functions **nodejs20 obsoleto el 30-oct-2026** y `firebase-functions` 4.x desactualizado.

**Circuito de pago (recién resuelto, en transición)**
20. El monto ahora se calcula server-side y `PAID` solo lo escribe el webhook de Stripe (verificado de punta a punta en test mode). Pendiente: deploy de las reglas endurecidas tras validar un pago real desde la app, y publicar la versión de app que ya no escribe PAID.
