package io.github.tuck1297.kioskpos.discount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A promo code: a percentage or a fixed amount off, with optional minimum, dates and cap. */
@Entity
@Table(name = "discount_code")
public class DiscountCodeEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String description;

    /** PERCENT or AMOUNT. */
    @Column(nullable = false)
    private String kind;

    @Column(name = "percent_bps")
    private Integer percentBps;

    @Column(name = "amount_cents")
    private Long amountCents;

    @Column(name = "min_subtotal_cents", nullable = false)
    private long minSubtotalCents;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "times_redeemed", nullable = false)
    private int timesRedeemed;

    @Column(nullable = false)
    private boolean active;

    protected DiscountCodeEntity() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPercent() {
        return "PERCENT".equals(kind);
    }

    public Integer getPercentBps() {
        return percentBps;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public long getMinSubtotalCents() {
        return minSubtotalCents;
    }

    public OffsetDateTime getStartsAt() {
        return startsAt;
    }

    public OffsetDateTime getEndsAt() {
        return endsAt;
    }

    public Integer getMaxRedemptions() {
        return maxRedemptions;
    }

    public int getTimesRedeemed() {
        return timesRedeemed;
    }

    public boolean isActive() {
        return active;
    }
}
