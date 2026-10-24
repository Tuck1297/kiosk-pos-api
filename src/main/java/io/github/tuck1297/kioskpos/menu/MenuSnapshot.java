package io.github.tuck1297.kioskpos.menu;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything needed to show a location's menu and price a basket there: the location,
 * its tax rate and its items. Immutable and serializable, so it can be cached in Redis
 * and shared by every API replica.
 */
public record MenuSnapshot(
        UUID locationId,
        String locationName,
        String parkCode,
        String timezone,
        boolean active,
        String currency,
        TaxSnapshot taxRate,
        List<ItemSnapshot> items
) implements Serializable {

    public record TaxSnapshot(UUID id, String code, String name, int rateBps) implements Serializable {
    }

    public record ItemSnapshot(UUID id, String sku, String name, String description, String category,
                               long priceCents, boolean available, OffsetDateTime updatedAt) implements Serializable {
    }

    public MenuSnapshot {
        items = List.copyOf(items);
    }

    public Optional<ItemSnapshot> item(UUID menuItemId) {
        return items.stream().filter(i -> i.id().equals(menuItemId)).findFirst();
    }
}
