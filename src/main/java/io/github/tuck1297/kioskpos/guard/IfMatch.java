package io.github.tuck1297.kioskpos.guard;

import io.github.tuck1297.kioskpos.common.ApiException;

/** Guard for the If-Match header that order-changing calls send back. */
public final class IfMatch {

    private IfMatch() {
    }

    /** Accepts {@code "3"}, {@code W/"3"} or {@code 3}; anything else is a 400. */
    public static int version(String ifMatch) {
        String value = ifMatch == null ? "" : ifMatch.strip();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            int version = Integer.parseInt(value);
            if (version >= 0) {
                return version;
            }
        } catch (NumberFormatException e) {
            // fall through
        }
        throw ApiException.validation("VALIDATION_FAILED", "If-Match", "must be the order's ETag, e.g. \"0\"");
    }
}
