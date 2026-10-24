package io.github.tuck1297.kioskpos.web;

import io.github.tuck1297.kioskpos.api.AuthApi;
import io.github.tuck1297.kioskpos.api.model.LoginRequest;
import io.github.tuck1297.kioskpos.api.model.Staff;
import io.github.tuck1297.kioskpos.api.model.StaffRole;
import io.github.tuck1297.kioskpos.auth.AuthConfig;
import io.github.tuck1297.kioskpos.auth.AuthFilter;
import io.github.tuck1297.kioskpos.auth.CurrentStaff;
import io.github.tuck1297.kioskpos.auth.SessionToken;
import io.github.tuck1297.kioskpos.common.ApiException;
import io.github.tuck1297.kioskpos.staff.StaffAuthenticator;
import io.github.tuck1297.kioskpos.staff.StaffEntity;
import io.github.tuck1297.kioskpos.staff.StaffRepository;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    private final StaffAuthenticator authenticator;
    private final StaffRepository staff;
    private final SessionToken tokens;
    private final AuthConfig.Settings settings;
    private final CurrentStaff currentStaff;

    public AuthController(StaffAuthenticator authenticator, StaffRepository staff, SessionToken tokens,
                          AuthConfig.Settings settings, CurrentStaff currentStaff) {
        this.authenticator = authenticator;
        this.staff = staff;
        this.tokens = tokens;
        this.settings = settings;
        this.currentStaff = currentStaff;
    }

    @Override
    public ResponseEntity<Staff> login(LoginRequest loginRequest) {
        StaffAuthenticator.Result result = authenticator.verify(loginRequest.getEmployeeId(), loginRequest.getPin());
        switch (result.outcome()) {
            case LOCKED -> throw ApiException.accountLocked(result.lockedUntil());
            case BAD_CREDENTIALS -> throw ApiException.invalidCredentials();
            case OK -> { }
        }
        String token = tokens.issue(result.staffId(), result.employeeId(), result.role(), settings.sessionTtl());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(token, settings.sessionTtl()))
                .body(new Staff()
                        .id(result.staffId())
                        .employeeId(result.employeeId())
                        .name(result.name())
                        .role(StaffRole.fromValue(result.role())));
    }

    @Override
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
                .build();
    }

    @Override
    public ResponseEntity<Staff> getCurrentStaff() {
        SessionToken.Claims claims = currentStaff.get();
        StaffEntity s = staff.findById(claims.staffId())
                .filter(StaffEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Sign in required",
                        "This account is no longer active."));
        return ResponseEntity.ok(new Staff()
                .id(s.getId())
                .employeeId(s.getEmployeeId())
                .name(s.getName())
                .role(StaffRole.fromValue(s.getRole())));
    }

    /** HttpOnly: JavaScript can't read it. SameSite=Strict: never sent on cross-site requests. */
    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(AuthFilter.COOKIE, value)
                .httpOnly(true)
                .secure(settings.cookieSecure())
                .sameSite("Strict")
                .path("/v1")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}
