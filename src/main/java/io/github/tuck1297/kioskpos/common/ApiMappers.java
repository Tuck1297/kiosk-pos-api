package io.github.tuck1297.kioskpos.common;

import io.github.tuck1297.kioskpos.api.model.Location;
import io.github.tuck1297.kioskpos.api.model.MenuCategory;
import io.github.tuck1297.kioskpos.api.model.MenuItem;
import io.github.tuck1297.kioskpos.api.model.Money;
import io.github.tuck1297.kioskpos.api.model.TaxRate;
import io.github.tuck1297.kioskpos.location.LocationEntity;
import io.github.tuck1297.kioskpos.menu.MenuItemEntity;
import io.github.tuck1297.kioskpos.tax.TaxRateEntity;

/** Entity → generated API model conversions shared by several controllers and services. */
public final class ApiMappers {

    private ApiMappers() {
    }

    public static Money money(long cents, String currency) {
        return new Money().amountCents(cents).currency(currency);
    }

    public static TaxRate taxRate(TaxRateEntity e) {
        return new TaxRate()
                .id(e.getId())
                .code(e.getCode())
                .name(e.getName())
                .rateBps(e.getRateBps());
    }

    public static Location location(LocationEntity e) {
        return new Location()
                .id(e.getId())
                .name(e.getName())
                .parkCode(e.getParkCode())
                .timezone(e.getTimezone())
                .taxRate(taxRate(e.getTaxRate()))
                .currency(e.getCurrency())
                .active(e.isActive());
    }

    public static MenuItem menuItem(MenuItemEntity e, String currency) {
        return new MenuItem()
                .id(e.getId())
                .locationId(e.getLocationId())
                .sku(e.getSku())
                .name(e.getName())
                .description(e.getDescription())
                .category(MenuCategory.fromValue(e.getCategory()))
                .price(money(e.getPriceCents(), currency))
                .available(e.isAvailable())
                .updatedAt(e.getUpdatedAt());
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
