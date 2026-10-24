import type { components } from './schema';

type S = components['schemas'];
export type Location = S['Location'];
export type MenuItem = S['MenuItem'];
export type MenuItemPage = S['MenuItemPage'];
export type MenuCategory = S['MenuCategory'];
export type Money = S['Money'];
export type OrderLineRequest = S['OrderLineRequest'];
export type QuoteRequest = S['QuoteRequest'];
export type OrderQuote = S['OrderQuote'];
export type CreateOrderRequest = S['CreateOrderRequest'];
export type Order = S['Order'];
export type CreatePaymentRequest = S['CreatePaymentRequest'];
export type Payment = S['Payment'];
export type Problem = S['Problem'];
export type Staff = S['Staff'];
export type CompReason = S['CompReason'];
export type CompRequest = S['CompRequest'];
export type DiscountRequest = S['DiscountRequest'];
export type AppliedDiscount = S['AppliedDiscount'];

/** The API answered with an error (problem+json). */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: Partial<Problem> | null;

  constructor(status: number, problem: Partial<Problem> | null) {
    super(problem?.detail ?? problem?.title ?? `Request failed (${status})`);
    this.status = status;
    this.problem = problem;
  }

  get code(): string | undefined {
    return this.problem?.code;
  }
}

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? '';

/** Called when any request comes back 401, e.g. the session cookie expired mid-shift. */
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  // CSRF protection: the API refuses changes without this header, and another site's page
  // can't add it to a request.
  headers.set('X-Kiosk-Register', '1');
  if (init.body) {
    headers.set('Content-Type', 'application/json');
  }
  // The HttpOnly session cookie is sent by the browser automatically (same origin).
  const res = await fetch(`${BASE}/v1${path}`, { ...init, headers, credentials: 'same-origin' });
  const text = await res.text();
  let body: unknown = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = null;
    }
  }
  if (!res.ok) {
    const error = new ApiError(res.status, body as Partial<Problem> | null);
    if (res.status === 401 && !path.startsWith('/auth/login')) {
      onUnauthorized();
    }
    throw error;
  }
  return body as T;
}

export const api = {
  login: (employeeId: string, pin: string) =>
    request<Staff>('/auth/login', { method: 'POST', body: JSON.stringify({ employeeId, pin }) }),

  logout: () => request<void>('/auth/logout', { method: 'POST' }),

  me: () => request<Staff>('/auth/me'),

  location: (id: string) => request<Location>(`/locations/${id}`),

  menu: (locationId: string) => request<MenuItemPage>(`/locations/${locationId}/menu-items?limit=100`),

  /** Server-side pricing: the register never computes prices, tax or totals itself. */
  quote: (body: QuoteRequest) =>
    request<OrderQuote>('/orders/quote', { method: 'POST', body: JSON.stringify(body) }),

  createOrder: (body: CreateOrderRequest, idempotencyKey: string) =>
    request<Order>('/orders', {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Idempotency-Key': idempotencyKey },
    }),

  pay: (order: Pick<Order, 'id' | 'version'>, body: CreatePaymentRequest, idempotencyKey: string) =>
    request<Payment>(`/orders/${order.id}/payments`, {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': `"${order.version}"` },
    }),

  /** Manager-approved comp; returns the re-priced order. */
  comp: (order: Pick<Order, 'id' | 'version'>, body: CompRequest, idempotencyKey: string) =>
    request<Order>(`/orders/${order.id}/comps`, {
      method: 'POST',
      body: JSON.stringify(body),
      headers: { 'Idempotency-Key': idempotencyKey, 'If-Match': `"${order.version}"` },
    }),
};

/** A message a cashier can act on, for any error from the calls above. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "Can't reach the register API. Check the connection and try again.";
}
