package io.github.tuck1297.kioskpos.guard;

import io.github.tuck1297.kioskpos.api.model.CompReason;
import io.github.tuck1297.kioskpos.api.model.CompRequest;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

/** "Other" isn't an audit trail on its own: it needs a note saying what happened. */
@Component
public class CompRequestGuard implements RequestGuard<CompRequest> {

    @Override
    public Class<CompRequest> type() {
        return CompRequest.class;
    }

    @Override
    public void check(CompRequest request, Errors errors) {
        if (request.getReason() == CompReason.OTHER && (request.getNote() == null || request.getNote().isBlank())) {
            errors.rejectValue("note", "required", "is required when the reason is OTHER");
        }
    }
}
