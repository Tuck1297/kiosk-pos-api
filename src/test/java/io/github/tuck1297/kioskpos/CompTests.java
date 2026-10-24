package io.github.tuck1297.kioskpos;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Comps need a manager's ID + PIN, recompute tax on what's left, and close a $0 order.
 * Basket: 2 pretzels ($15.00) + 1 lemonade ($5.99) = $20.99, tax $1.36, total $22.35.
 */
class CompTests extends ApiTestSupport {

    static final String BASKET = """
            {"locationId":"%s","channel":"COUNTER","guestName":"Jordan","lines":[
              {"menuItemId":"9a3e7c51-2b4d-4e6f-8a1c-3d5e7f9b1c22","quantity":2},
              {"menuItemId":"1c2d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e33","quantity":1,"note":"no ice"}]}""".formatted(LAKESIDE);

    @Test
    void managerCompsOneItemThenTheRestAndTheOrderClosesAtZero() throws Exception {
        HttpResponse<String> created = post("/v1/orders", BASKET, UUID.randomUUID().toString(), null);
        String orderId = JsonPath.read(created.body(), "$.id");
        String lemonadeLine = JsonPath.read(created.body(), "$.lines[1].id");
        String path = "/v1/orders/" + orderId + "/comps";

        // Comp the lemonade: tax is now on $15.00 only (97.5 rounds to 98).
        String lineComp = comp(lemonadeLine, "QUALITY_ISSUE", "Lemonade was warm", MANAGER, MANAGER_PIN);
        HttpResponse<String> first = post(path, lineComp, UUID.randomUUID().toString(), "\"0\"");
        assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        String body = first.body();
        assertThat(JsonPath.<Integer>read(body, "$.compTotal.amountCents")).isEqualTo(599);
        assertThat(JsonPath.<Integer>read(body, "$.tax.amountCents")).isEqualTo(98);
        assertThat(JsonPath.<Integer>read(body, "$.total.amountCents")).isEqualTo(1598);
        assertThat(JsonPath.<Boolean>read(body, "$.lines[1].comped")).isTrue();
        assertThat(JsonPath.<Boolean>read(body, "$.lines[0].comped")).isFalse();
        assertThat(JsonPath.<String>read(body, "$.comps[0].approvedBy.employeeId")).isEqualTo(MANAGER);
        assertThat(JsonPath.<String>read(body, "$.comps[0].requestedBy.employeeId")).isEqualTo(CASHIER);
        assertThat(JsonPath.<String>read(body, "$.status")).isEqualTo("OPEN");
        assertThat(first.headers().firstValue("ETag")).hasValue("\"1\"");

        // The same item can't be comped twice.
        HttpResponse<String> again = post(path, lineComp, UUID.randomUUID().toString(), "\"1\"");
        assertThat(again.statusCode()).as(again.body()).isEqualTo(409);
        assertThat(again.body()).contains("COMP_NOT_ALLOWED");

        // Comp the rest: nothing left to collect, so the order closes as PAID at $0.00.
        HttpResponse<String> rest = post(path, comp(null, "GUEST_RECOVERY", null, MANAGER, MANAGER_PIN),
                UUID.randomUUID().toString(), "\"1\"");
        assertThat(rest.statusCode()).as(rest.body()).isEqualTo(201);
        assertThat(JsonPath.<Integer>read(rest.body(), "$.compTotal.amountCents")).isEqualTo(2099);
        assertThat(JsonPath.<Integer>read(rest.body(), "$.tax.amountCents")).isEqualTo(0);
        assertThat(JsonPath.<Integer>read(rest.body(), "$.total.amountCents")).isEqualTo(0);
        assertThat(JsonPath.<String>read(rest.body(), "$.status")).isEqualTo("PAID");
        assertThat(JsonPath.<Integer>read(rest.body(), "$.comps.length()")).isEqualTo(2);

        // A closed order can't be paid.
        HttpResponse<String> pay = post("/v1/orders/" + orderId + "/payments",
                "{\"tenderType\":\"CARD\",\"cardToken\":\"tok_visa_4242\"}", UUID.randomUUID().toString(), "\"2\"");
        assertThat(pay.statusCode()).isEqualTo(409);
    }

    @Test
    void onlyAManagersIdAndPinApprove() throws Exception {
        HttpResponse<String> created = post("/v1/orders", BASKET, UUID.randomUUID().toString(), null);
        String path = "/v1/orders/" + JsonPath.read(created.body(), "$.id") + "/comps";

        HttpResponse<String> cashier = post(path, comp(null, "GUEST_RECOVERY", null, CASHIER, CASHIER_PIN),
                UUID.randomUUID().toString(), "\"0\"");
        assertThat(cashier.statusCode()).as("a cashier can't approve: %s", cashier.body()).isEqualTo(403);
        assertThat(cashier.body()).contains("MANAGER_APPROVAL_FAILED");

        HttpResponse<String> wrongPin = post(path, comp(null, "GUEST_RECOVERY", null, MANAGER, "0000"),
                UUID.randomUUID().toString(), "\"0\"");
        assertThat(wrongPin.statusCode()).isEqualTo(403);
        assertThat(JsonPath.<String>read(wrongPin.body(), "$.detail"))
                .isEqualTo(JsonPath.<String>read(cashier.body(), "$.detail"));

        HttpResponse<String> order = get(path.replace("/comps", ""));
        assertThat(JsonPath.<Integer>read(order.body(), "$.total.amountCents")).as("nothing was comped").isEqualTo(2235);
    }

    @Test
    void retryWithTheSameKeyDoesNotCompTwice() throws Exception {
        HttpResponse<String> created = post("/v1/orders", BASKET, UUID.randomUUID().toString(), null);
        String orderId = JsonPath.read(created.body(), "$.id");
        String pretzelLine = JsonPath.read(created.body(), "$.lines[0].id");
        String key = UUID.randomUUID().toString();
        String body = comp(pretzelLine, "WRONG_ORDER", null, MANAGER, MANAGER_PIN);

        assertThat(post("/v1/orders/" + orderId + "/comps", body, key, "\"0\"").statusCode()).isEqualTo(201);
        HttpResponse<String> replay = post("/v1/orders/" + orderId + "/comps", body, key, "\"0\"");

        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(201);
        assertThat(JsonPath.<Integer>read(replay.body(), "$.comps.length()")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(replay.body(), "$.compTotal.amountCents")).isEqualTo(1500);
    }

    private static String comp(String lineId, String reason, String note, String managerId, String pin) {
        return "{" + (lineId == null ? "" : "\"orderLineId\":\"" + lineId + "\",")
                + "\"reason\":\"" + reason + "\","
                + (note == null ? "" : "\"note\":\"" + note + "\",")
                + "\"managerEmployeeId\":\"" + managerId + "\",\"managerPin\":\"" + pin + "\"}";
    }
}
