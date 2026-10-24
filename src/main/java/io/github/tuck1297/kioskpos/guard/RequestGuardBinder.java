package io.github.tuck1297.kioskpos.guard;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Plugs every {@link RequestGuard} into Spring MVC. The generated API interfaces mark each
 * request body {@code @Valid}, so Spring validates it while binding: schema rules first,
 * then the guards for that body's type. Any failure raises MethodArgumentNotValidException
 * before the controller method is called.
 */
@ControllerAdvice
public class RequestGuardBinder {

    private final Map<Class<?>, List<RequestGuard<?>>> guardsByType;

    public RequestGuardBinder(List<RequestGuard<?>> guards) {
        this.guardsByType = guards.stream()
                .collect(Collectors.groupingBy(g -> (Class<?>) g.type()));
    }

    @InitBinder
    public void registerGuards(WebDataBinder binder) {
        Object target = binder.getTarget();
        if (target == null) {
            return;
        }
        List<RequestGuard<?>> guards = guardsByType.get(target.getClass());
        if (guards != null) {
            binder.addValidators(new GuardValidator(target.getClass(), guards));
        }
    }

    /** Adapts the guards for one request type to Spring's Validator interface. */
    private static final class GuardValidator implements Validator {

        private final Class<?> type;
        private final List<RequestGuard<?>> guards;

        GuardValidator(Class<?> type, List<RequestGuard<?>> guards) {
            this.type = type;
            this.guards = guards;
        }

        @Override
        public boolean supports(Class<?> clazz) {
            return type.isAssignableFrom(clazz);
        }

        @Override
        @SuppressWarnings("unchecked")
        public void validate(Object target, Errors errors) {
            for (RequestGuard<?> guard : guards) {
                ((RequestGuard<Object>) guard).check(target, errors);
            }
        }
    }
}
