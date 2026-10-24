package io.github.tuck1297.kioskpos.discount;

import io.github.tuck1297.kioskpos.api.model.DiscountRequest;
import io.github.tuck1297.kioskpos.common.ApiException;
import io.github.tuck1297.kioskpos.staff.StaffEntity;
import io.github.tuck1297.kioskpos.staff.StaffRepository;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns the register's discount request into a {@link DiscountRule}, or explains why it
 * doesn't apply. Always reads Postgres (codes change, expire and run out), for quotes as well
 * as orders. Which of code / employee ID was sent was already checked by the request guards.
 */
@Service
public class DiscountResolver {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final DiscountCodeRepository codes;
    private final StaffRepository staff;
    private final JdbcTemplate jdbc;
    private final int employeeRateBps;

    public DiscountResolver(DiscountCodeRepository codes, StaffRepository staff, JdbcTemplate jdbc,
                            @Value("${kiosk.discounts.employee-rate-bps:2000}") int employeeRateBps) {
        this.codes = codes;
        this.staff = staff;
        this.jdbc = jdbc;
        this.employeeRateBps = employeeRateBps;
    }

    @Transactional(readOnly = true)
    public Optional<DiscountRule> resolve(DiscountRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        if (request.getEmployeeId() != null) {
            return Optional.of(employee(request.getEmployeeId()));
        }
        return Optional.of(code(request.getCode().toUpperCase(Locale.ROOT)));
    }

    /**
     * Counts one use of a capped code, inside the order's transaction. Atomic, so two registers
     * can't both take the last redemption; rolled back with the order if it fails.
     */
    public void redeem(DiscountRule rule) {
        if (!rule.capped()) {
            return;
        }
        int updated = jdbc.update("""
                UPDATE discount_code SET times_redeemed = times_redeemed + 1
                WHERE id = ? AND (max_redemptions IS NULL OR times_redeemed < max_redemptions)""", rule.codeId());
        if (updated == 0) {
            throw ApiException.discountNotApplicable(rule.code() + " has reached its redemption limit.");
        }
    }

    private DiscountRule employee(String employeeId) {
        StaffEntity s = staff.findByEmployeeId(employeeId)
                .filter(StaffEntity::isActive)
                .orElseThrow(() -> ApiException.discountNotApplicable(
                        "Employee ID " + employeeId + " isn't recognized for the employee discount."));
        String label = "Employee discount · " + percent(employeeRateBps) + " (" + s.getName() + ")";
        return new DiscountRule(DiscountRule.EMPLOYEE, null, null, s.getId(),
                new DiscountRule.EmployeeRef(s.getEmployeeId(), s.getName()), label, employeeRateBps, null, 0, false);
    }

    private DiscountRule code(String code) {
        DiscountCodeEntity c = codes.findByCode(code)
                .filter(DiscountCodeEntity::isActive)
                .orElseThrow(() -> ApiException.discountNotApplicable(code + " isn't a valid code."));
        OffsetDateTime now = OffsetDateTime.now();
        if (c.getStartsAt() != null && now.isBefore(c.getStartsAt())) {
            throw ApiException.discountNotApplicable(code + " starts " + DATE.format(c.getStartsAt()) + ".");
        }
        if (c.getEndsAt() != null && !now.isBefore(c.getEndsAt())) {
            throw ApiException.discountNotApplicable(code + " expired on " + DATE.format(c.getEndsAt()) + ".");
        }
        if (c.getMaxRedemptions() != null && c.getTimesRedeemed() >= c.getMaxRedemptions()) {
            throw ApiException.discountNotApplicable(code + " has reached its redemption limit.");
        }
        String off = c.isPercent() ? percent(c.getPercentBps()) + " off" : DiscountRule.dollars(c.getAmountCents()) + " off";
        return new DiscountRule(DiscountRule.CODE, c.getId(), c.getCode(), null, null, c.getCode() + " · " + off,
                c.getPercentBps(), c.getAmountCents(), c.getMinSubtotalCents(), c.getMaxRedemptions() != null);
    }

    private static String percent(int bps) {
        return bps % 100 == 0 ? (bps / 100) + "%" : String.format(Locale.US, "%.2f%%", bps / 100.0);
    }
}
