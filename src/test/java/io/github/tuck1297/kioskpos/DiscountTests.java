package io.github.tuck1297.kioskpos;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Promo codes and the employee discount, priced on the server, with tax on the discounted
 * subtotal. Basket: 2 pretzels ($15.00) + 1 lemonade ($5.99) = $20.99 at 6.50% tax.
 */
class DiscountTests extends ApiTestSupport {

    static final String LINES = """
            "lines":[{"menuItemId":"9a3e7c51-2b4d-4e6f-8a1c-3d5e7f9b1c22","quantity":2},
                     {"menuItemId":"1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e33","quantity":1}]""";
    static final String ONE_LEMONADE = """
            "lines":[{"menuItemId":"1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e33","quantity":1}]""";

    @Test
    void percentCodeTakesTenPercentAndTaxesTheRest() throws Exception {
        HttpResponse<String> res = quote(LINES, "{\"code\":\"parkfan10\"}");

        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        String b = res.body();
        // 10% of 2099 = 209.9 -> 210; tax on 1889 = 122.785 -> 123; total 2012
        assertThat(JsonPath.<Integer>read(b, "$.discountTotal.amountCents")).isEqualTo(210);
        assertThat(JsonPath.<Integer>read(b, "$.tax.amountCents")).isEqualTo(123);
        assertThat(JsonPath.<Integer>read(b, "$.total.amountCents")).isEqualTo(2012);
        assertThat(JsonPath.<String>read(b, "$.discount.kind")).isEqualTo("CODE");
        assertThat(JsonPath.<String>read(b, "$.discount.code")).isEqualTo("PARKFAN10");
        assertThat(JsonPath.<Integer>read(b, "$.discount.rateBps")).isEqualTo(1000);
        assertThat(JsonPath.<String>read(b, "$.discount.label")).contains("PARKFAN10", "10%");
    }

    @Test
    void employeeDiscountIsTwentyPercentByEmployeeId() throws Exception {
        HttpResponse<String> res = quote(LINES, "{\"employeeId\":\"C2001\"}");

        assertThat(res.statusCode()).as(res.body()).isEqualTo(200);
        String b = res.body();
        // 20% of 2099 = 419.8 -> 420; tax on 1679 = 109.135 -> 109; total 1788
        assertThat(JsonPath.<Integer>read(b, "$.discountTotal.amountCents")).isEqualTo(420);
        assertThat(JsonPath.<Integer>read(b, "$.total.amountCents")).isEqualTo(1788);
        assertThat(JsonPath.<String>read(b, "$.discount.kind")).isEqualTo("EMPLOYEE");
        assertThat(JsonPath.<String>read(b, "$.discount.employee.name")).isEqualTo("Alex P.");
    }

    @Test
    void amountCodeNeedsItsMinimum() throws Exception {
        HttpResponse<String> tooSmall = quote(ONE_LEMONADE, "{\"code\":\"WELCOME5\"}");
        assertThat(tooSmall.statusCode()).as(tooSmall.body()).isEqualTo(409);
        assertThat(tooSmall.body()).contains("DISCOUNT_NOT_APPLICABLE", "$20.00");

        HttpResponse<String> ok = quote(LINES, "{\"code\":\"WELCOME5\"}");
        assertThat(ok.statusCode()).isEqualTo(200);
        // 500 off; tax on 1599 = 103.935 -> 104; total 1703
        assertThat(JsonPath.<Integer>read(ok.body(), "$.discountTotal.amountCents")).isEqualTo(500);
        assertThat(JsonPath.<Integer>read(ok.body(), "$.total.amountCents")).isEqualTo(1703);
    }

    @Test
    void expiredUnknownAndBadEmployeeAreExplained() throws Exception {
        HttpResponse<String> expired = quote(LINES, "{\"code\":\"SUMMER25\"}");
        assertThat(expired.statusCode()).isEqualTo(409);
        assertThat(expired.body()).contains("DISCOUNT_NOT_APPLICABLE", "expired");

        HttpResponse<String> unknown = quote(LINES, "{\"code\":\"NOPE123\"}");
        assertThat(unknown.statusCode()).isEqualTo(409);
        assertThat(unknown.body()).contains("isn't a valid code");

        HttpResponse<String> employee = quote(LINES, "{\"employeeId\":\"X9999\"}");
        assertThat(employee.statusCode()).isEqualTo(409);
        assertThat(employee.body()).contains("isn't recognized");
    }

