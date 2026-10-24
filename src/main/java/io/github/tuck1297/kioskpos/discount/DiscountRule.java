package io.github.tuck1297.kioskpos.discount;

import io.github.tuck1297.kioskpos.common.ApiException;
import java.util.UUID;

/**
 * A discount that has been checked (code exists and is live, or employee ID is valid) and is
 * ready to price. Pure: {@link #amountOff} works out cents for a given subtotal.
 *
 * @param kind          CODE or EMPLOYEE
 * @param codeId        the discount_code row, for CODE
 * @param code          the promo code text, for CODE
 * @param employeeId    the staff row whose employee discount this is, for EMPLOYEE
 * @param employeeRef   that employee's employee ID and name, for EMPLOYEE
 * @param label         shown on the receipt and register, e.g. "PARKFAN10 · 10% off"
 * @param percentBps    for percentage discounts (1000 = 10%), else null
 * @param amountCents   for fixed-amount discounts, else null
 * @param minSubtotalCents the order must be at least this before the discount
 * @param capped        true when the code has a redemption limit (counted at order creation)
 */
public record DiscountRule(String kind, UUID codeId, String code, UUID employeeId, EmployeeRef employeeRef,
                           String label, Integer percentBps, Long amountCents, long minSubtotalCents,
                           boolean capped) {

    public record EmployeeRef(String employeeId, String name) {
    }

    public static final String CODE = "CODE";
    public static final String EMPLOYEE = "EMPLOYEE";

    /** Cents off this subtotal. Percentages round half up; never more than the subtotal. */
    public long amountOff(long subtotalCents) {
        if (subtotalCents < minSubtotalCents) {
            throw ApiException.discountNotApplicable(code + " needs an order of at least "
                    + dollars(minSubtotalCents) + " before discounts.");
        }
        long off = percentBps != null
                ? (Math.multiplyExact(subtotalCents, (long) percentBps) + 5_000L) / 10_000L
                : amountCents;
        return Math.min(off, subtotalCents);
    }

    static String dollars(long cents) {
        return "$" + (cents / 100) + "." + String.format("%02d", cents % 100);
    }
}
