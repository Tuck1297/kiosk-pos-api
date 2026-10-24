import type { Money } from './api/client';

/** Display only. Amounts always come from the server in cents. */
export function formatMoney(money: Money): string {
  return formatCents(money.amountCents, money.currency);
}

export function formatCents(cents: number, currency = 'USD'): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100);
}

export function formatRate(rateBps: number): string {
  return `${(rateBps / 100).toFixed(2)}%`;
}

/**
 * One-tap cash amounts for a total: exact, then the next dollar, $5, $10 and $20 up,
 * then common bills. Only amounts that cover the total, smallest first.
 */
export function quickCashAmounts(totalCents: number): number[] {
  const roundUp = (step: number) => Math.ceil(totalCents / step) * step;
  const candidates = [totalCents, roundUp(100), roundUp(500), roundUp(1000), roundUp(2000), 5000, 10000];
  return [...new Set(candidates)]
    .filter((c) => c >= totalCents)
    .sort((a, b) => a - b)
    .slice(0, 5);
}
