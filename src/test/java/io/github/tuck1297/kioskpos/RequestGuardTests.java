package io.github.tuck1297.kioskpos;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Guards reject bad requests before any controller or service runs. Proof: requests
 * aimed at orders that don't exist come back 400 (the guard), not 404 (the service).
 */
class RequestGuardTests extends ApiTestSupport {

    @Test
    void cardPaymentNeedsACardToken() throws Exception {
        HttpResponse<String> res = post("/v1/orders/" + UUID.randomUUID() + "/payments",
                "{\"tenderType\":\"CARD\"}", UUID.randomUUID().toString(), "\"0\"");

        assertThat(res.statusCode()).as(res.body()).isEqualTo(400);
        assertThat(fields(res)).containsExactly("cardToken");
    }

    @Test
    void cashPaymentListsEveryProblem() throws Exception {
        HttpResponse<String> res = post("/v1/orders/" + UUID.randomUUID() + "/payments",
                "{\"tenderType\":\"CASH\",\"cardToken\":\"tok_visa_4242\"}", UUID.randomUUID().toString(), "\"0\"");

        assertThat(res.statusCode()).as(res.body()).isEqualTo(400);
        assertThat(res.body()).contains("VALIDATION_FAILED");
        assertThat(fields(res)).containsExactlyInAnyOrder("cashTenderedCents", "cardToken");
    }

    @Test
    void compWithReasonOtherNeedsANote() throws Exception {
        HttpResponse<String> res = post("/v1/orders/" + UUID.randomUUID() + "/comps",
                "{\"reason\":\"OTHER\",\"managerEmployeeId\":\"M1001\",\"managerPin\":\"2468\"}",
                UUID.randomUUID().toString(), "\"0\"");

        assertThat(res.statusCode()).as(res.body()).isEqualTo(400);
        assertThat(fields(res)).containsExactly("note");
    }

    @Test
    void basketRulesApplyToQuotesAndOrders() throws Exception {
        String item = UUID.randomUUID().toString();
        String duplicate = "{\"locationId\":\"" + LAKESIDE + "\",\"channel\":\"COUNTER\",\"lines\":["
                + "{\"menuItemId\":\"" + item + "\",\"quantity\":1},{\"menuItemId\":\"" + item + "\",\"quantity\":2}]}";

        HttpResponse<String> quote = post("/v1/orders/quote", duplicate.replace("\"channel\":\"COUNTER\",", ""));
        assertThat(quote.statusCode()).as(quote.body()).isEqualTo(400);
        assertThat(fields(quote)).containsExactly("lines[1].menuItemId");

        StringBuilder tooMany = new StringBuilder("{\"locationId\":\"" + LAKESIDE + "\",\"channel\":\"COUNTER\",\"lines\":[");
        for (int i = 0; i < 4; i++) {
            tooMany.append(i == 0 ? "" : ",").append("{\"menuItemId\":\"").append(UUID.randomUUID()).append("\",\"quantity\":20}");
        }
        HttpResponse<String> order = post("/v1/orders", tooMany + "]}", UUID.randomUUID().toString(), null);
        assertThat(order.statusCode()).as(order.body()).isEqualTo(400);
        assertThat(fields(order)).containsExactly("lines");
    }

    @Test
    void schemaRulesStillApply() throws Exception {
        HttpResponse<String> res = post("/v1/orders/quote",
                "{\"locationId\":\"" + LAKESIDE + "\",\"lines\":[{\"menuItemId\":\"" + UUID.randomUUID() + "\",\"quantity\":0}]}");

        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(fields(res)).containsExactly("lines[0].quantity");
    }

    private static List<String> fields(HttpResponse<String> res) {
        List<Map<String, Object>> errors = JsonPath.read(res.body(), "$.errors");
        return errors.stream().map(e -> String.valueOf(e.get("field"))).toList();
    }
}