    @Test
    void guardWantsExactlyOneOfCodeOrEmployee() throws Exception {
        HttpResponse<String> both = quote(LINES, "{\"code\":\"PARKFAN10\",\"employeeId\":\"C2001\"}");
        assertThat(both.statusCode()).as(both.body()).isEqualTo(400);
        assertThat(both.body()).contains("\"field\":\"discount\"");

        HttpResponse<String> neither = quote(LINES, "{}");
        assertThat(neither.statusCode()).isEqualTo(400);
    }

    @Test
    void cappedCodeStopsAtItsLimit() throws Exception {
        for (int i = 1; i <= 3; i++) {
            HttpResponse<String> order = createOrder(LINES, "{\"code\":\"LIMITED50\"}");
            assertThat(order.statusCode()).as("redemption %d: %s", i, order.body()).isEqualTo(201);
        }
        HttpResponse<String> fourth = createOrder(LINES, "{\"code\":\"LIMITED50\"}");
        assertThat(fourth.statusCode()).as(fourth.body()).isEqualTo(409);
        assertThat(fourth.body()).contains("redemption limit");
    }

    @Test
    void ordersKeepTheDiscountAndCompsComeOffAfterIt() throws Exception {
        HttpResponse<String> created = createOrder(LINES, "{\"code\":\"PARKFAN10\"}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        String orderId = JsonPath.read(created.body(), "$.id");
        assertThat(JsonPath.<Integer>read(created.body(), "$.discountTotal.amountCents")).isEqualTo(210);
        assertThat(JsonPath.<Integer>read(created.body(), "$.total.amountCents")).isEqualTo(2012);
        assertThat(JsonPath.<String>read(created.body(), "$.discount.code")).isEqualTo("PARKFAN10");

        // Comp the lemonade: its $5.99 less its share of the discount (210 x 599/2099 = 59) = 540.
        // Taxable 2099 - 210 - 540 = 1349; tax 87.685 -> 88; total 1437.
        String lemonade = JsonPath.read(created.body(), "$.lines[1].id");
        HttpResponse<String> comped = post("/v1/orders/" + orderId + "/comps",
                "{\"orderLineId\":\"" + lemonade + "\",\"reason\":\"QUALITY_ISSUE\",\"managerEmployeeId\":\"M1001\",\"managerPin\":\"2468\"}",
                UUID.randomUUID().toString(), "\"0\"");
        assertThat(comped.statusCode()).as(comped.body()).isEqualTo(201);
        assertThat(JsonPath.<Integer>read(comped.body(), "$.compTotal.amountCents")).isEqualTo(540);
        assertThat(JsonPath.<Integer>read(comped.body(), "$.total.amountCents")).isEqualTo(1437);

        // The rest: total reaches 0 and the order closes.
        HttpResponse<String> rest = post("/v1/orders/" + orderId + "/comps",
                "{\"reason\":\"GUEST_RECOVERY\",\"managerEmployeeId\":\"M1001\",\"managerPin\":\"2468\"}",
                UUID.randomUUID().toString(), "\"1\"");
        assertThat(rest.statusCode()).as(rest.body()).isEqualTo(201);
        assertThat(JsonPath.<Integer>read(rest.body(), "$.total.amountCents")).isEqualTo(0);
        assertThat(JsonPath.<String>read(rest.body(), "$.status")).isEqualTo("PAID");
    }

    private HttpResponse<String> quote(String lines, String discount) throws Exception {
        return post("/v1/orders/quote", "{\"locationId\":\"" + LAKESIDE + "\"," + lines + ",\"discount\":" + discount + "}");
    }

    private HttpResponse<String> createOrder(String lines, String discount) throws Exception {
        return post("/v1/orders", "{\"locationId\":\"" + LAKESIDE + "\",\"channel\":\"COUNTER\"," + lines
                + ",\"discount\":" + discount + "}", UUID.randomUUID().toString(), null);
    }
}
