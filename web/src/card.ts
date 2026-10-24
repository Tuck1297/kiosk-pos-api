import type { components } from './api/schema';

export type CardBrand = components['schemas']['CardBrand'];

/**
 * Card handling for the demo terminal.
 *
 * A real register never sees a card number: the payment terminal (or the processor's hosted
 * fields, for keyed entry) reads the card and hands back a single-use token. This module plays
 * that part in the browser. The number lives only in the key-in form's state, gets turned
 * into a token here, and only the token goes to the API, which the mock processor approves or
 * declines by its shape (`tok_<brand>_<last4>`, `tok_decline_<code>_<last4>`).
 */

export interface PresentedCard {
  token: string;
  brand: CardBrand | null;
  last4: string;
}

export const BRAND_LABELS: Record<CardBrand, string> = {
  VISA: 'Visa',
  MASTERCARD: 'Mastercard',
  AMEX: 'American Express',
  DISCOVER: 'Discover',
};

export function describeCard(brand: CardBrand | null | undefined, last4: string | null | undefined): string {
  const name = brand ? BRAND_LABELS[brand] : 'Card';
  return last4 ? `${name} ending ${last4}` : name;
}

/** Cards a guest might hand over, for the demo wallet. */
export interface WalletCard extends PresentedCard {
  label: string;
  /** What the processor will do with it. */
  outcome: string;
  color: string;
}

export const WALLET: WalletCard[] = [
  { label: 'Visa', brand: 'VISA', last4: '4242', token: 'tok_visa_4242', outcome: 'Approves', color: '#1a1f71' },
  { label: 'Mastercard', brand: 'MASTERCARD', last4: '4444', token: 'tok_mastercard_4444', outcome: 'Approves', color: '#3b3b3b' },
  { label: 'Amex', brand: 'AMEX', last4: '0005', token: 'tok_amex_0005', outcome: 'Approves', color: '#2e77bb' },
  { label: 'Visa', brand: 'VISA', last4: '0002', token: 'tok_decline_card_declined_0002', outcome: 'Declines', color: '#7a1f2b' },
  { label: 'Visa', brand: 'VISA', last4: '9995', token: 'tok_decline_insufficient_funds_9995', outcome: 'Insufficient funds', color: '#5c3a00' },
];

/** Test numbers that the demo processor declines, keyed by the full number. */
const DECLINING_NUMBERS: Record<string, string> = {
  '4000000000000002': 'card_declined',
  '4000000000009995': 'insufficient_funds',
  '4000000000000069': 'expired_card',
};

export function digitsOnly(value: string): string {
  return value.replace(/\D/g, '');
}

export function detectBrand(number: string): CardBrand | null {
  const n = digitsOnly(number);
  if (/^4/.test(n)) return 'VISA';
  if (/^3[47]/.test(n)) return 'AMEX';
  const first4 = Number(n.slice(0, 4));
  if (/^5[1-5]/.test(n) || (n.length >= 4 && first4 >= 2221 && first4 <= 2720)) return 'MASTERCARD';
  if (/^(6011|65|64[4-9])/.test(n)) return 'DISCOVER';
  return null;
}

export function luhnValid(number: string): boolean {
  const n = digitsOnly(number);
  if (n.length < 12 || n.length > 19) return false;
  let sum = 0;
  for (let i = 0; i < n.length; i++) {
    let d = Number(n[n.length - 1 - i]);
    if (i % 2 === 1) {
      d *= 2;
      if (d > 9) d -= 9;
    }
    sum += d;
  }
  return sum % 10 === 0;
}

/** Groups digits the way they're printed on the card: 4-6-5 for Amex, 4-4-4-4 otherwise. */
export function formatCardNumber(value: string): string {
  const n = digitsOnly(value).slice(0, 19);
  const groups = detectBrand(n) === 'AMEX' ? [4, 6, 5] : [4, 4, 4, 4, 3];
  const parts: string[] = [];
  let at = 0;
  for (const size of groups) {
    if (at >= n.length) break;
    parts.push(n.slice(at, at + size));
    at += size;
  }
  return parts.join(' ');
}

export function formatExpiry(value: string): string {
  const n = digitsOnly(value).slice(0, 4);
  return n.length > 2 ? `${n.slice(0, 2)}/${n.slice(2)}` : n;
}

export interface KeyedCard {
  number: string;
  expiry: string;
  cvc: string;
}

export type KeyedCardErrors = Partial<Record<keyof KeyedCard, string>>;

export function validateKeyedCard(card: KeyedCard, now = new Date()): KeyedCardErrors {
  const errors: KeyedCardErrors = {};
  const brand = detectBrand(card.number);

  if (!brand) {
    errors.number = 'Enter a Visa, Mastercard, American Express or Discover number';
  } else if (!luhnValid(card.number)) {
    errors.number = "That card number isn't valid. Check the digits";
  }

  const exp = /^(\d{2})\/(\d{2})$/.exec(card.expiry);
  if (!exp) {
    errors.expiry = 'Use MM/YY';
  } else {
    const month = Number(exp[1]);
    const year = 2000 + Number(exp[2]);
    if (month < 1 || month > 12) {
      errors.expiry = 'Month must be 01 to 12';
    } else if (year < now.getFullYear() || (year === now.getFullYear() && month < now.getMonth() + 1)) {
      errors.expiry = 'This card has expired';
    }
  }

  const cvcLength = brand === 'AMEX' ? 4 : 3;
  if (digitsOnly(card.cvc).length !== cvcLength) {
    errors.cvc = `${cvcLength} digits`;
  }
  return errors;
}

/**
 * Turns a keyed card into a processor token. Call only after validateKeyedCard passes; the
 * card number goes no further than this function.
 */
export function tokenizeCard(card: KeyedCard): PresentedCard {
  const number = digitsOnly(card.number);
  const brand = detectBrand(number);
  const last4 = number.slice(-4);
  const decline = DECLINING_NUMBERS[number];
  const token = decline ? `tok_decline_${decline}_${last4}` : `tok_${(brand ?? 'card').toLowerCase()}_${last4}`;
  return { token, brand, last4 };
}
