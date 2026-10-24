package io.github.tuck1297.kioskpos;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** End-to-end checks of the basics, signed in as the demo cashier. */
class ApiSmokeTests extends ApiTestSupport {

    private static final Pattern NEXT_CURSOR = Pattern.compile("\"nextCursor\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    void healthIsUpUnderV1() throws Exception {
        HttpResponse<String> res = get("/v1/health");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"status\"").contains("UP");
    }

    @Test
    void actuatorHealthIsNotPrefixed() throws Exception {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
    }

    @Test
    void locationsArePagedWithCursor() throws Exception {
        HttpResponse<String> first = get("/v1/locations?limit=1");
        assertThat(first.statusCode()).isEqualTo(200);

        Matcher cursor = NEXT_CURSOR.matcher(first.body());
        assertThat(cursor.find()).as("first page has a nextCursor: %s", first.body()).isTrue();

        HttpResponse<String> second = get("/v1/locations?limit=1&cursor=" + cursor.group(1));
        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(NEXT_CURSOR.matcher(second.body()).find())
                .as("two seeded locations, so the second page is the last: %s", second.body())
                .isFalse();
    }

    @Test
    void unknownLocationIsProblemJson404() throws Exception {
        HttpResponse<String> res = get("/v1/locations/" + UUID.randomUUID());

        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(res.body()).contains("NOT_FOUND");
    }

    @Test
    void badCursorIs400() throws Exception {
        HttpResponse<String> res = get("/v1/locations?cursor=not-a-cursor");

        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(res.body()).contains("VALIDATION_FAILED");
    }

    @Test
    void limitAboveMaximumIs400() throws Exception {
        HttpResponse<String> res = get("/v1/locations?limit=500");

        assertThat(res.statusCode()).as(res.body()).isEqualTo(400);
        assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/problem+json"));
        assertThat(res.body()).contains("VALIDATION_FAILED").contains("\"field\":\"limit\"");
    }

    @Test
    void unimplementedEndpointsAre501() throws Exception {
        HttpResponse<String> res = get("/v1/orders?locationId=" + LAKESIDE);

        assertThat(res.statusCode()).isEqualTo(501);
    }
}
