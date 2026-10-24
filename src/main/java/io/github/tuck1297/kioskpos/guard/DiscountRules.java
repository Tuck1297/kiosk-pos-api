package io.github.tuck1297.kioskpos.guard;

import io.github.tuck1297.kioskpos.api.model.DiscountRequest;
import org.springframework.validation.Errors;

/** A discount is either a promo code or an employee ID, never both and never neither. */
final class DiscountRules {

    private DiscountRules() {
    }

    static void check(DiscountRequest discount, Errors errors) {
        if (discount == null) {
            return; // no discount is fine
        }
        boolean hasCode = discount.getCode() != null && !discount.getCode().isBlank();
        boolean hasEmployee = discount.getEmployeeId() != null && !discount.getEmployeeId().isBlank();
        if (hasCode == hasEmployee) {
            errors.rejectValue("discount", "one_of", "needs exactly one of code or employeeId");
        }
    }
}
