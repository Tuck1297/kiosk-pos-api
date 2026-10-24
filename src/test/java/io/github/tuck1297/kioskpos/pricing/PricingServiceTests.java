package io.github.tuck1297.kioskpos.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.tuck1297.kioskpos.api.model.OrderLineRequest;
import io.github.tuck1297.kioskpos.common.ApiException;
import io.github.tuck1297.kioskpos.discount.DiscountRule;
import java.util.Optional;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot.ItemSnapshot;
import io.github.tuck1297.kioskpos.menu.MenuSnapshot.TaxSnapshot;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PricingServiceTests {

    static final UUID PRETZEL = UUID.randomUUID();
    static final UUID LEMONADE = UUID.randomUUID();
    static final UUID CHURRO = UUID.randomUUID();

    private final PricingService pricing = new PricingService();

    private static MenuSnapshot menu(boolean active) {
        OffsetDateTime now = OffsetDateTime.now();
        return new MenuSnapshot(UUID.randomUUID(), "Lakeside Snack Stand", "LAKE", "America/New_York", active, "USD",
                new TaxSnapshot(UUID.randomUUID(), "PARK-STD", "Park standard sales tax", 650),
                List.of(new ItemSnapshot(PRETZEL, "SNK-PRETZEL", "Soft Pretzel", null, "SNACK", 750, true, now),
                        new ItemSnapshot(LEMONADE, "BEV-LEM-FRZ", "Frozen Lemonade", null, "BEVERAGE", 599, true, now),
                        new ItemSnapshot(CHURRO, "DST-CHURRO", "Churro", null, "DESSERT", 650, false, now)));
    }

    private static OrderLineRequest line(UUID id, int qty, String note) {
        return new OrderLineRequest().menuItemId(id).quantity(qty).note(note);
    }

    @Test
    void pricesLinesTaxAndTotal() {
        PricingService.PricedOrder p = pricing.price(menu(true), List.of(line(PRETZEL, 2, null), line(LEMONADE, 1, "  no ice ")));

        assertThat(p.lines()).extracting(PricingService.PricedLine::lineTotalCents).containsExactly(1500L, 599L);
        assertThat(p.subtotalCents()).isEqualTo(2099);
        assertThat(p.taxCents()).isEqualTo(136);
        assertThat(p.totalCents()).isEqualTo(2235);
        assertThat(p.lines().get(1).note()).isEqualTo("no ice");
        assertThat(p.currency()).isEqualTo("USD");
    }

    @Test
    void discountComesOffBeforeTax() {
        DiscountRule tenPercent = new DiscountRule(DiscountRule.CODE, UUID.randomUUID(), "PARKFAN10", null, null,
                "PARKFAN10 · 10% off", 1000, null, 0, false);
        DiscountRule fiveOffOverTwenty = new DiscountRule(DiscountRule.CODE, UUID.randomUUID(), "WELCOME5", null, null,
                "WELCOME5 · $5.00 off", null, 500L, 2000, false);
        List<OrderLineRequest> basket = List.of(line(PRETZEL, 2, null), line(LEMONADE, 1, null));

        PricingService.PricedOrder pct = pricing.price(menu(true), basket, Optional.of(tenPercent));
        assertThat(pct.discountCents()).isEqualTo(210);
        assertThat(pct.taxCents()).isEqualTo(123);
        assertThat(pct.totalCents()).isEqualTo(2012);

        PricingService.PricedOrder amt = pricing.price(menu(true), basket, Optional.of(fiveOffOverTwenty));
        assertThat(amt.discountCents()).isEqualTo(500);
        assertThat(amt.totalCents()).isEqualTo(1599 + 104);

        assertThatThrownBy(() -> pricing.price(menu(true), List.of(line(LEMONADE, 1, null)), Optional.of(fiveOffOverTwenty)))
                .isInstanceOf(ApiException.class).hasMessageContaining("$20.00");
    }

    @Test
    void discountNeverExceedsTheSubtotal() {
        DiscountRule bigAmount = new DiscountRule(DiscountRule.CODE, UUID.randomUUID(), "BIG", null, null,
                "BIG", null, 100_000L, 0, false);

        PricingService.PricedOrder p = pricing.price(menu(true), List.of(line(LEMONADE, 1, null)), Optional.of(bigAmount));
        assertThat(p.discountCents()).isEqualTo(599);
        assertThat(p.totalCents()).isZero();
    }

    @Test
    void rejectsSoldOutUnknownAndClosed() {
        assertThatThrownBy(() -> pricing.price(menu(true), List.of(line(CHURRO, 1, null))))
                .isInstanceOf(ApiException.class).hasMessageContaining("sold out")
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("ITEM_UNAVAILABLE");
        assertThatThrownBy(() -> pricing.price(menu(true), List.of(line(UUID.randomUUID(), 1, null))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("ITEM_UNAVAILABLE");
        assertThatThrownBy(() -> pricing.price(menu(false), List.of(line(PRETZEL, 1, null))))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo("LOCATION_CLOSED");
    }
}
