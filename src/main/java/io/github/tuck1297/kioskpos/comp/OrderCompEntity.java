package io.github.tuck1297.kioskpos.comp;

import io.github.tuck1297.kioskpos.order.OrderEntity;
import io.github.tuck1297.kioskpos.staff.StaffEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One comp: the audit record of who approved taking how much off which order, and why. */
@Entity
@Table(name = "order_comp")
public class OrderCompEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private OrderEntity order;

    /** Null for a whole-order comp. */
    @Column(name = "order_line_id", updatable = false)
    private UUID orderLineId;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(updatable = false)
    private String note;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approved_by", nullable = false, updatable = false)
    private StaffEntity approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", updatable = false)
    private StaffEntity requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OrderCompEntity() {
        // for JPA
    }

    public OrderCompEntity(OrderEntity order, UUID orderLineId, String reason, String note, long amountCents,
                           StaffEntity approvedBy, StaffEntity requestedBy, OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.order = order;
        this.orderLineId = orderLineId;
        this.reason = reason;
        this.note = note;
        this.amountCents = amountCents;
        this.approvedBy = approvedBy;
        this.requestedBy = requestedBy;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderLineId() {
        return orderLineId;
    }

    public boolean isWholeOrder() {
        return orderLineId == null;
    }

    public String getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public StaffEntity getApprovedBy() {
        return approvedBy;
    }

    public StaffEntity getRequestedBy() {
        return requestedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
