package io.github.tuck1297.kioskpos.web;

import io.github.tuck1297.kioskpos.api.PaymentsApi;
import io.github.tuck1297.kioskpos.api.model.CreatePaymentRequest;
import io.github.tuck1297.kioskpos.api.model.Payment;
import io.github.tuck1297.kioskpos.api.model.PaymentList;
import io.github.tuck1297.kioskpos.common.ApiException;
import io.github.tuck1297.kioskpos.payment.PaymentService;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentsController implements PaymentsApi {

    private final PaymentService payments;

    public PaymentsController(PaymentService payments) {
        this.payments = payments;
    }

    @Override
    public ResponseEntity<Payment> payOrder(UUID orderId, UUID idempotencyKey, String ifMatch,
                                            CreatePaymentRequest createPaymentRequest) {
        PaymentService.PayResult result = payments.pay(orderId, idempotencyKey, ifMatch, createPaymentRequest);
        if (result.declined()) {
            // The declined attempt is already committed; report it as 402 problem+json.
            throw ApiException.paymentDeclined(result.declineCode(), result.payment().getId());
        }
        return ResponseEntity.created(URI.create("/v1/orders/" + orderId + "/payments"))
                .body(result.payment());
    }

    @Override
    public ResponseEntity<PaymentList> listOrderPayments(UUID orderId) {
        return ResponseEntity.ok(payments.list(orderId));
    }
}
