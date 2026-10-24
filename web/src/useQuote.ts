import { useEffect, useRef, useState } from 'react';
import { api, describeError, type OrderQuote } from './api/client';
import { toLineRequests, type CartLine } from './cart';

export interface QuoteState {
  quote: OrderQuote | null;
  loading: boolean;
  error: string | null;
}

/**
 * Re-prices the cart on the server whenever it changes (debounced). Only the latest
 * response is kept, so a slow older quote can never overwrite a newer one.
 */
export function useQuote(locationId: string, cart: CartLine[]): QuoteState {
  const [state, setState] = useState<QuoteState>({ quote: null, loading: false, error: null });
  const latest = useRef(0);

  useEffect(() => {
    const requestNo = ++latest.current;
    if (cart.length === 0) {
      setState({ quote: null, loading: false, error: null });
      return;
    }
    setState((s) => ({ ...s, loading: true }));
    const timer = setTimeout(() => {
      api
        .quote({ locationId, lines: toLineRequests(cart) })
        .then((quote) => {
          if (requestNo === latest.current) setState({ quote, loading: false, error: null });
        })
        .catch((e: unknown) => {
          if (requestNo === latest.current) setState({ quote: null, loading: false, error: describeError(e) });
        });
    }, 200);
    return () => clearTimeout(timer);
  }, [locationId, cart]);

  return state;
}
