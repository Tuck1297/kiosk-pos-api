package io.github.tuck1297.kioskpos.order;

import static io.github.tuck1297.kioskpos.common.ApiMappers.blankToNull;
import static io.github.tuck1297.kioskpos.common.ApiMappers.money;

import io.github.tuck1297.kioskpos.api.model.AppliedDiscount;
import io.github.tuck1297.kioskpos.api.model.Comp;
import io.github.tuck1297.kioskpos.api.model.DiscountKind;
import io.github.tuck1297.kioskpos.api.model.DiscountRequest;
import io.github.tuck1297.kioskpos.discount.DiscountCodeEntity;
import io.github.tuck1297.kioskpos.discount.DiscountResolver;
import io.github.tuck1297.kioskpos.discount.DiscountRule;
import io.github.tuck1297.kioskpos.api.model.CompReason;
import io.github.tuck1297.kioskpos.api.model.CreateOrderRequest;
import io.github.tuck1297.kioskpos.common.ApiMappers;
import io.github.tuck1297.kioskpos.staff.StaffEntity;
import io.github.tuck1297.kioskpos.api.model.Order;
import io.github.tuck1297.kioskpos.api.model.OrderChannel;
import io.github.tuck1297.kioskpos.api.model.OrderLine;
import io.github.tuck1297.kioskpos.api.model.OrderLineRequest;
import io.github.tuck1297.kioskpos.api.model.OrderStatus;
import io.github.tuck1297.kioskpos.common.NotFoundException;
import io.github.tuck1297.kioskpos.idempotency.IdempotencyStore;
import io.github.tuck1297.kioskpos.idempotency.RequestFingerprint;
import io.github.tuck1297.kioskpos.menu.MenuCatalog;
import io.github.tuck1297.kioskpos.pricing.PricingService;
import io.github.tuck1297.kioskpos.pricing.PricingService.PricedLine;
import io.github.tuck1297.kioskpos.pricing.PricingService.PricedOrder;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final String CREATE_SCOPE = "createOrder";

    private final MenuCatalog catalog;
    private final PricingService pricing;
    private final DiscountResolver discounts;
    private final OrderRepository orders;
    private final IdempotencyStore idempotency;
    private final JdbcTemplate jdbc;
    private final EntityManager em;

    public OrderService(MenuCatalog catalog, PricingService pricing, DiscountResolver discounts,
                        OrderRepository orders, IdempotencyStore idempotency, JdbcTemplate jdbc, EntityManager em) {
        this.catalog = catalog;
        this.pricing = pricing;
        this.discounts = discounts;
        this.orders = orders;
        this.idempotency = idempotency;
        this.jdbc = jdbc;
        this.em = em;
    }

    /**
     * Creates an OPEN order priced entirely on the server. Retrying with the same
     * Idempotency-Key returns the same order instead of creating a second one.
     */
    @Transactional
    public Order create(UUID idempotencyKey, CreateOrderRequest request, UUID staffId) {
        DiscountRequest discountRequest = request.getDiscount();
        String fingerprint = RequestFingerprint.of(CREATE_SCOPE, request.getLocationId(), request.getChannel(),
                blankToNull(request.getGuestName()), linesFingerprint(request.getLines()),
                discountRequest == null ? null : discountRequest.getCode() + "/" + discountRequest.getEmployeeId());
        Optional<IdempotencyStore.StoredResult> earlier = idempotency.claim(CREATE_SCOPE, idempotencyKey, fingerprint);
        if (earlier.isPresent()) {
            return toApi(load(earlier.get().resourceId()));
        }

        // Price from the database, never the cache: this is the write that takes money.
        Optional<DiscountRule> discount = discounts.resolve(discountRequest);
        PricedOrder priced = pricing.price(catalog.fresh(request.getLocationId()), request.getLines(), discount);
        discount.ifPresent(discounts::redeem);
        Integer orderNumber = jdbc.queryForObject("""
                UPDATE location SET next_order_number = next_order_number + 1
                WHERE id = ?
                RETURNING next_order_number - 1""", Integer.class, request.getLocationId());

        OffsetDateTime now = OffsetDateTime.now();
        OrderEntity order = new OrderEntity(UUID.randomUUID(), request.getLocationId(), orderNumber,
                request.getChannel().getValue(), blankToNull(request.getGuestName()), priced.currency(),
                priced.subtotalCents(), priced.taxRate().rateBps(), priced.taxCents(), priced.totalCents(),
                staffId == null ? null : em.getReference(StaffEntity.class, staffId), now);
        discount.ifPresent(d -> order.setDiscount(d.kind(),
                d.codeId() == null ? null : em.getReference(DiscountCodeEntity.class, d.codeId()),
                d.employeeId() == null ? null : em.getReference(StaffEntity.class, d.employeeId()),
                d.label(), d.percentBps(), priced.discountCents()));
        short lineNo = 1;
        for (PricedLine line : priced.lines()) {
            order.addLine(new OrderLineEntity(UUID.randomUUID(), lineNo++, line.item().id(), line.item().sku(),
                    line.item().name(), line.unitPriceCents(), line.quantity(), line.lineTotalCents(), line.note()));
        }
        em.persist(order);
        em.flush();

        idempotency.complete(CREATE_SCOPE, idempotencyKey, 201, order.getId(), "/v1/orders/" + order.getId());
        return toApi(order);
    }

    @Transactional(readOnly = true)
    public Order get(UUID orderId) {
        return toApi(load(orderId));
    }

    OrderEntity load(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    /** Must be called inside a transaction: lines, comps and staff load lazily. */
    public static Order toApi(OrderEntity o) {
        String currency = o.getCurrency();
        List<OrderLine> lines = o.getLines().stream()
                .map(l -> new OrderLine()
                        .id(l.getId())
                        .menuItemId(l.getMenuItemId())
                        .sku(l.getSku())
                        .name(l.getName())
                        .quantity(l.getQuantity())
                        .unitPrice(money(l.getUnitPriceCents(), currency))
                        .lineTotal(money(l.getLineTotalCents(), currency))
                        .note(l.getNote())
                        .comped(o.isLineComped(l.getId())))
                .collect(Collectors.toList());
        List<Comp> comps = o.getComps().stream()
                .map(c -> new Comp()
                        .id(c.getId())
                        .orderLineId(c.getOrderLineId())
                        .reason(CompReason.fromValue(c.getReason()))
                        .note(c.getNote())
                        .amount(money(c.getAmountCents(), currency))
                        .approvedBy(ApiMappers.staffRef(c.getApprovedBy()))
                        .requestedBy(ApiMappers.staffRef(c.getRequestedBy()))
                        .createdAt(c.getCreatedAt()))
                .collect(Collectors.toList());
        return new Order()
                .id(o.getId())
                .orderNumber(o.getOrderNumber())
                .locationId(o.getLocationId())
                .channel(OrderChannel.fromValue(o.getChannel()))
                .guestName(o.getGuestName())
                .status(OrderStatus.fromValue(o.getStatus()))
                .lines(lines)
                .subtotal(money(o.getSubtotalCents(), currency))
                .discount(o.getDiscountKind() == null ? null : new AppliedDiscount()
                        .kind(DiscountKind.fromValue(o.getDiscountKind()))
                        .code(o.getDiscountCode() == null ? null : o.getDiscountCode().getCode())
                        .employee(ApiMappers.staffRef(o.getDiscountEmployee()))
                        .label(o.getDiscountLabel())
                        .rateBps(o.getDiscountRateBps())
                        .amount(money(o.getDiscountCents(), currency)))
                .discountTotal(money(o.getDiscountCents(), currency))
                .compTotal(money(o.getCompCents(), currency))
                .comps(comps)
                .createdBy(ApiMappers.staffRef(o.getCreatedBy()))
                .taxRateBps(o.getTaxRateBps())
                .tax(money(o.getTaxCents(), currency))
                .total(money(o.getTotalCents(), currency))
                .cancelReason(o.getCancelReason())
                .version(o.getVersion())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt());
    }

    private static String linesFingerprint(List<OrderLineRequest> lines) {
        return lines.stream()
                .map(l -> l.getMenuItemId() + "x" + l.getQuantity() + ":" + blankToNull(l.getNote()))
                .collect(Collectors.joining(","));
    }
}
