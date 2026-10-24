package io.github.tuck1297.kioskpos.payment;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Stand-in for a real card processor. It only ever sees a token, never a card number: the
 * register tokenizes the card first, as a real processor's SDK would, so card numbers stay
 * out of this API (and out of PCI scope).
 *
 * Tokens look like {@code tok_visa_4242}. A token starting with {@code tok_decline} is
 * declined ({@code tok_decline_insufficient_funds_0002} with code insufficient_funds);
 * anything else is approved. The brand and the trailing digits go on the receipt.
 */
@Component
public class MockCardProcessor {

    public record CardResult(boolean approved, String brand, String last4, String reference, String declineCode) {
    }

    private static final Pattern LAST4 = Pattern.compile("(\\d{4})$");
    private static final Pattern BRAND = Pattern.compile("^tok_(visa|mastercard|amex|discover)_");
    private static final Pattern DECLINE = Pattern.compile("^tok_decline(?:_([a-z_]+?))?(?:_\\d{4})?$");

    public CardResult charge(String token, long amountCents) {
        Matcher digits = LAST4.matcher(token);
        String last4 = digits.find() ? digits.group(1) : null;
        Matcher brandMatch = BRAND.matcher(token);
        String brand = brandMatch.find() ? brandMatch.group(1).toUpperCase(Locale.ROOT) : null;

        if (token.startsWith("tok_decline")) {
            Matcher decline = DECLINE.matcher(token);
            String code = decline.matches() && decline.group(1) != null ? decline.group(1) : "card_declined";
            return new CardResult(false, brand, last4, null, code);
        }
        return new CardResult(true, brand, last4, "mock_ch_" + UUID.randomUUID().toString().substring(0, 8), null);
    }
}
