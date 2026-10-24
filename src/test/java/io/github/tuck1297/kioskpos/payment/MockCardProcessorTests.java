package io.github.tuck1297.kioskpos.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MockCardProcessorTests {

    private final MockCardProcessor processor = new MockCardProcessor();

    @Test
    void approvesNormalTokens() {
        MockCardProcessor.CardResult r = processor.charge("tok_visa_4242", 2235);

        assertThat(r.approved()).isTrue();
        assertThat(r.brand()).isEqualTo("VISA");
        assertThat(r.last4()).isEqualTo("4242");
        assertThat(processor.charge("tok_amex_0005", 1).brand()).isEqualTo("AMEX");
        assertThat(processor.charge("tok_mastercard_4444", 1).brand()).isEqualTo("MASTERCARD");
        assertThat(r.reference()).startsWith("mock_ch_");
        assertThat(r.declineCode()).isNull();
    }

    @Test
    void declinesWithCodeFromToken() {
        MockCardProcessor.CardResult r = processor.charge("tok_decline_insufficient_funds_0002", 2235);

        assertThat(r.approved()).isFalse();
        assertThat(r.declineCode()).isEqualTo("insufficient_funds");
        assertThat(r.last4()).isEqualTo("0002");
    }

    @Test
    void declinesWithGenericCode() {
        assertThat(processor.charge("tok_decline", 100).declineCode()).isEqualTo("card_declined");
        assertThat(processor.charge("tok_decline_0002", 100).declineCode()).isEqualTo("card_declined");
    }

    @Test
    void last4IsNullWithoutTrailingDigits() {
        assertThat(processor.charge("tok_visa", 100).last4()).isNull();
    }
}
