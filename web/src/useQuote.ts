import { useEffect, useRef, useState } from 'react';
import { ApiError, api, describeError, type DiscountRequest, type OrderQuote } from './api/client';
import { toLineRequests, type CartLine } from './cart';

export interface QuoteState {
  quote: OrderQuote | null;
  loading: boolean;
  error: string | null;
  /** The problem `code`, e.g. DISCOUNT_NOT_APPLICABLE, when the server answered with one. */
  errorCode: string | null;
}

/**
 * Re-prices the cart (and any discount) on the server whenever either changes (debounced).
 * Only the latest response is kept, so a slow older quote can never overwrite a newer one.
 */
export function useQuote(locationId: string, cart: CartLine[], discount: DiscountRequest | null): QuoteState {
  const [state, setState] = useState<QuoteState>({ quote: null, loading: false, error: null, errorCode: null });
  const latest = useRef(0);

  useEffect(() => {
    const requestNo = ++latest.current;
    if (cart.length === 0) {
      setState({ quote: null, loading: false, error: null, errorCode: null });
      return;
    }
    setState((s) => ({ ...s, loading: true }));
    const timer = setTimeout(() => {
      api
        .quote({ locationId, lines: toLineRequests(cart), discount: discount ?? undefined })
        .then((quote) => {
          if (requestNo === latest.current) setState({ quote, loading: false, error: null, errorCode: null });
        })
        .catch((e: unknown) => {
          if (requestNo === latest.current) {
            setState({
              quote: null,
              loading: false,
              error: describeError(e),
              errorCode: e instanceof ApiError ? e.code ?? null : null,
            });
          }
        });
    }, 200);
    return () => clearTimeout(timer);
  }, [locationId, cart, discount]);

  return state;
}
