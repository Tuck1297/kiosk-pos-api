import type { OrderLineRequest } from './api/client';

/** What the cashier picked: ids and quantities only. Prices come from the server's quote. */
export interface CartLine {
  menuItemId: string;
  quantity: number;
}

export const MAX_QUANTITY = 20;

export function addItem(cart: CartLine[], menuItemId: string): CartLine[] {
  const existing = cart.find((l) => l.menuItemId === menuItemId);
  if (!existing) {
    return [...cart, { menuItemId, quantity: 1 }];
  }
  return changeQuantity(cart, menuItemId, 1);
}

export function changeQuantity(cart: CartLine[], menuItemId: string, delta: number): CartLine[] {
  return cart
    .map((l) =>
      l.menuItemId === menuItemId
        ? { ...l, quantity: Math.min(MAX_QUANTITY, l.quantity + delta) }
        : l,
    )
    .filter((l) => l.quantity > 0);
}

export function toLineRequests(cart: CartLine[]): OrderLineRequest[] {
  return cart.map((l) => ({ menuItemId: l.menuItemId, quantity: l.quantity }));
}
