package io.github.tuck1297.kioskpos.order;

import io.github.tuck1297.kioskpos.comp.OrderCompEntity;
import io.github.tuck1297.kioskpos.discount.DiscountCodeEntity;
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

    /** Discount snapshot, set once at creation: later changes to a code never alter it. */
    @Column(name = "discount_cents", nullable = false, updatable = false)
    private long discountCents;

    @Column(name = "discount_kind", updatable = false)
    private String discountKind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_code_id", updatable = false)
    private DiscountCodeEntity discountCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_employee_id", updatable = false)
    private StaffEntity discountEmployee;

    @Column(name = "discount_label", updatable = false)
    private String discountLabel;

    @Column(name = "discount_rate_bps", updatable = false)
    private Integer discountRateBps;

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

    /** Records the discount the order was priced with. Only before the order is first saved. */
    public void setDiscount(String kind, DiscountCodeEntity code, StaffEntity employee, String label, Integer rateBps,
                            long cents) {
        this.discountKind = kind;
        this.discountCode = code;
        this.discountEmployee = employee;
        this.discountLabel = label;
        this.discountRateBps = rateBps;
        this.discountCents = cents;
    }

    /** What a whole-order comp would take off: everything still owed before tax. */
    public long uncompedNetCents() {
        return subtotalCents - discountCents - compCents;
    }

    /**
     * What comping one line takes off: the line's price less its share of the order discount
     * (so a 10% discount followed by comping an item doesn't refund more than the guest paid
     * for it), and never more than what's left.
     */
    public long lineCompAmountCents(OrderLineEntity line) {
        long discountShare = subtotalCents == 0 ? 0 : discountCents * line.getLineTotalCents() / subtotalCents;
        return Math.min(line.getLineTotalCents() - discountShare, uncompedNetCents());
    }

    /**
     * Records a comp and re-prices: tax is charged only on what's left after the discount and
     * comps, using the rate this order was priced with. An order comped down to $0.00 has
     * nothing left to collect, so it is closed as PAID.
     */
    public void applyComp(OrderCompEntity comp, OffsetDateTime now) {
        comps.add(comp);
        compCents = Math.addExact(compCents, comp.getAmountCents());
        long taxable = subtotalCents - discountCents - compCents;
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

    public long getDiscountCents() {
        return discountCents;
    }

    public String getDiscountKind() {
        return discountKind;
    }

    public DiscountCodeEntity getDiscountCode() {
        return discountCode;
    }

    public StaffEntity getDiscountEmployee() {
        return discountEmployee;
    }

    public String getDiscountLabel() {
        return discountLabel;
    }

    public Integer getDiscountRateBps() {
        return discountRateBps;
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
