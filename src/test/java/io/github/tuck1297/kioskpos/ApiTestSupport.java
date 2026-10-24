package io.github.tuck1297.kioskpos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base for end-to-end tests: a real server on a random port, real Postgres and Redis
 * (Testcontainers), and the "local" profile's demo data. Signs in as the demo cashier
 * before each test; requests carry the session cookie and the CSRF header like the
 * register does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("local")
public abstract class ApiTestSupport {

    protected static final String LAKESIDE = "5b1f6a2e-8c1d-4f3a-9e7b-2d4c6a8e0f11";
    protected static final String CASHIER = "C2001";
    protected static final String CASHIER_PIN = "1111";
    protected static final String MANAGER = "M1001";
    protected static final String MANAGER_PIN = "2468";

    protected final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    protected int port;

    /** The kiosk_session cookie ("kiosk_session=..."), or null when signed out. */
    protected String session;

    @BeforeEach
    void signInAsCashier() throws Exception {
        session = signIn(CASHIER, CASHIER_PIN);
    }

    protected String signIn(String employeeId, String pin) throws Exception {
        HttpResponse<String> res = send("POST", "/v1/auth/login",
                "{\"employeeId\":\"" + employeeId + "\",\"pin\":\"" + pin + "\"}", null, null, null, true);
        assertThat(res.statusCode()).as("sign in as %s: %s", employeeId, res.body()).isEqualTo(200);
        String setCookie = res.headers().firstValue("Set-Cookie").orElseThrow();
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    protected HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return send("GET", path, null, null, null, session, true);
    }

    protected HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
        return send("POST", path, json, null, null, session, true);
    }

    protected HttpResponse<String> post(String path, String json, String idempotencyKey, String ifMatch)
            throws IOException, InterruptedException {
        return send("POST", path, json, idempotencyKey, ifMatch, session, true);
    }

    protected HttpResponse<String> send(String method, String path, String json, String idempotencyKey,
                                        String ifMatch, String cookie, boolean csrfHeader)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (json != null) {
            b.header("Content-Type", "application/json");
        }
        if (csrfHeader) {
            b.header("X-Kiosk-Register", "1");
        }
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        if (idempotencyKey != null) {
            b.header("Idempotency-Key", idempotencyKey);
        }
        if (ifMatch != null) {
            b.header("If-Match", ifMatch);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }
}
