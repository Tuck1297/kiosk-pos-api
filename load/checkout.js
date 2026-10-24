// 500 cashier registers ringing up sales, exactly as the register UI does:
//
//   sign in once (HttpOnly session cookie) → load menu → add items (each add re-prices the cart: POST /orders/quote)
//   → ~10% apply promo code PARKFAN10 at review
//   → create order (Idempotency-Key) → pay (cash, card, or a declined card then cash)
//
// Run with the stack up:  docker compose --profile loadtest run --rm k6
// Tune with env: REGISTERS (default 500), DURATION (default 3m), BASE_URL.
//
// Pace: a sale every ~10-15 s per register, so 500 registers is ~35-50 sales/s,
// several times a realistic lunch rush across all kiosks.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const REGISTERS = parseInt(__ENV.REGISTERS || '500', 10);
const DURATION = __ENV.DURATION || '3m';
const LOCATION = __ENV.LOCATION_ID || '5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11';

// A 402 is the expected answer for the declined test card, not a failure.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 402));

const sales = new Counter('sales_completed');
const saleDuration = new Trend('sale_server_time', true);

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  // A register's browser keeps its session cookie for the whole shift. (k6's default is
  // to clear cookies every iteration, which would sign every register out after one sale.)
  noCookiesReset: true,
  scenarios: {
    registers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '60s', target: REGISTERS },   // registers sign in and come online
        { duration: DURATION, target: REGISTERS }, // sustained peak
        { duration: '15s', target: 0 },
      ],
      gracefulRampDown: '20s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{name:menu}': ['p(95)<300'],
    'http_req_duration{name:quote}': ['p(95)<300'],
    'http_req_duration{name:create_order}': ['p(95)<600'],
    'http_req_duration{name:pay}': ['p(95)<600'],
    // bcrypt makes sign-in deliberately slow; it happens once per shift, not per sale.
    'http_req_duration{name:login}': ['p(95)<2000'],
  },
};

// Every request carries the CSRF header, like the register does. The session cookie from
// sign-in is kept in each virtual register's own cookie jar.
const JSON_HEADERS = { 'Content-Type': 'application/json', Accept: 'application/json', 'X-Kiosk-Register': '1' };
const CASHIER = { employeeId: __ENV.CASHIER_ID || 'C2001', pin: __ENV.CASHIER_PIN || '1111' };

// Per virtual register: sign in once at the start of its shift.
let signedIn = false;

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function think(min, max) {
  sleep(min + Math.random() * (max - min));
}

function post(path, body, name, headers = {}) {
  return http.post(`${BASE}/v1${path}`, JSON.stringify(body), {
    headers: { ...JSON_HEADERS, ...headers },
    tags: { name },
  });
}

