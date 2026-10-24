package io.github.tuck1297.kioskpos.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 of a request's meaningful fields, so a reused Idempotency-Key with a
 * different request can be told apart from a genuine retry.
 */
public final class RequestFingerprint {

    private static final char SEPARATOR = '\u001f';
    private static final String NULL = "\u0000";

    private RequestFingerprint() {
    }

    public static String of(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object part : parts) {
            sb.append(part == null ? NULL : part.toString()).append(SEPARATOR);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available on the JVM", e);
        }
    }
}
