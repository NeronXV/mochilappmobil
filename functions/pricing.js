/**
 * Módulo único de precios (spec-modalidad-privada-colectiva §5).
 *
 * Toda function que cobre dinero calcula el monto AQUÍ, leyendo el servicio
 * de Firestore. El cliente solo calcula precios para mostrarlos en UI.
 *
 * Rutas de cálculo:
 *  - Pedido de comida (orderItems): se recalcula desde el menú del servicio.
 *  - PRIVADA: precioBase cubre personasIncluidas + precioPersonaExtra por
 *    persona adicional. Con rango de noches (checkOutDate), el total se
 *    multiplica por noches (interpretación v1 para renta privada).
 *  - COLECTIVA / legado (sin modalidad): precio unitario × unidades, donde
 *    unidades = noches si hay checkOutDate (hospedaje) o personas si no.
 */

const HOLD_MILLIS = 30 * 60 * 1000; // espejo de PENDING_HOLD_MILLIS en la app

/**
 * Lee el pricing de un servicio con tolerancia a documentos legado.
 * @param {Object} service Datos del documento del servicio.
 * @return {Object} {modalidad, ...campos según modalidad}
 */
function resolverPricing(service) {
  const modalidad = service.modalidad === "PRIVADA" ? "PRIVADA" : "COLECTIVA";
  const pricing = service.pricing || {};

  if (modalidad === "PRIVADA") {
    const precioBase = Number(pricing.precioBase);
    const personasIncluidas = Number(pricing.personasIncluidas);
    const precioPersonaExtra = Number(pricing.precioPersonaExtra);
    const capacidadMaxima = Number(pricing.capacidadMaxima);
    if (!(precioBase > 0) || !(personasIncluidas > 0) ||
        !(precioPersonaExtra >= 0) || !(capacidadMaxima >= personasIncluidas)) {
      throw new Error("PRICING_PRIVADA_INVALIDO");
    }
    return {modalidad, precioBase, personasIncluidas, precioPersonaExtra, capacidadMaxima};
  }

  // COLECTIVA: pricing.precioPorPersona para servicios nuevos; el campo
  // `precio` legado como fuente para documentos sin migrar.
  const precioPorPersona = Number(
      pricing.precioPorPersona !== undefined ? pricing.precioPorPersona : service.price);
  const capacidadMaxima = Number(
      pricing.capacidadMaxima !== undefined ? pricing.capacidadMaxima : (service.capacity || 0));
  if (!(precioPorPersona > 0)) throw new Error("PRICING_COLECTIVA_INVALIDO");
  return {modalidad, precioPorPersona, capacidadMaxima};
}

/**
 * Noches entre dos fechas yyyy-MM-dd; 0 si no hay checkOutDate válido.
 * @param {string} checkIn Fecha de entrada.
 * @param {string} checkOut Fecha de salida.
 * @return {number} Noches (>= 0).
 */
function noches(checkIn, checkOut) {
  if (!checkIn || !checkOut) return 0;
  const inMs = Date.parse(checkIn + "T00:00:00Z");
  const outMs = Date.parse(checkOut + "T00:00:00Z");
  if (isNaN(inMs) || isNaN(outMs) || outMs <= inMs) return 0;
  return Math.round((outMs - inMs) / 86400000);
}

/**
 * Total SIN descuento para una reserva por personas/noches.
 * @param {Object} pricing Resultado de resolverPricing().
 * @param {number} personas Personas de la reserva.
 * @param {number} numNoches Noches (0 = salida de un día).
 * @return {number} Total en pesos.
 */
function calcularTotal(pricing, personas, numNoches) {
  const unidadesNoche = numNoches > 0 ? numNoches : 1;
  if (pricing.modalidad === "PRIVADA") {
    const extras = Math.max(0, personas - pricing.personasIncluidas);
    return (pricing.precioBase + extras * pricing.precioPersonaExtra) * unidadesNoche;
  }
  // Colectiva: hospedaje cobra por noche (fórmula histórica de la app:
  // precio × noches); el resto por persona.
  const unidades = numNoches > 0 ? numNoches : Math.max(1, personas);
  return pricing.precioPorPersona * unidades;
}

