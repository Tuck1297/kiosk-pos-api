package io.github.tuck1297.kioskpos.order;

import io.github.tuck1297.kioskpos.comp.OrderCompEntity;
import io.github.tuck1297.kioskpos.staff.StaffEntity;
import io.github.tuck1297.kioskpos.tax.TaxCalculator;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Maps customer_order ("order" is a reserved word in SQL). */
@Entity
@Table(name = "customer_order")
public class OrderEntity {

    public static final String OPEN = "OPEN";
    public static final String PAID = "PAID";

    @Id
    private UUID id;

    @Column(name = "location_id", nullable = false, updatable = false)
    private UUID locationId;

    @Column(name = "order_number", nullable = false, updatable = false)
    private int orderNumber;

    @Column(nullable = false, updatable = false)
    private String channel;

    @Column(name = "guest_name")
    private String guestName;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "subtotal_cents", nullable = false, updatable = false)
    private long subtotalCents;

    @Column(name = "tax_rate_bps", nullable = false, updatable = false)
    private int taxRateBps;

    /** Recomputed when a comp lowers the taxable amount. */
    @Column(name = "tax_cents", nullable = false)
    private long taxCents;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Column(name = "comp_cents", nullable = false)
    private long compCents;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private StaffEntity createdBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    /** Optimistic lock: exposed as the ETag and required back in If-Match. */
    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<OrderLineEntity> lines = new ArrayList<>();

    /** Inserted through {@link #applyComp}; never updated or removed. */
    @OneToMany(mappedBy = "order")
    @OrderBy("createdAt ASC")
    private List<OrderCompEntity> comps = new ArrayList<>();

    protected OrderEntity() {
        // for JPA
    }

    public OrderEntity(UUID id, UUID locationId, int orderNumber, String channel, String guestName, String currency,
                       long subtotalCents, int taxRateBps, long taxCents, long totalCents, StaffEntity createdBy,
                       OffsetDateTime now) {
        this.id = id;
        this.createdBy = createdBy;
        this.locationId = locationId;
        this.orderNumber = orderNumber;
        this.channel = channel;
        this.guestName = guestName;
        this.status = OPEN;
        this.currency = currency;
        this.subtotalCents = subtotalCents;
        this.taxRateBps = taxRateBps;
        this.taxCents = taxCents;
        this.totalCents = totalCents;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void addLine(OrderLineEntity line) {
        line.setOrder(this);
        lines.add(line);
    }

    public void markPaid(OffsetDateTime now) {
        this.status = PAID;
        this.updatedAt = now;
    }

    public Optional<OrderLineEntity> line(UUID lineId) {
        return lines.stream().filter(l -> l.getId().equals(lineId)).findFirst();
    }

    public boolean isLineComped(UUID lineId) {
        return comps.stream().anyMatch(c -> lineId.equals(c.getOrderLineId()));
    }

    public boolean hasWholeOrderComp() {
        return comps.stream().anyMatch(OrderCompEntity::isWholeOrder);
    }

    /** What a whole-order comp would take off: everything not already comped. */
    public long uncompedSubtotalCents() {
        return subtotalCents - compCents;
    }

    /**
     * Records a comp and re-prices: tax is charged only on what's left after comps, using
     * the rate this order was priced with. An order comped down to $0.00 has nothing left
     * to collect, so it is closed as PAID.
     */
    public void applyComp(OrderCompEntity comp, OffsetDateTime now) {
        comps.add(comp);
        compCents = Math.addExact(compCents, comp.getAmountCents());
        long taxable = subtotalCents - compCents;
        taxCents = TaxCalculator.taxCents(taxable, taxRateBps);
        totalCents = taxable + taxCents;
        if (totalCents == 0) {
            status = PAID;
        }
        updatedAt = now;
    }

    public long getCompCents() {
        return compCents;
    }

    public StaffEntity getCreatedBy() {
        return createdBy;
    }

    public List<OrderCompEntity> getComps() {
        return comps;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public int getOrderNumber() {
        return orderNumber;
    }

    public String getChannel() {
        return channel;
    }

    public String getGuestName() {
        return guestName;
    }

    public String getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public long getSubtotalCents() {
        return subtotalCents;
    }

    public int getTaxRateBps() {
        return taxRateBps;
    }

    public long getTaxCents() {
        return taxCents;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public Integer getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderLineEntity> getLines() {
        return lines;
    }
}
