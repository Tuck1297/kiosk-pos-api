package io.github.tuck1297.kioskpos.payment;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Stand-in for a real card processor. Tokens starting with {@code tok_decline} are
 * declined; {@code tok_decline_insufficient_funds_0002} declines with code
 * {@code insufficient_funds}. Everything else is approved. Trailing digits become last4.
 */
@Component
public class MockCardProcessor {

    public record CardResult(boolean approved, String last4, String reference, String declineCode) {
    }

    private static final Pattern LAST4 = Pattern.compile("(\\d{4})$");
    private static final Pattern DECLINE = Pattern.compile("^tok_decline(?:_([a-z_]+?))?(?:_\\d{4})?$");

    public CardResult charge(String token, long amountCents) {
        Matcher digits = LAST4.matcher(token);
        String last4 = digits.find() ? digits.group(1) : null;

        if (token.startsWith("tok_decline")) {
            Matcher decline = DECLINE.matcher(token);
            String code = decline.matches() && decline.group(1) != null ? decline.group(1) : "card_declined";
            return new CardResult(false, last4, null, code);
        }
        return new CardResult(true, last4, "mock_ch_" + UUID.randomUUID().toString().substring(0, 8), null);
    }
}
