/**
 * Tests del módulo de precios. Sin framework: node test/pricing.test.js
 * (assert nativo; sale con código != 0 si algo falla).
 */
const assert = require("node:assert");
const p = require("../pricing");

let pasados = 0;
/**
 * Corre un caso y cuenta.
 * @param {string} nombre Descripción del caso.
 * @param {Function} fn Cuerpo con asserts.
 */
function test(nombre, fn) {
  try {
    fn();
    pasados++;
  } catch (e) {
    console.error(`✗ ${nombre}`);
    throw e;
  }
}

// --- resolverPricing ---
test("privada válida", () => {
  const r = p.resolverPricing({modalidad: "PRIVADA", pricing: {
    precioBase: 8400, personasIncluidas: 6, precioPersonaExtra: 1200, capacidadMaxima: 20}});
  assert.equal(r.modalidad, "PRIVADA");
  assert.equal(r.precioBase, 8400);
});
test("privada inválida (incluidas > max) lanza", () => {
  assert.throws(() => p.resolverPricing({modalidad: "PRIVADA", pricing: {
    precioBase: 8400, personasIncluidas: 21, precioPersonaExtra: 1200, capacidadMaxima: 20}}));
});
test("legado sin modalidad → colectiva con precio", () => {
  const r = p.resolverPricing({price: 850.0, capacity: 12});
  assert.equal(r.modalidad, "COLECTIVA");
  assert.equal(r.precioPorPersona, 850);
  assert.equal(r.capacidadMaxima, 12);
});
test("colectiva nueva usa pricing.precioPorPersona sobre precio legado", () => {
  const r = p.resolverPricing({price: 1, modalidad: "COLECTIVA",
    pricing: {precioPorPersona: 850, capacidadMaxima: 10}});
  assert.equal(r.precioPorPersona, 850);
});

// --- calcularTotal: caso Mario Diaz Tours (spec §12.1-12.2) ---
const mario = p.resolverPricing({modalidad: "PRIVADA", pricing: {
  precioBase: 8400, personasIncluidas: 6, precioPersonaExtra: 1200, capacidadMaxima: 20}});
test("privada 6 personas → 8400 (base cubre incluidas)", () =>
  assert.equal(p.calcularTotal(mario, 6, 0), 8400));
test("privada 2 personas → 8400 (base no se prorratea)", () =>
  assert.equal(p.calcularTotal(mario, 2, 0), 8400));
test("privada 8 personas → 10800 (criterio de aceptación)", () =>
  assert.equal(p.calcularTotal(mario, 8, 0), 10800));
test("privada 20 personas → 25200", () =>
  assert.equal(p.calcularTotal(mario, 20, 0), 8400 + 14 * 1200));
test("privada con 3 noches multiplica", () =>
  assert.equal(p.calcularTotal(mario, 8, 3), 10800 * 3));

const colectiva = p.resolverPricing({price: 850});
test("colectiva 4 personas → 3400", () =>
  assert.equal(p.calcularTotal(colectiva, 4, 0), 3400));
test("colectiva hospedaje: 3 noches → precio×noches (fórmula histórica)", () =>
  assert.equal(p.calcularTotal(colectiva, 4, 3), 2550));

// --- noches ---
test("noches 11→14 jul = 3", () => assert.equal(p.noches("2026-07-11", "2026-07-14"), 3));
test("noches sin checkout = 0", () => assert.equal(p.noches("2026-07-11", ""), 0));
test("noches invertidas = 0", () => assert.equal(p.noches("2026-07-14", "2026-07-11"), 0));

// --- pedido de comida ---
const puesto = {menu: [{name: "Taco", price: 35}, {name: "Agua", price: 20}], deliveryFee: 50};
test("pedido pickup desde menú", () =>
  assert.equal(p.calcularTotalPedido(puesto, [{name: "Taco", quantity: 3}, {name: "Agua", quantity: 1}], "PICKUP"), 125));
test("pedido delivery suma envío", () =>
  assert.equal(p.calcularTotalPedido(puesto, [{name: "Taco", quantity: 2}], "DELIVERY"), 120));
test("producto fuera del menú lanza MENU_CAMBIADO", () =>
  assert.throws(() => p.calcularTotalPedido(puesto, [{name: "Torta", quantity: 1}], "PICKUP"),
      /MENU_CAMBIADO/));

// --- descuento ---
test("descuento 15% de 10800 → 9180", () => {
  const r = p.aplicarDescuento(10800, 15);
  assert.equal(r.total, 9180);
  assert.equal(r.descuento, 1620);
});
test("descuento inválido no descuenta", () =>
  assert.equal(p.aplicarDescuento(100, 0).descuento, 0));

// --- exclusividad de salida privada ---
const NOW = 1_800_000_000_000;
const mia = {id: "bbb", data: {status: "PENDING", createdAt: NOW - 60_000}};
test("PAID bloquea siempre", () =>
  assert.equal(p.bloqueaSalidaPrivada({id: "x", data: {status: "PAID", createdAt: 0}}, mia, NOW), true));
test("CANCELLED nunca bloquea", () =>
  assert.equal(p.bloqueaSalidaPrivada({id: "x", data: {status: "CANCELLED", createdAt: 0}}, mia, NOW), false));
test("PENDING vencida (hold expirado) no bloquea", () =>
  assert.equal(p.bloqueaSalidaPrivada({id: "x", data: {status: "PENDING", createdAt: NOW - p.HOLD_MILLIS - 1}}, mia, NOW), false));
test("PENDING más antigua bloquea", () =>
  assert.equal(p.bloqueaSalidaPrivada({id: "x", data: {status: "PENDING", createdAt: NOW - 120_000}}, mia, NOW), true));
test("PENDING más nueva no bloquea", () =>
  assert.equal(p.bloqueaSalidaPrivada({id: "x", data: {status: "PENDING", createdAt: NOW - 10_000}}, mia, NOW), false));
test("empate de createdAt: desempata id menor", () => {
  const otra = {id: "aaa", data: {status: "PENDING", createdAt: NOW - 60_000}};
  assert.equal(p.bloqueaSalidaPrivada(otra, mia, NOW), true);
  const otra2 = {id: "zzz", data: {status: "PENDING", createdAt: NOW - 60_000}};
  assert.equal(p.bloqueaSalidaPrivada(otra2, mia, NOW), false);
});
test("claim vigente bloquea aunque sea más nueva", () => {
  const otra = {id: "zzz", data: {status: "PENDING", createdAt: NOW - 10_000,
    salidaClaimedAt: {toMillis: () => NOW - 5_000}}};
  assert.equal(p.bloqueaSalidaPrivada(otra, mia, NOW), true);
});

console.log(`OK — ${pasados} tests pasaron`);
