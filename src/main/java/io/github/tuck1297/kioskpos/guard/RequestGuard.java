package io.github.tuck1297.kioskpos.guard;

import org.springframework.validation.Errors;

/**
 * Rejects an invalid request body before it reaches a controller.
 *
 * The OpenAPI schema already checks types, required fields, lengths and patterns (that is
 * generated as Bean Validation annotations). A guard adds the rules the schema can't
 * express: ones that span fields, such as "card payments need a cardToken and no cash".
 *
 * Implement it as a Spring bean and it runs automatically for every request body of
 * {@link #type()}: {@link RequestGuardBinder} registers it with Spring MVC's validation,
 * so a failed check never reaches controller or service code and comes back as
 * 400 VALIDATION_FAILED listing each bad field.
 */
public interface RequestGuard<T> {

    Class<T> type();

    /** Report problems with {@code errors.rejectValue(field, code, message)}; never throw. */
    void check(T request, Errors errors);
}
