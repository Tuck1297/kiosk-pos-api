package io.github.tuck1297.kioskpos.menu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "menu_item")
public class MenuItemEntity {

    @Id
    private UUID id;

    @Column(name = "location_id", nullable = false)
    private UUID locationId;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String description;

    /** One of the MenuCategory values in openapi.yaml. */
    @Column(nullable = false)
    private String category;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(nullable = false)
    private boolean available;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MenuItemEntity() {
        // for JPA
    }

    public UUID getId() {
        return id;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public boolean isAvailable() {
        return available;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
