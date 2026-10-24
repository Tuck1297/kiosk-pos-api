package io.github.tuck1297.kioskpos.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** One line of an order. sku, name and unit price are snapshots taken when the order was created. */
@Entity
@Table(name = "order_line")
public class OrderLineEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private OrderEntity order;

    @Column(name = "line_no", nullable = false, updatable = false)
    private short lineNo;

    @Column(name = "menu_item_id", nullable = false, updatable = false)
    private UUID menuItemId;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(name = "unit_price_cents", nullable = false, updatable = false)
    private long unitPriceCents;

    @Column(nullable = false, updatable = false)
    private int quantity;

    @Column(name = "line_total_cents", nullable = false, updatable = false)
    private long lineTotalCents;

    @Column(updatable = false)
    private String note;

    protected OrderLineEntity() {
        // for JPA
    }

    public OrderLineEntity(UUID id, short lineNo, UUID menuItemId, String sku, String name,
                           long unitPriceCents, int quantity, long lineTotalCents, String note) {
        this.id = id;
        this.lineNo = lineNo;
        this.menuItemId = menuItemId;
        this.sku = sku;
        this.name = name;
        this.unitPriceCents = unitPriceCents;
        this.quantity = quantity;
        this.lineTotalCents = lineTotalCents;
        this.note = note;
    }

    void setOrder(OrderEntity order) {
        this.order = order;
    }

    public UUID getId() {
        return id;
    }

    public short getLineNo() {
        return lineNo;
    }

    public UUID getMenuItemId() {
        return menuItemId;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public long getUnitPriceCents() {
        return unitPriceCents;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getLineTotalCents() {
        return lineTotalCents;
    }

    public String getNote() {
        return note;
    }
}
