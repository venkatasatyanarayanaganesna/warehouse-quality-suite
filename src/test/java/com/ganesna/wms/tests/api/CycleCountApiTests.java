package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@Epic("API")
@Feature("Cycle counts")
public class CycleCountApiTests {

    private String admin;
    private String picker;

    @BeforeClass
    public void tokens() {
        admin = ApiClient.adminToken();
        picker = ApiClient.pickerToken();
    }

    private static Map<String, Object> json(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    /** Creates a count for one fresh bin holding one item of the given quantity. Returns {countId, bin, sku}. */
    private Object[] countWithOneLine(int quantity) {
        String bin = ApiClient.uniqueSku();
        String sku = ApiClient.uniqueSku();
        ApiClient.authed(admin).body(json("sku", sku, "name", "Counted", "quantity", quantity, "location", bin))
                .when().post("/api/items").then().statusCode(201);
        int id = ApiClient.authed(admin).body(json("bins", bin))
                .when().post("/api/counts")
                .then().statusCode(201)
                .body("status", equalTo("OPEN"))
                .extract().path("id");
        return new Object[]{id, bin, sku};
    }

    @Test(description = "A count snapshots the expected quantity of every item in the chosen bin")
    public void countSnapshotsExpected() {
        Object[] c = countWithOneLine(10);
        ApiClient.authed(admin)
                .when().get("/api/counts/" + c[0])
                .then().statusCode(200)
                .body("lines.find { it.sku == '" + c[2] + "' }.expected", equalTo(10))
                .body("lines.find { it.sku == '" + c[2] + "' }.counted", equalTo(null));
    }

    @Test(description = "Recording a count shows the variance against the expected quantity")
    public void recordShowsVariance() {
        Object[] c = countWithOneLine(10);
        ApiClient.authed(picker).body(json("sku", c[2], "location", c[1], "counted", 8))
                .when().post("/api/counts/" + c[0] + "/lines")
                .then().statusCode(200)
                .body("lines.find { it.sku == '" + c[2] + "' }.variance", equalTo(-2));
    }

    @Test(description = "A count cannot be submitted while lines are uncounted")
    public void submitWithUncountedIs409() {
        Object[] c = countWithOneLine(4);
        ApiClient.authed(admin)
                .when().post("/api/counts/" + c[0] + "/submit")
                .then().statusCode(409)
                .body("error", containsString("not counted"));
    }

    @Test(description = "A count cannot be approved before it is submitted")
    public void approveBeforeSubmitIs409() {
        Object[] c = countWithOneLine(4);
        ApiClient.authed(admin)
                .when().post("/api/counts/" + c[0] + "/approve")
                .then().statusCode(409);
    }

    @Test(description = "Full cycle: count, submit, admin approves, stock is corrected")
    public void fullCycleCorrectsStock() {
        Object[] c = countWithOneLine(10);
        ApiClient.authed(picker).body(json("sku", c[2], "location", c[1], "counted", 8))
                .when().post("/api/counts/" + c[0] + "/lines").then().statusCode(200);
        ApiClient.authed(picker)
                .when().post("/api/counts/" + c[0] + "/submit")
                .then().statusCode(200)
                .body("status", equalTo("SUBMITTED"));
        ApiClient.authed(admin)
                .when().post("/api/counts/" + c[0] + "/approve")
                .then().statusCode(200)
                .body("status", equalTo("APPROVED"));
        Assert.assertEquals(ApiClient.quantityOf(admin, (String) c[2]), 8);
    }

    @Test(description = "Only admins can approve a count")
    public void pickerCannotApproveIs403() {
        Object[] c = countWithOneLine(4);
        ApiClient.authed(picker).body(json("sku", c[2], "location", c[1], "counted", 4))
                .when().post("/api/counts/" + c[0] + "/lines").then().statusCode(200);
        ApiClient.authed(picker).when().post("/api/counts/" + c[0] + "/submit").then().statusCode(200);
        ApiClient.authed(picker)
                .when().post("/api/counts/" + c[0] + "/approve")
                .then().statusCode(403);
    }

    @Test(description = "Approving twice is rejected and stock is not changed again")
    public void approveTwiceIs409() {
        Object[] c = countWithOneLine(10);
        ApiClient.authed(admin).body(json("sku", c[2], "location", c[1], "counted", 7))
                .when().post("/api/counts/" + c[0] + "/lines").then().statusCode(200);
        ApiClient.authed(admin).when().post("/api/counts/" + c[0] + "/submit").then().statusCode(200);
        ApiClient.authed(admin).when().post("/api/counts/" + c[0] + "/approve").then().statusCode(200);
        ApiClient.authed(admin)
                .when().post("/api/counts/" + c[0] + "/approve")
                .then().statusCode(409);
        Assert.assertEquals(ApiClient.quantityOf(admin, (String) c[2]), 7);
    }

    @Test(description = "A count with no bins is rejected")
    public void countWithoutBinsIs400() {
        ApiClient.authed(admin).body(json("bins", ""))
                .when().post("/api/counts")
                .then().statusCode(400);
    }

    @Test(description = "Recording a line that is not in the count is 404")
    public void unknownLineIs404() {
        Object[] c = countWithOneLine(3);
        ApiClient.authed(admin).body(json("sku", "NOPE-LINE", "location", c[1], "counted", 1))
                .when().post("/api/counts/" + c[0] + "/lines")
                .then().statusCode(404);
    }
}