export default function () {
  if (!signedIn) {
    const login = post('/auth/login', CASHIER, 'login');
    if (!check(login, { 'sign in 200': (r) => r.status === 200 })) {
      think(1, 3);
      return;
    }
    signedIn = true;
  }
  // Like the register: a 401 at any point (e.g. session expired) means sign in again.
  const signInAgainOn401 = (res) => {
    if (res.status === 401) signedIn = false;
    return res;
  };
  let serverMs = 0;

  // The register reloads the menu after every sale (served from Redis).
  const menuRes = http.get(`${BASE}/v1/locations/${LOCATION}/menu-items?available=true&limit=100`, {
    headers: JSON_HEADERS,
    tags: { name: 'menu' },
  });
  serverMs += menuRes.timings.duration;
  signInAgainOn401(menuRes);
  if (!check(menuRes, { 'menu 200': (r) => r.status === 200 })) {
    think(1, 2);
    return;
  }
  const items = menuRes.json('items');

  // Build a cart of 1-4 taps; every change is re-priced on the server.
  const cart = [];
  const taps = 1 + Math.floor(Math.random() * 4);
  let quote;
  for (let i = 0; i < taps; i++) {
    think(0.3, 1.2);
    const item = items[Math.floor(Math.random() * items.length)];
    const line = cart.find((l) => l.menuItemId === item.id);
    if (line) line.quantity += 1;
    else cart.push({ menuItemId: item.id, quantity: 1 });
    quote = post('/orders/quote', { locationId: LOCATION, lines: cart }, 'quote');
    serverMs += quote.timings.duration;
    signInAgainOn401(quote);
    check(quote, { 'quote 200': (r) => r.status === 200 });
  }

  // Review with the guest; about one sale in ten applies a promo code (re-priced on the server).
  think(1, 2.5);
  const discount = Math.random() < 0.1 ? { code: 'PARKFAN10' } : undefined;
  if (discount) {
    quote = post('/orders/quote', { locationId: LOCATION, lines: cart, discount }, 'quote');
    serverMs += quote.timings.duration;
    signInAgainOn401(quote);
    check(quote, { 'discounted quote 200': (r) => r.status === 200 && r.json('discountTotal.amountCents') > 0 });
  }
  const order = post(
    '/orders',
    { locationId: LOCATION, channel: 'COUNTER', guestName: `Guest ${__VU}`, lines: cart, discount },
    'create_order',
    { 'Idempotency-Key': uuid() },
  );
  serverMs += order.timings.duration;
  signInAgainOn401(order);
  if (!check(order, {
    'order 201': (r) => r.status === 201,
    'order total matches quote': (r) => r.status === 201 && quote && quote.status === 200
      && r.json('total.amountCents') === quote.json('total.amountCents'),
  })) {
    think(2, 4);
    return;
  }
  const orderId = order.json('id');
  const total = order.json('total.amountCents');
  const ifMatch = { 'If-Match': `"${order.json('version')}"` };

  // Payment: 65% card, 30% cash, 5% a declined card followed by cash.
  think(1, 3);
  const roll = Math.random();
  let paid;
  if (roll < 0.05) {
    const declined = post(`/orders/${orderId}/payments`,
      { tenderType: 'CARD', cardToken: 'tok_decline_insufficient_funds_0002' },
      'pay', { 'Idempotency-Key': uuid(), ...ifMatch });
    serverMs += declined.timings.duration;
    check(declined, { 'decline is 402': (r) => r.status === 402 });
    think(1, 2);
    paid = post(`/orders/${orderId}/payments`,
      { tenderType: 'CASH', cashTenderedCents: Math.ceil(total / 2000) * 2000 },
      'pay', { 'Idempotency-Key': uuid(), ...ifMatch });
  } else if (roll < 0.35) {
    paid = post(`/orders/${orderId}/payments`,
      { tenderType: 'CASH', cashTenderedCents: Math.ceil(total / 500) * 500 },
      'pay', { 'Idempotency-Key': uuid(), ...ifMatch });
  } else {
    paid = post(`/orders/${orderId}/payments`,
      { tenderType: 'CARD', cardToken: 'tok_visa_4242' },
      'pay', { 'Idempotency-Key': uuid(), ...ifMatch });
  }
  serverMs += paid.timings.duration;
  signInAgainOn401(paid);
  if (check(paid, { 'payment 201': (r) => r.status === 201 })) {
    sales.add(1);
    saleDuration.add(serverMs);
  }

  // Next guest steps up.
  think(2, 4);
}

function fmt(ms) {
  return ms === undefined ? 'n/a' : `${ms.toFixed(0)} ms`;
}

export function handleSummary(data) {
  const m = data.metrics;
  const p = (name, stat) => (m[name] ? m[name].values[stat] : undefined);
  const durationS = data.state.testRunDurationMs / 1000;
  const lines = [
    `Registers (peak VUs): ${p('vus_max', 'max')}`,
    `Sales completed:      ${p('sales_completed', 'count') || 0} (${((p('sales_completed', 'count') || 0) / durationS).toFixed(1)}/s over ${durationS.toFixed(0)} s)`,
    `HTTP requests:        ${p('http_reqs', 'count')} (${p('http_reqs', 'rate').toFixed(1)}/s)`,
    `Failed requests:      ${(100 * p('http_req_failed', 'rate')).toFixed(2)}%`,
    `Checks passed:        ${(100 * p('checks', 'rate')).toFixed(2)}%`,
    '',
    'Latency p50 / p95 / p99:',
  ];
  for (const name of ['login', 'menu', 'quote', 'create_order', 'pay']) {
    const key = `http_req_duration{name:${name}}`;
    lines.push(`  ${name.padEnd(13)} ${fmt(p(key, 'med'))} / ${fmt(p(key, 'p(95)'))} / ${fmt(p(key, 'p(99)'))}`);
  }
  lines.push(`  whole sale    ${fmt(p('sale_server_time', 'med'))} / ${fmt(p('sale_server_time', 'p(95)'))} (server time only)`);
  const failed = Object.entries(m).filter(([, v]) => v.thresholds && Object.values(v.thresholds).some((t) => !t.ok)).map(([k]) => k);
  lines.push('', failed.length ? `THRESHOLDS FAILED: ${failed.join(', ')}` : 'All thresholds passed.');

  const text = lines.join('\n') + '\n';
  const out = { stdout: text };
  if (__ENV.RESULTS_DIR) {
    out[`${__ENV.RESULTS_DIR}/summary.txt`] = text;
    out[`${__ENV.RESULTS_DIR}/summary.json`] = JSON.stringify(data, null, 2);
  }
  return out;
}
