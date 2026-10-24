package io.github.tuck1297.kioskpos.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * An error with a known HTTP status and a stable {@code code} from the Problem schema.
 * {@link ApiExceptionHandler} turns it into application/problem+json.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;
    private final Map<String, Object> properties = new LinkedHashMap<>();

    public ApiException(HttpStatus status, String code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public ApiException with(String key, Object value) {
        properties.put(key, value);
        return this;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public static ApiException itemUnavailable(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "ITEM_UNAVAILABLE", "Item unavailable", detail);
    }

    public static ApiException locationClosed(String locationName) {
        return new ApiException(HttpStatus.CONFLICT, "LOCATION_CLOSED", "Location closed",
                locationName + " is not taking orders right now.");
    }

    public static ApiException invalidTransition(String detail) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_TRANSITION", "Invalid status transition", detail);
    }

    public static ApiException staleVersion(Integer currentVersion) {
        return new ApiException(HttpStatus.PRECONDITION_FAILED, "STALE_VERSION", "Stale version",
                "The order changed since you last read it. Re-fetch it and try again.")
                .with("currentVersion", currentVersion);
    }

    public static ApiException idempotencyKeyReused() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "IDEMPOTENCY_KEY_REUSED", "Idempotency key reused",
                "This Idempotency-Key was already used with a different request. Generate a new key.");
    }

    public static ApiException idempotencyInProgress() {
        return new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS", "Request in progress",
                "A request with this Idempotency-Key is still being processed. Retry shortly.");
    }

    public static ApiException validation(String code, String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, "Validation failed", field + " " + message)
                .with("errors", List.of(Map.of("field", field, "message", message)));
    }

    public static ApiException paymentDeclined(String declineCode, UUID paymentId) {
        return new ApiException(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED", "Payment declined",
                "The card was declined. The guest was not charged; the order is still open.")
                .with("declineCode", declineCode)
                .with("paymentId", paymentId.toString());
    }
}
