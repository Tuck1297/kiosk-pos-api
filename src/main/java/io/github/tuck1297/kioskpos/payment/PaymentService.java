package io.github.tuck1297.kioskpos.payment;

import static io.github.tuck1297.kioskpos.common.ApiMappers.money;

import io.github.tuck1297.kioskpos.api.model.CreatePaymentRequest;
import io.github.tuck1297.kioskpos.api.model.Payment;
import io.github.tuck1297.kioskpos.api.model.PaymentList;
import io.github.tuck1297.kioskpos.api.model.PaymentStatus;
import io.github.tuck1297.kioskpos.api.model.TenderType;
import io.github.tuck1297.kioskpos.common.ApiException;
import io.github.tuck1297.kioskpos.common.NotFoundException;
import io.github.tuck1297.kioskpos.idempotency.IdempotencyStore;
import io.github.tuck1297.kioskpos.idempotency.RequestFingerprint;
import io.github.tuck1297.kioskpos.order.OrderEntity;
import io.github.tuck1297.kioskpos.order.OrderRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final String PAY_SCOPE = "payOrder";

    public record PayResult(Payment payment, boolean declined, String declineCode) {
    }

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final IdempotencyStore idempotency;
    private final MockCardProcessor processor;
    private final EntityManager em;

    public PaymentService(OrderRepository orders, PaymentRepository payments, IdempotencyStore idempotency,
                          MockCardProcessor processor, EntityManager em) {
        this.orders = orders;
        this.payments = payments;
        this.idempotency = idempotency;
        this.processor = processor;
        this.em = em;
    }

    /**
     * Charges the order's server-computed total. A decline is committed (so it shows in the
     * payment history) and reported to the caller instead of thrown, so it isn't rolled back.
     */
    @Transactional
    public PayResult pay(UUID orderId, UUID idempotencyKey, String ifMatch, CreatePaymentRequest request) {
        String fingerprint = RequestFingerprint.of(PAY_SCOPE, orderId, request.getTenderType(),
                request.getCardToken(), request.getCashTenderedCents());
        Optional<IdempotencyStore.StoredResult> earlier = idempotency.claim(PAY_SCOPE, idempotencyKey, fingerprint);
        if (earlier.isPresent()) {
            PaymentEntity stored = payments.findById(earlier.get().resourceId())
                    .orElseThrow(() -> new NotFoundException("Payment", earlier.get().resourceId()));
            return result(stored);
        }

        OrderEntity order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        if (!order.getVersion().equals(parseIfMatch(ifMatch))) {
            throw ApiException.staleVersion(order.getVersion());
        }
        if (!OrderEntity.OPEN.equals(order.getStatus())) {
            throw ApiException.invalidTransition("Order #" + order.getOrderNumber() + " is " + order.getStatus()
                    + ". Only OPEN orders can be paid.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        long total = order.getTotalCents();
        PaymentEntity payment;
        if (request.getTenderType() == TenderType.CASH) {
            Long tendered = request.getCashTenderedCents();
            if (tendered == null) {
                throw ApiException.validation("VALIDATION_FAILED", "cashTenderedCents", "is required for cash payments");
            }
            if (request.getCardToken() != null) {
                throw ApiException.validation("VALIDATION_FAILED", "cardToken", "must not be sent for cash payments");
            }
            if (tendered < total) {
                throw ApiException.validation("INSUFFICIENT_CASH", "cashTenderedCents",
                        "must be at least the order total of " + total + " cents");
            }
            payment = PaymentEntity.cash(orderId, total, order.getCurrency(), tendered, now);
        } else {
            String token = request.getCardToken();
            if (token == null || token.isBlank()) {
                throw ApiException.validation("VALIDATION_FAILED", "cardToken", "is required for card payments");
            }
            if (request.getCashTenderedCents() != null) {
                throw ApiException.validation("VALIDATION_FAILED", "cashTenderedCents", "must not be sent for card payments");
            }
            MockCardProcessor.CardResult card = processor.charge(token, total);
            payment = card.approved()
                    ? PaymentEntity.cardCaptured(orderId, total, order.getCurrency(), card.last4(), card.reference(), now)
                    : PaymentEntity.cardDeclined(orderId, total, order.getCurrency(), card.last4(), card.declineCode(), now);
        }

        em.persist(payment);
        if (payment.isCaptured()) {
            order.markPaid(now);
        }
        // Flush now so a concurrent change to the order (version check) or a second captured
        // payment (unique index) fails here, before the idempotency key is completed.
        em.flush();

        idempotency.complete(PAY_SCOPE, idempotencyKey, payment.isCaptured() ? 201 : 402, payment.getId(),
                "/v1/orders/" + orderId + "/payments");
        return result(payment);
    }

    @Transactional(readOnly = true)
    public PaymentList list(UUID orderId) {
        if (!orders.existsById(orderId)) {
            throw new NotFoundException("Order", orderId);
        }
        return new PaymentList().items(payments.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(PaymentService::toApi)
                .collect(Collectors.toList()));
    }

    private static PayResult result(PaymentEntity p) {
        return new PayResult(toApi(p), !p.isCaptured(), p.getDeclineCode());
    }

    static Payment toApi(PaymentEntity p) {
        return new Payment()
                .id(p.getId())
                .orderId(p.getOrderId())
                .tenderType(TenderType.fromValue(p.getTenderType()))
                .status(PaymentStatus.fromValue(p.getStatus()))
                .amount(money(p.getAmountCents(), p.getCurrency()))
                .changeDue(p.getChangeDueCents() == null ? null : money(p.getChangeDueCents(), p.getCurrency()))
                .cardLast4(p.getCardLast4())
                .processorReference(p.getProcessorReference())
                .declineCode(p.getDeclineCode())
                .createdAt(p.getCreatedAt());
    }

    /** Accepts {@code "3"}, {@code W/"3"} or {@code 3}. */
    static int parseIfMatch(String ifMatch) {
        String value = ifMatch == null ? "" : ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw ApiException.validation("VALIDATION_FAILED", "If-Match", "must be the order's ETag, e.g. \"0\"");
        }
    }
}
