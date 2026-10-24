package io.github.tuck1297.kioskpos.staff;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Checks an employee ID + PIN, for sign-in and for manager approvals.
 *
 * Built for many registers signing in at once (shift change):
 * <ul>
 *   <li>The bcrypt comparison (deliberately slow, ~60 ms) runs with no row lock held, so
 *       sign-ins for the same account never queue behind each other.</li>
 *   <li>Failed-attempt counting is one atomic UPDATE, so parallel wrong guesses are all
 *       counted, and a successful sign-in writes nothing unless there is a count to reset.</li>
 *   <li>Counter updates commit in their own transaction: a failed attempt stays counted
 *       even though the request that made it fails and rolls back.</li>
 * </ul>
 * Returns an outcome rather than throwing, so callers decide the HTTP error.
 */
@Component
public class StaffAuthenticator {

    public enum Outcome { OK, BAD_CREDENTIALS, LOCKED }

    public record Result(Outcome outcome, UUID staffId, String employeeId, String name, String role,
                         OffsetDateTime lockedUntil) {

        static Result ok(StaffEntity s) {
            return new Result(Outcome.OK, s.getId(), s.getEmployeeId(), s.getName(), s.getRole(), null);
        }

        static Result bad() {
            return new Result(Outcome.BAD_CREDENTIALS, null, null, null, null, null);
        }

        static Result locked(OffsetDateTime until) {
            return new Result(Outcome.LOCKED, null, null, null, null, until);
        }

        public boolean isManager() {
            return StaffEntity.MANAGER.equals(role);
        }
    }

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final int LOCK_MINUTES = 15;

    private final StaffRepository staff;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    /** Compared against when the employee ID is unknown, so response time doesn't reveal valid IDs. */
    private final String dummyHash = encoder.encode("000000");

    public StaffAuthenticator(StaffRepository staff, JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.staff = staff;
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Result verify(String employeeId, String pin) {
        Optional<StaffEntity> found = staff.findByEmployeeId(employeeId);
        if (found.isEmpty() || !found.get().isActive()) {
            encoder.matches(pin, dummyHash);
            return Result.bad();
        }
        StaffEntity s = found.get();
        OffsetDateTime now = OffsetDateTime.now();
        if (s.isLocked(now)) {
            return Result.locked(s.getLockedUntil());
        }

        if (encoder.matches(pin, s.getPinHash())) {
            if (s.getFailedAttempts() > 0 || s.getLockedUntil() != null) {
                ownTransaction.executeWithoutResult(tx -> jdbc.update(
                        "UPDATE staff SET failed_attempts = 0, locked_until = NULL, updated_at = now() WHERE id = ?",
                        s.getId()));
            }
            return Result.ok(s);
        }

        // Count the failure; the fifth in a row locks the account (and restarts the count).
        OffsetDateTime lockedUntil = ownTransaction.execute(tx -> jdbc.query("""
                UPDATE staff SET
                    locked_until = CASE WHEN failed_attempts + 1 >= ? THEN now() + make_interval(mins => ?)
                                        ELSE locked_until END,
                    failed_attempts = CASE WHEN failed_attempts + 1 >= ? THEN 0 ELSE failed_attempts + 1 END,
                    updated_at = now()
                WHERE id = ?
                RETURNING locked_until""",
                rs -> rs.next() ? rs.getObject(1, OffsetDateTime.class) : null,
                MAX_FAILED_ATTEMPTS, LOCK_MINUTES, MAX_FAILED_ATTEMPTS, s.getId()));
        return lockedUntil != null && lockedUntil.isAfter(now) ? Result.locked(lockedUntil) : Result.bad();
    }
}
