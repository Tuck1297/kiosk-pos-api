package io.github.tuck1297.kioskpos.pricing;

import static io.github.tuck1297.kioskpos.common.ApiMappers.blankToNull;

import io.github.tuck1297.kioskpos.api.model.OrderLineRequest;
import io.github.tuck1297.kioskpos.common.ApiException;
import io.github.tuck1297.kioskpos.common.NotFoundException;
import io.github.tuck1297.kioskpos.location.LocationEntity;
import io.github.tuck1297.kioskpos.location.LocationRepository;
import io.github.tuck1297.kioskpos.menu.MenuItemEntity;
import io.github.tuck1297.kioskpos.menu.MenuItemRepository;
import io.github.tuck1297.kioskpos.tax.TaxCalculator;
import io.github.tuck1297.kioskpos.tax.TaxRateEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place prices, line totals, tax and totals are computed. Used by both
 * POST /orders/quote and POST /orders, so the register can never send its own numbers.
 */
@Service
public class PricingService {

    public record PricedLine(MenuItemEntity item, int quantity, String note, long unitPriceCents, long lineTotalCents) {
    }

    public record PricedOrder(LocationEntity location, List<PricedLine> lines, long subtotalCents,
                              TaxRateEntity taxRate, long taxCents, long totalCents) {

        public String currency() {
            return location.getCurrency();
        }
    }

    private final LocationRepository locations;
    private final MenuItemRepository menuItems;

    public PricingService(LocationRepository locations, MenuItemRepository menuItems) {
        this.locations = locations;
        this.menuItems = menuItems;
    }

    @Transactional(readOnly = true)
    public PricedOrder price(UUID locationId, List<OrderLineRequest> lines) {
        LocationEntity location = locations.findById(locationId)
                .orElseThrow(() -> new NotFoundException("Location", locationId));
        if (!location.isActive()) {
            throw ApiException.locationClosed(location.getName());
        }

        Set<UUID> ids = lines.stream().map(OrderLineRequest::getMenuItemId).collect(Collectors.toSet());
        Map<UUID, MenuItemEntity> items = menuItems.findAllById(ids).stream()
                .collect(Collectors.toMap(MenuItemEntity::getId, Function.identity()));

        List<PricedLine> priced = new ArrayList<>(lines.size());
        long subtotal = 0;
        for (OrderLineRequest line : lines) {
            MenuItemEntity item = items.get(line.getMenuItemId());
            if (item == null || !item.getLocationId().equals(locationId)) {
                throw ApiException.itemUnavailable(
                        "Menu item " + line.getMenuItemId() + " is not sold at " + location.getName() + ".")
                        .with("menuItemId", line.getMenuItemId().toString());
            }
            if (!item.isAvailable()) {
                throw ApiException.itemUnavailable(item.getName() + " is sold out.")
                        .with("menuItemId", item.getId().toString());
            }
            int quantity = line.getQuantity();
            long lineTotal = Math.multiplyExact(item.getPriceCents(), (long) quantity);
            subtotal = Math.addExact(subtotal, lineTotal);
            priced.add(new PricedLine(item, quantity, blankToNull(line.getNote()), item.getPriceCents(), lineTotal));
        }

        TaxRateEntity taxRate = location.getTaxRate();
        long tax = TaxCalculator.taxCents(subtotal, taxRate.getRateBps());
        return new PricedOrder(location, List.copyOf(priced), subtotal, taxRate, tax, Math.addExact(subtotal, tax));
    }
}
