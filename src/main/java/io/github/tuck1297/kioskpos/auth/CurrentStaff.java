package io.github.tuck1297.kioskpos.auth;

import io.github.tuck1297.kioskpos.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** The staff member signed in for the current request, as checked by {@link AuthFilter}. */
@Component
public class CurrentStaff {

    public SessionToken.Claims get() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object claims = attributes == null
                ? null
                : attributes.getAttribute(AuthFilter.CLAIMS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (claims instanceof SessionToken.Claims c) {
            return c;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sign in required",
                "Sign in at the register to continue.");
    }
}
