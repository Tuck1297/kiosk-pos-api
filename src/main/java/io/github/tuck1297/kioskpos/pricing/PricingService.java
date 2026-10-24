package io.github.tuck1297.kioskpos.pricing;

import static io.github.tuck1297.kioskpos.common.ApiMappers.blankToNull;

import io.github.tuck1297.kioskpos.api.model.OrderLineRequest;
import io.github.tuck1297.kioskpos.common.ApiException;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot.ItemSnapshot;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot.TaxSnapshot;
import io.github.tuck1297.kioskpos.tax.TaxCalculator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The single place prices, line totals, tax and totals are computed. A pure function of a
 * {@link MenuSnapshot}: POST /orders/quote passes the cached snapshot, POST /orders passes
 * one read fresh from the database. Same rules either way, and the register never sends
 * its own numbers.
 */
@Service
public class PricingService {

    public record PricedLine(ItemSnapshot item, int quantity, String note, long unitPriceCents, long lineTotalCents) {
    }

    public record PricedOrder(MenuSnapshot menu, List<PricedLine> lines, long subtotalCents,
                              TaxSnapshot taxRate, long taxCents, long totalCents) {

        public String currency() {
            return menu.currency();
        }
    }

    public PricedOrder price(MenuSnapshot menu, List<OrderLineRequest> lines) {
        if (!menu.active()) {
            throw ApiException.locationClosed(menu.locationName());
        }

        List<PricedLine> priced = new ArrayList<>(lines.size());
        long subtotal = 0;
        for (OrderLineRequest line : lines) {
            ItemSnapshot item = menu.item(line.getMenuItemId()).orElseThrow(() -> ApiException.itemUnavailable(
                            "Menu item " + line.getMenuItemId() + " is not sold at " + menu.locationName() + ".")
                    .with("menuItemId", line.getMenuItemId().toString()));
            if (!item.available()) {
                throw ApiException.itemUnavailable(item.name() + " is sold out.")
                        .with("menuItemId", item.id().toString());
            }
            int quantity = line.getQuantity();
            long lineTotal = Math.multiplyExact(item.priceCents(), (long) quantity);
            subtotal = Math.addExact(subtotal, lineTotal);
            priced.add(new PricedLine(item, quantity, blankToNull(line.getNote()), item.priceCents(), lineTotal));
        }

        TaxSnapshot taxRate = menu.taxRate();
        long tax = TaxCalculator.taxCents(subtotal, taxRate.rateBps());
        return new PricedOrder(menu, List.copyOf(priced), subtotal, taxRate, tax, Math.addExact(subtotal, tax));
    }
}
