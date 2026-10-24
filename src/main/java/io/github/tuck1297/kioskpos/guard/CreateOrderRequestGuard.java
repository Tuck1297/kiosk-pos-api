package io.github.tuck1297.kioskpos.guard;

import io.github.tuck1297.kioskpos.api.model.CreateOrderRequest;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component
public class CreateOrderRequestGuard implements RequestGuard<CreateOrderRequest> {

    @Override
    public Class<CreateOrderRequest> type() {
        return CreateOrderRequest.class;
    }

    @Override
    public void check(CreateOrderRequest request, Errors errors) {
        OrderLineRules.check(request.getLines(), errors);
    }
}
