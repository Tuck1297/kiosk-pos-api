package io.github.tuck1297.kioskpos.pricing;

import static io.github.tuck1297.kioskpos.common.ApiMappers.blankToNull;

import io.github.tuck1297.kioskpos.api.model.OrderLineRequest;
import io.github.tuck1297.kioskpos.common.ApiException;
import io.github.tuck1297.kioskpos.discount.DiscountRule;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot.ItemSnapshot;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot.TaxSnapshot;
import io.github.tuck1297.kioskpos.tax.TaxCalculator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The single place prices, discounts, tax and totals are computed. A pure function of a
 * {@link MenuSnapshot} and an already-validated discount: POST /orders/quote passes the
 * cached snapshot, POST /orders one read fresh from the database. Same rules either way,
 * and the register never sends its own numbers.
 *
 * subtotal - discount = taxable; tax on taxable (rounded half up); total = taxable + tax.
 */
@Service
public class PricingService {

    public record PricedLine(ItemSnapshot item, int quantity, String note, long unitPriceCents, long lineTotalCents) {
    }

    public record PricedOrder(MenuSnapshot menu, List<PricedLine> lines, long subtotalCents,
                              Optional<DiscountRule> discount, long discountCents,
                              TaxSnapshot taxRate, long taxCents, long totalCents) {

        public String currency() {
            return menu.currency();
        }
    }

    public PricedOrder price(MenuSnapshot menu, List<OrderLineRequest> lines) {
        return price(menu, lines, Optional.empty());
    }

    public PricedOrder price(MenuSnapshot menu, List<OrderLineRequest> lines, Optional<DiscountRule> discount) {
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

        long discountCents = 0;
        if (discount.isPresent()) {
            discountCents = discount.get().amountOff(subtotal);
        }
        long taxable = subtotal - discountCents;
        TaxSnapshot taxRate = menu.taxRate();
        long tax = TaxCalculator.taxCents(taxable, taxRate.rateBps());
        return new PricedOrder(menu, List.copyOf(priced), subtotal, discount, discountCents, taxRate, tax,
                Math.addExact(taxable, tax));
    }
}
