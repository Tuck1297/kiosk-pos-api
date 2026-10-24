package io.github.tuck1297.kioskpos.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A cashier or manager. The PIN is only ever stored as a bcrypt hash. */
@Entity
@Table(name = "staff")
public class StaffEntity {

    public static final String MANAGER = "MANAGER";

    @Id
    private UUID id;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private String employeeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String role;

    @Column(name = "pin_hash", nullable = false)
    private String pinHash;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    protected StaffEntity() {
        // for JPA
    }

    public boolean isLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Counters are changed only by StaffAuthenticator's atomic UPDATEs, never via this entity. */
    public int getFailedAttempts() {
        return failedAttempts;
    }

    public UUID getId() {
        return id;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public boolean isManager() {
        return MANAGER.equals(role);
    }

    public String getPinHash() {
        return pinHash;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }
}
