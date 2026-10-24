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

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body) {
    headers.set('Content-Type', 'application/json');
  }
  const res = await fetch(`${BASE}/v1${path}`, { ...init, headers });
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
    throw new ApiError(res.status, body as Partial<Problem> | null);
  }
  return body as T;
}

export const api = {
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
};

/** A message a cashier can act on, for any error from the calls above. */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "Can't reach the register API. Check the connection and try again.";
}
