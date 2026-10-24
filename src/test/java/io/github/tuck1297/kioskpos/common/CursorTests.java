package io.github.tuck1297.kioskpos.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CursorTests {

    @Test
    void roundTrips() {
        UUID id = UUID.randomUUID();

        assertThat(Cursor.decode(Cursor.encode(id))).isEqualTo(id);
    }

    @Test
    void isUrlSafe() {
        assertThat(Cursor.encode(UUID.randomUUID())).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> Cursor.decode("not-a-cursor"))
                .isInstanceOf(Cursor.InvalidCursorException.class);
        assertThatThrownBy(() -> Cursor.decode("%%%"))
                .isInstanceOf(Cursor.InvalidCursorException.class);
    }
}
