package io.github.tuck1297.kioskpos;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Sign-in with an HTTP-only session cookie, the CSRF header, and PIN lockout. */
class AuthTests extends ApiTestSupport {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void everythingButHealthAndLoginNeedsASession() throws Exception {
        HttpResponse<String> res = send("GET", "/v1/locations", null, null, null, null, true);

        assertThat(res.statusCode()).isEqualTo(401);
        assertThat(res.headers().firstValue("Content-Type")).hasValueSatisfying(
                t -> assertThat(t).startsWith("application/problem+json"));
        assertThat(res.body()).contains("UNAUTHORIZED");
        assertThat(send("GET", "/v1/health", null, null, null, null, true).statusCode()).isEqualTo(200);
    }

    @Test
    void signInSetsAnHttpOnlyStrictCookieAndMeKnowsWhoYouAre() throws Exception {
        HttpResponse<String> res = send("POST", "/v1/auth/login",
                "{\"employeeId\":\"C2001\",\"pin\":\"1111\"}", null, null, null, true);

        assertThat(res.statusCode()).isEqualTo(200);
        String cookie = res.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(cookie).startsWith("kiosk_session=").contains("HttpOnly", "SameSite=Strict", "Path=/v1");
        assertThat(JsonPath.<String>read(res.body(), "$.role")).isEqualTo("CASHIER");

        HttpResponse<String> me = get("/v1/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(me.body(), "$.employeeId")).isEqualTo(CASHIER);
        assertThat(JsonPath.<String>read(me.body(), "$.name")).isEqualTo("Alex P.");
    }

    @Test
    void wrongPinAndUnknownIdLookTheSame() throws Exception {
        HttpResponse<String> wrongPin = login("C2001", "9999");
        HttpResponse<String> unknown = login("X9999", "1111");

        assertThat(wrongPin.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(wrongPin.body(), "$.detail"))
                .isEqualTo(JsonPath.<String>read(unknown.body(), "$.detail"));
        assertThat(wrongPin.body()).contains("INVALID_CREDENTIALS");
        login("C2001", "1111"); // reset the cashier's failed-attempt count
    }

    @Test
    void tamperedOrMissingCookieIsRejected() throws Exception {
        String tampered = session.substring(0, session.length() - 2) + (session.endsWith("A") ? "BB" : "AA");

        assertThat(send("GET", "/v1/auth/me", null, null, null, tampered, true).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/v1/auth/me", null, null, null, "kiosk_session=junk", true).statusCode()).isEqualTo(401);
    }

    @Test
    void changesWithoutTheCsrfHeaderAreRefused() throws Exception {
        String quote = "{\"locationId\":\"" + LAKESIDE + "\",\"lines\":[{\"menuItemId\":\"9a3e7c51-2b4d-4e6f-8a1c-3d5e7f9b1c22\",\"quantity\":1}]}";

        HttpResponse<String> res = send("POST", "/v1/orders/quote", quote, null, null, session, false);
        assertThat(res.statusCode()).isEqualTo(403);
        assertThat(res.body()).contains("CSRF_HEADER_MISSING");

        assertThat(send("POST", "/v1/auth/login", "{\"employeeId\":\"C2001\",\"pin\":\"1111\"}", null, null, null, false)
                .statusCode()).isEqualTo(403);
        assertThat(send("POST", "/v1/orders/quote", quote, null, null, session, true).statusCode()).isEqualTo(200);
    }

    @Test
    void signOutClearsTheCookie() throws Exception {
        HttpResponse<String> res = post("/v1/auth/logout", null);

        assertThat(res.statusCode()).isEqualTo(204);
        assertThat(res.headers().firstValue("Set-Cookie")).hasValueSatisfying(
                c -> assertThat(c).startsWith("kiosk_session=;").contains("Max-Age=0", "HttpOnly"));
    }

    @Test
    void fiveWrongPinsLockTheAccount() throws Exception {
        try {
            for (int i = 1; i <= 4; i++) {
                assertThat(login("M1002", "0000").statusCode()).as("attempt %d", i).isEqualTo(401);
            }
            HttpResponse<String> fifth = login("M1002", "0000");
            assertThat(fifth.statusCode()).isEqualTo(423);
            assertThat(fifth.body()).contains("ACCOUNT_LOCKED");

            // Even the right PIN is refused while locked.
            assertThat(login("M1002", "1357").statusCode()).isEqualTo(423);
        } finally {
            jdbc.update("UPDATE staff SET failed_attempts = 0, locked_until = NULL WHERE employee_id = 'M1002'");
        }
        assertThat(login("M1002", "1357").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> login(String employeeId, String pin) throws Exception {
        return send("POST", "/v1/auth/login",
                "{\"employeeId\":\"" + employeeId + "\",\"pin\":\"" + pin + "\"}", null, null, null, true);
    }
}
