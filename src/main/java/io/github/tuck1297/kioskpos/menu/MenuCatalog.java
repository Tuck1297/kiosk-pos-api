package io.github.tuck1297.kioskpos.menu;

import io.github.tuck1297.kioskpos.common.NotFoundException;
import io.github.tuck1297.kioskpos.location.LocationEntity;
import io.github.tuck1297.kioskpos.location.LocationRepository;
import io.github.tuck1297.kioskpos.tax.TaxRateEntity;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Source of {@link MenuSnapshot}s, with two read paths on purpose:
 *
 * <ul>
 *   <li>{@link #cached} for reads that only display or estimate (menu, location, quote).
 *       Served from Redis, so hundreds of registers re-pricing carts never touch Postgres.
 *       May be up to the cache TTL out of date.</li>
 *   <li>{@link #fresh} for writes that take money (creating an order). Always reads the
 *       database inside the caller's transaction, so a stale cache can never put a sold-out
 *       item or an old price on a real order.</li>
 * </ul>
 */
@Service
public class MenuCatalog {

    public static final String CACHE = "menu";

    private final LocationRepository locations;
    private final MenuItemRepository menuItems;

    public MenuCatalog(LocationRepository locations, MenuItemRepository menuItems) {
        this.locations = locations;
        this.menuItems = menuItems;
    }

    @Cacheable(cacheNames = CACHE, key = "#p0")
    @Transactional(readOnly = true)
    public MenuSnapshot cached(UUID locationId) {
        return load(locationId);
    }

    @Transactional(readOnly = true)
    public MenuSnapshot fresh(UUID locationId) {
        return load(locationId);
    }

    /** Call after anything changes a location's menu, prices or tax rate. */
    @CacheEvict(cacheNames = CACHE, key = "#p0")
    public void evict(UUID locationId) {
        // the annotation does the work
    }

    private MenuSnapshot load(UUID locationId) {
        LocationEntity location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location", locationId));
        TaxRateEntity tax = location.getTaxRate();
        return new MenuSnapshot(
                location.getId(),
                location.getName(),
                location.getParkCode(),
                location.getTimezone(),
                location.isActive(),
                location.getCurrency(),
                new MenuSnapshot.TaxSnapshot(tax.getId(), tax.getCode(), tax.getName(), tax.getRateBps()),
                menuItems.findByLocationId(locationId).stream()
                        .map(i -> new MenuSnapshot.ItemSnapshot(i.getId(), i.getSku(), i.getName(), i.getDescription(),
                                i.getCategory(), i.getPriceCents(), i.isAvailable(), i.getUpdatedAt()))
                        .toList());
    }
}