/**
 * Total de un pedido de comida, recalculado desde el menú del servicio
 * (los precios del cliente no se usan). Lanza MENU_CAMBIADO si algún
 * producto del pedido ya no existe en el menú.
 * @param {Object} service Datos del servicio (menu, deliveryFee).
 * @param {Array} orderItems Items {name, quantity} del pedido.
 * @param {string} fulfillmentType "PICKUP" | "DELIVERY".
 * @return {number} Total en pesos.
 */
function calcularTotalPedido(service, orderItems, fulfillmentType) {
  const menu = Array.isArray(service.menu) ? service.menu : [];
  let total = 0;
  for (const item of orderItems) {
    const qty = Math.max(1, Number(item.quantity) || 1);
    const menuItem = menu.find((m) => m && m.name === item.name);
    if (!menuItem || !(Number(menuItem.price) > 0)) {
      throw new Error("MENU_CAMBIADO");
    }
    total += Number(menuItem.price) * qty;
  }
  if (fulfillmentType === "DELIVERY") {
    total += Number(service.deliveryFee) || 0;
  }
  return total;
}

/**
 * Aplica una promo de porcentaje simple ya validada.
 * @param {number} total Total original.
 * @param {number} discountPercent Porcentaje 1-100.
 * @return {{total: number, descuento: number}} Total final y descuento.
 */
function aplicarDescuento(total, discountPercent) {
  const pct = Number(discountPercent);
  if (!(pct > 0 && pct <= 100)) return {total: redondear(total), descuento: 0};
  const descuento = redondear(total * (pct / 100));
  return {total: redondear(total - descuento), descuento};
}

/**
 * Redondeo a 2 decimales (pesos con centavos).
 * @param {number} n Monto.
 * @return {number} Monto redondeado.
 */
function redondear(n) {
  return Math.round(n * 100) / 100;
}

/**
 * ¿Esta reserva sigue reteniendo cupo/salida? Espejo de holdsSeats() en la
 * app: pagadas/operadas sí; canceladas no; PENDING solo dentro del hold.
 * @param {Object} booking Datos de la reserva.
 * @param {number} now Epoch millis actual.
 * @return {boolean} true si retiene cupo.
 */
function retieneCupo(booking, now) {
  if (booking.status === "CANCELLED") return false;
  if (booking.status !== "PENDING") return true;
  const createdAt = Number(booking.createdAt) || 0;
  return createdAt <= 0 || now - createdAt < HOLD_MILLIS;
}

/**
 * ¿La reserva `otra` bloquea la salida privada frente a `mia`?
 * Bloquea si: está pagada/operada, tiene un claim de pago vigente (sello del
 * servidor, inmune al reloj del cliente), o es PENDING vigente más antigua
 * (createdAt; empate por id de documento).
 * @param {Object} otra {id, data} de la otra reserva.
 * @param {Object} mia {id, data} de la reserva que intenta pagar.
 * @param {number} now Epoch millis actual.
 * @return {boolean} true si `otra` bloquea.
 */
function bloqueaSalidaPrivada(otra, mia, now) {
  if (!retieneCupo(otra.data, now)) return false;
  if (otra.data.status !== "PENDING") return true; // PAID / CHECKED_IN / COMPLETED
  const claim = otra.data.salidaClaimedAt;
  if (claim && typeof claim.toMillis === "function" &&
      now - claim.toMillis() < HOLD_MILLIS) {
    return true; // ya inició su pago y su claim sigue vigente
  }
  const otraCreated = Number(otra.data.createdAt) || 0;
  const miaCreated = Number(mia.data.createdAt) || 0;
  if (otraCreated !== miaCreated) return otraCreated < miaCreated;
  return otra.id < mia.id;
}

module.exports = {
  HOLD_MILLIS,
  resolverPricing,
  noches,
  calcularTotal,
  calcularTotalPedido,
  aplicarDescuento,
  redondear,
  retieneCupo,
  bloqueaSalidaPrivada,
};
