package io.github.tuck1297.kioskpos.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The value of the {@code kiosk_session} cookie: who signed in, their role and when the
 * session ends, signed with HMAC-SHA256.
 *
 * Signed rather than stored, so every API replica can check it on every request without
 * a database or Redis lookup, and nothing needs sharing between replicas except the key.
 * Trade-off: one session can't be revoked before it expires (12 h); rotating the key
 * signs everyone out.
 *
 * Format: base64url(payload) "." base64url(hmac(payload)), where payload is
 * {@code v1|staffId|employeeId|role|expiresAtEpochSeconds|nonce}.
 */
public final class SessionToken {

    public record Claims(UUID staffId, String employeeId, String role, Instant expiresAt) {
    }

    private static final String VERSION = "v1";
    private static final Set<String> ROLES = Set.of("CASHIER", "MANAGER");
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SessionToken(byte[] secret, Clock clock) {
        if (secret.length < 32) {
            throw new IllegalArgumentException("The session signing key must be at least 32 bytes");
        }
        this.key = new SecretKeySpec(secret, "HmacSHA256");
        this.clock = clock;
    }

    public String issue(UUID staffId, String employeeId, String role, Duration ttl) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        long expires = clock.instant().plus(ttl).getEpochSecond();
        String payload = String.join("|", VERSION, staffId.toString(), employeeId, role,
                Long.toString(expires), B64.encodeToString(nonce));
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        return B64.encodeToString(body) + "." + B64.encodeToString(sign(body));
    }

    /** Empty for anything malformed, tampered with, signed with another key, or expired. */
    public Optional<Claims> verify(String token) {
        if (token == null || token.length() > 512) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot != token.lastIndexOf('.')) {
            return Optional.empty();
        }
        try {
            byte[] body = B64D.decode(token.substring(0, dot));
            byte[] signature = B64D.decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(sign(body), signature)) {
                return Optional.empty();
            }
            String[] parts = new String(body, StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 6 || !VERSION.equals(parts[0]) || !ROLES.contains(parts[3])) {
                return Optional.empty();
            }
            Instant expires = Instant.ofEpochSecond(Long.parseLong(parts[4]));
            if (!expires.isAfter(clock.instant())) {
                return Optional.empty();
            }
            return Optional.of(new Claims(UUID.fromString(parts[1]), parts[2], parts[3], expires));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is always available on the JVM", e);
        }
    }
}
