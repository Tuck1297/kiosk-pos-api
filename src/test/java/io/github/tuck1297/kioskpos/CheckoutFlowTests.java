package io.github.tuck1297.kioskpos;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The cashier's checkout end to end: menu, quote, create order, pay. Runs against a real
 * server and Postgres (Testcontainers) with the seeded Lakeside Snack Stand at 6.50% tax.
 */
class CheckoutFlowTests extends ApiTestSupport {

    static final String PRETZEL = "9a3e7c51-2b4d-4e6f-8a1c-3d5e7f9b1c22";   // $7.50
    static final String LEMONADE = "1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e33";  // $5.99
    static final String CHURRO = "3e4f5a6b-7c8d-4e9f-0a1b-2c3d4e5f6a55";    // sold out
    static final String COLLECTOR_PIN = "4f5a6b7c-8d9e-4f0a-1b2c-3d4e5f6a7b66";       // other location

    /** 2 pretzels + 1 lemonade = $20.99, tax $1.36, total $22.35 */
    static final String BASKET = """
            {"locationId":"%s","channel":"COUNTER","guestName":"Jordan","lines":[
              {"menuItemId":"%s","quantity":2},
              {"menuItemId":"%s","quantity":1,"note":"no ice"}]}""".formatted(LAKESIDE, PRETZEL, LEMONADE);

    @Test
    void menuListsOnlyAvailableItemsWhenAsked() throws Exception {
        HttpResponse<String> res = get("/v1/locations/" + LAKESIDE + "/menu-items?available=true&limit=100");

        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        assertThat(res.body()).contains("Soft Pretzel", "Frozen Lemonade", "Grilled Hot Dog").doesNotContain("Churro");
        assertThat(JsonPath.<Integer>read(res.body(), "$.items[0].price.amountCents")).isPositive();
    }

    @Test
    void quoteComputesTotalsOnTheServer() throws Exception {
        HttpResponse<String> res = post("/v1/orders/quote", BASKET.replace("\"channel\":\"COUNTER\",\"guestName\":\"Jordan\",", ""));

        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        String body = res.body();
        assertThat(JsonPath.<Integer>read(body, "$.subtotal.amountCents")).isEqualTo(2099);
        assertThat(JsonPath.<Integer>read(body, "$.tax.amountCents")).isEqualTo(136);
        assertThat(JsonPath.<Integer>read(body, "$.total.amountCents")).isEqualTo(2235);
        assertThat(JsonPath.<Integer>read(body, "$.taxRate.rateBps")).isEqualTo(650);
        assertThat(JsonPath.<String>read(body, "$.taxRate.code")).isEqualTo("PARK-STD");
        assertThat(JsonPath.<Integer>read(body, "$.lines[0].lineTotal.amountCents")).isEqualTo(1500);
        assertThat(JsonPath.<String>read(body, "$.lines[1].note")).isEqualTo("no ice");
    }

    @Test
    void quoteRejectsSoldOutAndOtherLocationItems() throws Exception {
        HttpResponse<String> soldOut = post("/v1/orders/quote", quoteOf(CHURRO), null, null);
        assertThat(soldOut.statusCode()).as(soldOut.body()).isEqualTo(409);
        assertThat(soldOut.body()).contains("ITEM_UNAVAILABLE", "sold out");

        HttpResponse<String> elsewhere = post("/v1/orders/quote", quoteOf(COLLECTOR_PIN), null, null);
        assertThat(elsewhere.statusCode()).as(elsewhere.body()).isEqualTo(409);
        assertThat(elsewhere.body()).contains("ITEM_UNAVAILABLE");
    }

    @Test
    void quoteRejectsEmptyBasketAndUnknownLocation() throws Exception {
        HttpResponse<String> empty = post("/v1/orders/quote",
                "{\"locationId\":\"" + LAKESIDE + "\",\"lines\":[]}", null, null);
        assertThat(empty.statusCode()).as(empty.body()).isEqualTo(400);

        HttpResponse<String> unknown = post("/v1/orders/quote",
                quoteOf(PRETZEL).replace(LAKESIDE, UUID.randomUUID().toString()), null, null);
        assertThat(unknown.statusCode()).as(unknown.body()).isEqualTo(404);
    }

