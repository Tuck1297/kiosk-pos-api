package io.github.tuck1297.kioskpos.comp;

import static io.github.tuck1297.kioskpos.common.ApiMappers.blankToNull;

import io.github.tuck1297.kioskpos.api.model.CompRequest;
import io.github.tuck1297.kioskpos.api.model.Order;
import io.github.tuck1297.kioskpos.common.ApiException;
import io.github.tuck1297.kioskpos.common.NotFoundException;
import io.github.tuck1297.kioskpos.guard.IfMatch;
import io.github.tuck1297.kioskpos.idempotency.IdempotencyStore;
import io.github.tuck1297.kioskpos.idempotency.RequestFingerprint;
import io.github.tuck1297.kioskpos.order.OrderEntity;
import io.github.tuck1297.kioskpos.order.OrderLineEntity;
import io.github.tuck1297.kioskpos.order.OrderRepository;
import io.github.tuck1297.kioskpos.order.OrderService;
import io.github.tuck1297.kioskpos.staff.StaffAuthenticator;
import io.github.tuck1297.kioskpos.staff.StaffEntity;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comps an item or a whole open order, with a manager's approval.
 *
 * Order of checks matters: replay first (a retried comp returns the same result), then the
 * manager's PIN (counted even when it fails), then the order's version and state. Request
 * shape (e.g. OTHER needs a note) was already enforced by CompRequestGuard.
 */
@Service
public class CompService {

    private static final String SCOPE = "compOrder";

    private final OrderRepository orders;
    private final IdempotencyStore idempotency;
    private final StaffAuthenticator authenticator;
    private final EntityManager em;

    public CompService(OrderRepository orders, IdempotencyStore idempotency, StaffAuthenticator authenticator,
                       EntityManager em) {
        this.orders = orders;
        this.idempotency = idempotency;
        this.authenticator = authenticator;
        this.em = em;
    }

    @Transactional
    public Order comp(UUID orderId, UUID idempotencyKey, String ifMatch, CompRequest request, UUID requestedBy) {
        // The PIN is deliberately left out of the fingerprint: it is stored, and a short PIN's
        // hash would be trivial to reverse.
        String fingerprint = RequestFingerprint.of(SCOPE, orderId, request.getOrderLineId(), request.getReason(),
                blankToNull(request.getNote()), request.getManagerEmployeeId());
        if (idempotency.claim(SCOPE, idempotencyKey, fingerprint).isPresent()) {
            return OrderService.toApi(load(orderId));
        }

        StaffAuthenticator.Result manager = authenticator.verify(request.getManagerEmployeeId(), request.getManagerPin());
        if (manager.outcome() == StaffAuthenticator.Outcome.LOCKED) {
            throw ApiException.accountLocked(manager.lockedUntil());
        }
        if (manager.outcome() != StaffAuthenticator.Outcome.OK || !manager.isManager()) {
            throw ApiException.managerApprovalFailed();
        }

        OrderEntity order = load(orderId);
        if (!order.getVersion().equals(IfMatch.version(ifMatch))) {
            throw ApiException.staleVersion(order.getVersion());
        }
        if (!OrderEntity.OPEN.equals(order.getStatus())) {
            throw ApiException.compNotAllowed("Order #" + order.getOrderNumber() + " is " + order.getStatus()
                    + ". Only open (unpaid) orders can be comped.");
        }
        if (order.hasWholeOrderComp()) {
            throw ApiException.compNotAllowed("This order has already been comped in full.");
        }

        long amount;
        UUID lineId = request.getOrderLineId();
        if (lineId != null) {
            OrderLineEntity line = order.line(lineId)
                    .orElseThrow(() -> new NotFoundException("Order line", lineId));
            if (order.isLineComped(lineId)) {
                throw ApiException.compNotAllowed(line.getName() + " has already been comped.");
            }
            amount = order.lineCompAmountCents(line);
        } else {
            amount = order.uncompedNetCents();
        }
        if (amount <= 0) {
            throw ApiException.compNotAllowed("There is nothing left to comp on this order.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OrderCompEntity comp = new OrderCompEntity(order, lineId, request.getReason().getValue(),
                blankToNull(request.getNote()), amount,
                em.getReference(StaffEntity.class, manager.staffId()),
                requestedBy == null ? null : em.getReference(StaffEntity.class, requestedBy),
                now);
        em.persist(comp);
        order.applyComp(comp, now);
        em.flush();

        idempotency.complete(SCOPE, idempotencyKey, 201, comp.getId(), "/v1/orders/" + orderId);
        return OrderService.toApi(order);
    }

    private OrderEntity load(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }
}
