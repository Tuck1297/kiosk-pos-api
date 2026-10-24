package io.github.tuck1297.kioskpos.common;

import io.github.tuck1297.kioskpos.api.model.Location;
import io.github.tuck1297.kioskpos.api.model.MenuCategory;
import io.github.tuck1297.kioskpos.api.model.MenuItem;
import io.github.tuck1297.kioskpos.api.model.Money;
import io.github.tuck1297.kioskpos.api.model.StaffRef;
import io.github.tuck1297.kioskpos.staff.StaffEntity;
import io.github.tuck1297.kioskpos.api.model.TaxRate;
import io.github.tuck1297.kioskpos.location.LocationEntity;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot;
import io.github.tuck1297.kioskpos.tax.TaxRateEntity;
import java.util.UUID;

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

    public static TaxRate taxRate(MenuSnapshot.TaxSnapshot t) {
        return new TaxRate()
                .id(t.id())
                .code(t.code())
                .name(t.name())
                .rateBps(t.rateBps());
    }

    public static Location location(MenuSnapshot m) {
        return new Location()
                .id(m.locationId())
                .name(m.locationName())
                .parkCode(m.parkCode())
                .timezone(m.timezone())
                .taxRate(taxRate(m.taxRate()))
                .currency(m.currency())
                .active(m.active());
    }

    public static MenuItem menuItem(MenuSnapshot.ItemSnapshot i, UUID locationId, String currency) {
        return new MenuItem()
                .id(i.id())
                .locationId(locationId)
                .sku(i.sku())
                .name(i.name())
                .description(i.description())
                .category(MenuCategory.fromValue(i.category()))
                .price(money(i.priceCents(), currency))
                .available(i.available())
                .updatedAt(i.updatedAt());
    }

    public static StaffRef staffRef(StaffEntity s) {
        return s == null ? null : new StaffRef().employeeId(s.getEmployeeId()).name(s.getName());
    }

    public static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
