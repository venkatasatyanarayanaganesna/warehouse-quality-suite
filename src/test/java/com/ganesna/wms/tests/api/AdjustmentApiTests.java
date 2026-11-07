package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@Epic("API")
@Feature("Stock count adjustments")
public class AdjustmentApiTests {

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

    @Test(description = "An admin count sets the quantity and records the difference")
    public void adminAdjustSetsQuantityAndLogsDelta() {
        String sku = ApiClient.createItem(admin, "Counted", 10);
        ApiClient.authed(admin).body(json("location", "T-LOC", "quantity", 7, "reason", "Cycle count"))
                .when().post("/api/items/" + sku + "/adjust")
                .then().statusCode(200)
                .body("quantity", equalTo(7));
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku + "/history")
                .then().statusCode(200)
                .body("[0].type", equalTo("ADJUST"))
                .body("[0].quantity", equalTo(-3))
                .body("[0].note", equalTo("Cycle count (was 10, now 7)"));
    }

    @Test(description = "An adjustment without a reason is rejected")
    public void adjustWithoutReasonIs400() {
        String sku = ApiClient.createItem(admin, "No Reason", 10);
        ApiClient.authed(admin).body(json("location", "T-LOC", "quantity", 7))
                .when().post("/api/items/" + sku + "/adjust")
                .then().statusCode(400);
    }

    @Test(description = "A picker cannot adjust stock")
    public void pickerCannotAdjustIs403() {
        String sku = ApiClient.createItem(admin, "Protected Count", 10);
        ApiClient.authed(picker).body(json("location", "T-LOC", "quantity", 1, "reason", "Try"))
                .when().post("/api/items/" + sku + "/adjust")
                .then().statusCode(403);
        org.testng.Assert.assertEquals(ApiClient.quantityOf(admin, sku), 10);
    }

    @Test(description = "Counting a bin down to zero removes that bin")
    public void adjustToZeroRemovesBin() {
        String sku = ApiClient.createItem(admin, "Zeroed", 4);
        ApiClient.authed(admin).body(json("location", "T-LOC", "quantity", 0, "reason", "Written off"))
                .when().post("/api/items/" + sku + "/adjust")
                .then().statusCode(200)
                .body("quantity", equalTo(0))
                .body("locations", hasSize(0));
    }
}
