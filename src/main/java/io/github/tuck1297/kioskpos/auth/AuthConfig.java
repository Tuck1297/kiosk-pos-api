package io.github.tuck1297.kioskpos.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Session settings. Every API replica must get the same AUTH_TOKEN_SECRET, or a cookie
 * signed by one replica is rejected by the next.
 */
@Configuration
public class AuthConfig {

    public record Settings(boolean cookieSecure, Duration sessionTtl) {
    }

    @Bean
    SessionToken sessionToken(@Value("${kiosk.auth.token-secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "kiosk.auth.token-secret (AUTH_TOKEN_SECRET) must be set to at least 32 characters");
        }
        return new SessionToken(bytes, Clock.systemUTC());
    }

    @Bean
    Settings authSettings(@Value("${kiosk.auth.cookie-secure:true}") boolean cookieSecure,
                          @Value("${kiosk.auth.session-ttl:PT12H}") Duration sessionTtl) {
        return new Settings(cookieSecure, sessionTtl);
    }
}
