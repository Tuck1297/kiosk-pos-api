package io.github.tuck1297.kioskpos.guard;

import io.github.tuck1297.kioskpos.api.model.QuoteRequest;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component
public class QuoteRequestGuard implements RequestGuard<QuoteRequest> {

    @Override
    public Class<QuoteRequest> type() {
        return QuoteRequest.class;
    }

    @Override
    public void check(QuoteRequest request, Errors errors) {
        OrderLineRules.check(request.getLines(), errors);
    }
}