    @Test
    void createOrderIsIdempotentAndCashPaymentReturnsServerChange() throws Exception {
        String key = UUID.randomUUID().toString();
        HttpResponse<String> created = post("/v1/orders", BASKET, key, null);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String orderId = JsonPath.read(created.body(), "$.id");
        assertThat(JsonPath.<Integer>read(created.body(), "$.total.amountCents")).isEqualTo(2235);
        assertThat(JsonPath.<Integer>read(created.body(), "$.taxRateBps")).isEqualTo(650);
        assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("OPEN");
        assertThat(JsonPath.<String>read(created.body(), "$.createdBy.employeeId")).isEqualTo(CASHIER);
        String etag = created.headers().firstValue("ETag").orElseThrow();

        // Same key, same body: same order back, not a second one.
        HttpResponse<String> retried = post("/v1/orders", BASKET, key, null);
        assertThat(retried.statusCode()).isEqualTo(201);
        assertThat(JsonPath.<String>read(retried.body(), "$.id")).isEqualTo(orderId);

        // Same key, different body: rejected.
        HttpResponse<String> reused = post("/v1/orders", BASKET.replace("Jordan", "Someone else"), key, null);
        assertThat(reused.statusCode()).as(reused.body()).isEqualTo(422);
        assertThat(reused.body()).contains("IDEMPOTENCY_KEY_REUSED");

        // Not enough cash.
        HttpResponse<String> short20 = post("/v1/orders/" + orderId + "/payments",
                "{\"tenderType\":\"CASH\",\"cashTenderedCents\":2000}", UUID.randomUUID().toString(), etag);
        assertThat(short20.statusCode()).as(short20.body()).isEqualTo(400);
        assertThat(short20.body()).contains("INSUFFICIENT_CASH");

        // $30 cash: the server computes $7.65 change.
        String payKey = UUID.randomUUID().toString();
        String cash = "{\"tenderType\":\"CASH\",\"cashTenderedCents\":3000}";
        HttpResponse<String> paid = post("/v1/orders/" + orderId + "/payments", cash, payKey, etag);
        assertThat(paid.statusCode()).as(paid.body()).isEqualTo(201);
        assertThat(JsonPath.<Integer>read(paid.body(), "$.changeDue.amountCents")).isEqualTo(765);
        assertThat(JsonPath.<String>read(paid.body(), "$.status")).isEqualTo("CAPTURED");

        // A network retry of the same payment replays it instead of charging again.
        HttpResponse<String> replay = post("/v1/orders/" + orderId + "/payments", cash, payKey, etag);
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(201);
        assertThat(JsonPath.<String>read(replay.body(), "$.id")).isEqualTo(JsonPath.<String>read(paid.body(), "$.id"));

        // A second, different payment attempt is refused.
        HttpResponse<String> again = post("/v1/orders/" + orderId + "/payments", cash,
                UUID.randomUUID().toString(), "\"1\"");
        assertThat(again.statusCode()).as(again.body()).isEqualTo(409);

        HttpResponse<String> order = get("/v1/orders/" + orderId);
        assertThat(JsonPath.<String>read(order.body(), "$.status")).isEqualTo("PAID");
        assertThat(order.headers().firstValue("ETag")).hasValue("\"1\"");
    }

    @Test
    void declinedCardKeepsOrderOpenThenAnotherCardSucceeds() throws Exception {
        HttpResponse<String> created = post("/v1/orders", BASKET, UUID.randomUUID().toString(), null);
        String orderId = JsonPath.read(created.body(), "$.id");
        String etag = created.headers().firstValue("ETag").orElseThrow();

        HttpResponse<String> declined = post("/v1/orders/" + orderId + "/payments",
                "{\"tenderType\":\"CARD\",\"cardToken\":\"tok_decline_insufficient_funds_0002\"}",
                UUID.randomUUID().toString(), etag);
        assertThat(declined.statusCode()).as(declined.body()).isEqualTo(402);
        assertThat(declined.body()).contains("PAYMENT_DECLINED", "insufficient_funds");

        HttpResponse<String> approved = post("/v1/orders/" + orderId + "/payments",
                "{\"tenderType\":\"CARD\",\"cardToken\":\"tok_visa_4242\"}", UUID.randomUUID().toString(), etag);
        assertThat(approved.statusCode()).as(approved.body()).isEqualTo(201);
        assertThat(JsonPath.<String>read(approved.body(), "$.cardLast4")).isEqualTo("4242");
        assertThat(JsonPath.<String>read(approved.body(), "$.cardBrand")).isEqualTo("VISA");

        HttpResponse<String> history = get("/v1/orders/" + orderId + "/payments");
        assertThat(JsonPath.<Integer>read(history.body(), "$.items.length()")).isEqualTo(2);
    }

    @Test
    void staleIfMatchIs412() throws Exception {
        HttpResponse<String> created = post("/v1/orders", BASKET, UUID.randomUUID().toString(), null);
        String orderId = JsonPath.read(created.body(), "$.id");

        HttpResponse<String> stale = post("/v1/orders/" + orderId + "/payments",
                "{\"tenderType\":\"CARD\",\"cardToken\":\"tok_visa_4242\"}", UUID.randomUUID().toString(), "\"7\"");
        assertThat(stale.statusCode()).as(stale.body()).isEqualTo(412);
        assertThat(stale.body()).contains("STALE_VERSION");
    }

    private static String quoteOf(String menuItemId) {
        return "{\"locationId\":\"" + LAKESIDE + "\",\"lines\":[{\"menuItemId\":\"" + menuItemId + "\",\"quantity\":1}]}";
    }
}
