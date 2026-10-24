package io.github.tuck1297.kioskpos.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "location")
public class LocationEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "park_code", nullable = false)
    private String parkCode;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "tax_rate_bps", nullable = false)
    private int taxRateBps;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected LocationEntity() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getParkCode() {
        return parkCode;
    }

    public String getTimezone() {
        return timezone;
    }

    public int getTaxRateBps() {
        return taxRateBps;
    }

    public String getCurrency() {
        return currency;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
