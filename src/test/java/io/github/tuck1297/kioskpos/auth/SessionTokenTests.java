package io.github.tuck1297.kioskpos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionTokenTests {

    static final byte[] KEY = "test-signing-key-0123456789abcdef-xyz".getBytes(StandardCharsets.UTF_8);
    static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    private final SessionToken tokens = new SessionToken(KEY, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void roundTrips() {
        UUID staff = UUID.randomUUID();
        String token = tokens.issue(staff, "C2001", "CASHIER", Duration.ofHours(12));

        SessionToken.Claims claims = tokens.verify(token).orElseThrow();
        assertThat(claims.staffId()).isEqualTo(staff);
        assertThat(claims.employeeId()).isEqualTo("C2001");
        assertThat(claims.role()).isEqualTo("CASHIER");
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
    }

    @Test
    void rejectsTamperingAndOtherKeys() {
        String token = tokens.issue(UUID.randomUUID(), "C2001", "CASHIER", Duration.ofHours(1));
        String[] parts = token.split("\\.");
        // Swap in a payload claiming to be a manager, keeping the original signature.
        String forged = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                new String(java.util.Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)
                        .replace("CASHIER", "MANAGER").getBytes(StandardCharsets.UTF_8)) + "." + parts[1];
        SessionToken otherKey = new SessionToken("another-signing-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(tokens.verify(forged)).isEmpty();
        assertThat(otherKey.verify(token)).isEmpty();
        assertThat(tokens.verify("not.a-token")).isEmpty();
        assertThat(tokens.verify("")).isEmpty();
        assertThat(tokens.verify(null)).isEmpty();
    }

    @Test
    void expires() {
        String token = tokens.issue(UUID.randomUUID(), "C2001", "CASHIER", Duration.ofHours(12));
        SessionToken later = new SessionToken(KEY, Clock.fixed(NOW.plus(Duration.ofHours(12)), ZoneOffset.UTC));

        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void refusesShortKeys() {
        assertThatThrownBy(() -> new SessionToken("short".getBytes(StandardCharsets.UTF_8), Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
