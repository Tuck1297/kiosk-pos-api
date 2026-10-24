package io.github.tuck1297.kioskpos.common;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque pagination cursor. Clients treat it as a string; internally it is the
 * last id of the previous page, so we can change the encoding later without
 * breaking the contract.
 */
public final class Cursor {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private Cursor() {
    }

    public static String encode(UUID lastId) {
        return ENCODER.encodeToString(lastId.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws InvalidCursorException if the value was not produced by {@link #encode(UUID)}
     */
    public static UUID decode(String cursor) {
        try {
            return UUID.fromString(new String(DECODER.decode(cursor), StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new InvalidCursorException(cursor);
        }
    }

    public static final class InvalidCursorException extends RuntimeException {
        public InvalidCursorException(String cursor) {
            super("Invalid cursor: " + cursor);
        }
    }
}
