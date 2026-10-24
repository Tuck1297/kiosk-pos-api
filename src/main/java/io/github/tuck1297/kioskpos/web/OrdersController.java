package io.github.tuck1297.kioskpos.web;

import static io.github.tuck1297.kioskpos.common.ApiMappers.money;

import io.github.tuck1297.kioskpos.api.OrdersApi;
import io.github.tuck1297.kioskpos.api.model.AppliedDiscount;
import io.github.tuck1297.kioskpos.api.model.CompRequest;
import io.github.tuck1297.kioskpos.api.model.DiscountKind;
import io.github.tuck1297.kioskpos.api.model.StaffRef;
import io.github.tuck1297.kioskpos.discount.DiscountResolver;
import io.github.tuck1297.kioskpos.discount.DiscountRule;
import java.util.Optional;
import io.github.tuck1297.kioskpos.api.model.CreateOrderRequest;
import io.github.tuck1297.kioskpos.auth.CurrentStaff;
import io.github.tuck1297.kioskpos.comp.CompService;
import io.github.tuck1297.kioskpos.api.model.Order;
import io.github.tuck1297.kioskpos.api.model.OrderQuote;
import io.github.tuck1297.kioskpos.api.model.QuoteLine;
import io.github.tuck1297.kioskpos.api.model.QuoteRequest;
import io.github.tuck1297.kioskpos.common.ApiMappers;
import io.github.tuck1297.kioskpos.menu.MenuCatalog;
import io.github.tuck1297.kioskpos.order.OrderService;
import io.github.tuck1297.kioskpos.pricing.PricingService;
import io.github.tuck1297.kioskpos.pricing.PricingService.PricedOrder;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order endpoints. Not overridden yet (listOrders, updateOrderStatus, cancelOrder) answer
 * 501 from the generated defaults.
 */
@RestController
public class OrdersController implements OrdersApi {

    private final MenuCatalog catalog;
    private final PricingService pricing;
    private final DiscountResolver discounts;
    private final OrderService orders;
    private final CompService comps;
    private final CurrentStaff currentStaff;

    public OrdersController(MenuCatalog catalog, PricingService pricing, DiscountResolver discounts,
                            OrderService orders, CompService comps, CurrentStaff currentStaff) {
        this.catalog = catalog;
        this.pricing = pricing;
        this.discounts = discounts;
        this.orders = orders;
        this.comps = comps;
        this.currentStaff = currentStaff;
    }

    @Override
    public ResponseEntity<Order> compOrder(UUID orderId, UUID idempotencyKey, String ifMatch, CompRequest compRequest) {
        Order order = comps.comp(orderId, idempotencyKey, ifMatch, compRequest, currentStaff.get().staffId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag(String.valueOf(order.getVersion()))
                .body(order);
    }

    /**
     * Priced from the cached menu: the hottest read path, kept off the database. A discount,
     * when the register sends one, is checked against Postgres (codes expire and run out).
     */
    @Override
    public ResponseEntity<OrderQuote> quoteOrder(QuoteRequest quoteRequest) {
        Optional<DiscountRule> discount = discounts.resolve(quoteRequest.getDiscount());
        PricedOrder priced = pricing.price(catalog.cached(quoteRequest.getLocationId()), quoteRequest.getLines(),
                discount);
        String currency = priced.currency();
        return ResponseEntity.ok(new OrderQuote()
                .locationId(priced.menu().locationId())
                .lines(priced.lines().stream()
                        .map(l -> new QuoteLine()
                                .menuItemId(l.item().id())
                                .sku(l.item().sku())
                                .name(l.item().name())
                                .quantity(l.quantity())
                                .unitPrice(money(l.unitPriceCents(), currency))
                                .lineTotal(money(l.lineTotalCents(), currency))
                                .note(l.note()))
                        .collect(Collectors.toList()))
                .subtotal(money(priced.subtotalCents(), currency))
                .discount(discount.map(d -> new AppliedDiscount()
                        .kind(DiscountKind.fromValue(d.kind()))
                        .code(d.code())
                        .employee(d.employeeRef() == null ? null
                                : new StaffRef().employeeId(d.employeeRef().employeeId()).name(d.employeeRef().name()))
                        .label(d.label())
                        .rateBps(d.percentBps())
                        .amount(money(priced.discountCents(), currency))).orElse(null))
                .discountTotal(money(priced.discountCents(), currency))
                .taxRate(ApiMappers.taxRate(priced.taxRate()))
                .tax(money(priced.taxCents(), currency))
                .total(money(priced.totalCents(), currency))
                .pricedAt(OffsetDateTime.now()));
    }

    @Override
    public ResponseEntity<Order> createOrder(UUID idempotencyKey, CreateOrderRequest createOrderRequest) {
        Order order = orders.create(idempotencyKey, createOrderRequest, currentStaff.get().staffId());
        return ResponseEntity.created(URI.create("/v1/orders/" + order.getId()))
                .eTag(String.valueOf(order.getVersion()))
                .body(order);
    }

    @Override
    public ResponseEntity<Order> getOrder(UUID orderId) {
        Order order = orders.get(orderId);
        return ResponseEntity.ok()
                .eTag(String.valueOf(order.getVersion()))
                .body(order);
    }
}
