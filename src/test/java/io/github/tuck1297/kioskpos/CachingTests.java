package io.github.tuck1297.kioskpos;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.tuck1297.kioskpos.menu.MenuCatalog;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Reads (quotes) are served from Redis; the write that takes money (create order) is
 * always checked against Postgres, even while the cache is stale.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("local")
class CachingTests {

    static final String LAKESIDE = "5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11";
    static final UUID HOT_DOG = UUID.fromString("2d3e4f5a-6b7c-4d8e-9f0a-1b2c3d4e5f44");

    @Autowired
    MenuCatalog catalog;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void quotesUseTheCacheButOrdersCheckTheDatabase() throws Exception {
        catalog.evict(UUID.fromString(LAKESIDE));
        String hotDogQuote = "{\"locationId\":\"" + LAKESIDE + "\",\"lines\":[{\"menuItemId\":\"" + HOT_DOG + "\",\"quantity\":1}]}";
        String hotDogOrder = "{\"locationId\":\"" + LAKESIDE + "\",\"channel\":\"COUNTER\",\"lines\":[{\"menuItemId\":\"" + HOT_DOG + "\",\"quantity\":1}]}";

        assertThat(post("/v1/orders/quote", hotDogQuote, null).statusCode()).isEqualTo(200);
        assertThat(redis.keys("kiosk:v1:*")).as("menu snapshot cached in Redis").anyMatch(k -> k.contains(LAKESIDE));

        // The item sells out behind the cache's back (e.g. changed directly in the database).
        jdbc.update("UPDATE menu_item SET available = false WHERE id = ?", HOT_DOG);
        try {
            HttpResponse<String> staleQuote = post("/v1/orders/quote", hotDogQuote, null);
            assertThat(staleQuote.statusCode()).as("quote is served from the cache").isEqualTo(200);

            HttpResponse<String> order = post("/v1/orders", hotDogOrder, UUID.randomUUID().toString());
            assertThat(order.statusCode()).as("order creation re-reads the database: %s", order.body()).isEqualTo(409);
            assertThat(order.body()).contains("ITEM_UNAVAILABLE", "sold out");
        } finally {
            jdbc.update("UPDATE menu_item SET available = true WHERE id = ?", HOT_DOG);
            catalog.evict(UUID.fromString(LAKESIDE));
        }

        assertThat(post("/v1/orders", hotDogOrder, UUID.randomUUID().toString()).statusCode()).isEqualTo(201);
    }

    private HttpResponse<String> post(String path, String json, String idempotencyKey) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (idempotencyKey != null) {
            b.header("Idempotency-Key", idempotencyKey);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
