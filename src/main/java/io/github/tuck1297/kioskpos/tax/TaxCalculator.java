package io.github.tuck1297.kioskpos.tax;

/**
 * Sales tax on an order subtotal, in whole cents. Integer math only: no floating point
 * ever touches money. Rounds half up (e.g. 97.5 cents becomes 98).
 */
public final class TaxCalculator {

    private static final long BPS_PER_UNIT = 10_000L;

    private TaxCalculator() {
    }

    public static long taxCents(long subtotalCents, int rateBps) {
        if (subtotalCents < 0) {
            throw new IllegalArgumentException("subtotalCents must not be negative");
        }
        if (rateBps < 0) {
            throw new IllegalArgumentException("rateBps must not be negative");
        }
        long scaled = Math.multiplyExact(subtotalCents, (long) rateBps);
        return (scaled + BPS_PER_UNIT / 2) / BPS_PER_UNIT;
    }
}
