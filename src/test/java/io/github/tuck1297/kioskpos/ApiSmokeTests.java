package io.github.tuck1297.kioskpos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * End-to-end checks against a real server and a real Postgres (Testcontainers).
 * The "local" profile loads the two seeded demo locations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("local")
class ApiSmokeTests {

    private static final Pattern NEXT_CURSOR = Pattern.compile("\"nextCursor\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    int port;

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
        HttpResponse<String> res = get("/v1/orders?locationId=5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11");

        assertThat(res.statusCode()).isEqualTo(501);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
