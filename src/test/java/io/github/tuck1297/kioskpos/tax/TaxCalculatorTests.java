package io.github.tuck1297.kioskpos.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class TaxCalculatorTests {

    @ParameterizedTest(name = "{0} cents at {1} bps = {2} cents")
    @CsvSource({
            "2099, 650, 136",   // 136.435 rounds down
            "2049, 650, 133",   // 133.185
            "3796, 650, 247",   // 246.74 rounds up
            "1500, 650, 98",    // exactly 97.5 rounds half up
            "0, 650, 0",
            "1000, 0, 0",
            "6000000000, 3000, 1800000000"  // large basket, no overflow
    })
    void roundsHalfUpToTheCent(long subtotal, int bps, long expected) {
        assertThat(TaxCalculator.taxCents(subtotal, bps)).isEqualTo(expected);
    }

    @Test
    void rejectsNegativeInput() {
        assertThatThrownBy(() -> TaxCalculator.taxCents(-1, 650)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TaxCalculator.taxCents(100, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
