package io.github.tuck1297.kioskpos.guard;

import io.github.tuck1297.kioskpos.api.model.CreatePaymentRequest;
import io.github.tuck1297.kioskpos.api.model.TenderType;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

/**
 * Each tender type has exactly its own fields: card payments carry a card token and no
 * cash amount; cash payments carry a cash amount and no card token. Whether the cash
 * covers the total depends on the order, so the payment service checks that.
 */
@Component
public class PaymentRequestGuard implements RequestGuard<CreatePaymentRequest> {

    @Override
    public Class<CreatePaymentRequest> type() {
        return CreatePaymentRequest.class;
    }

    @Override
    public void check(CreatePaymentRequest request, Errors errors) {
        if (request.getTenderType() == TenderType.CARD) {
            if (request.getCardToken() == null || request.getCardToken().isBlank()) {
                errors.rejectValue("cardToken", "required", "is required for card payments");
            }
            if (request.getCashTenderedCents() != null) {
                errors.rejectValue("cashTenderedCents", "not_allowed", "must not be sent for card payments");
            }
        } else if (request.getTenderType() == TenderType.CASH) {
            if (request.getCashTenderedCents() == null) {
                errors.rejectValue("cashTenderedCents", "required", "is required for cash payments");
            }
            if (request.getCardToken() != null) {
                errors.rejectValue("cardToken", "not_allowed", "must not be sent for cash payments");
            }
        }
    }
}
