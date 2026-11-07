package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;

@Epic("API")
@Feature("Idempotent requests")
public class IdempotencyApiTests {

    private String admin;

    @BeforeClass
    public void tokens() {
        admin = ApiClient.adminToken();
    }

    private static Map<String, Object> json(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test(description = "A retried receive with the same key is applied only once")
    public void sameKeyAppliesOnce() {
        String sku = ApiClient.createItem(admin, "Retry Receive", 10);
        String key = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            ApiClient.authed(admin).header("Idempotency-Key", key).body(json("quantity", 5))
                    .when().post("/api/items/" + sku + "/receive")
                    .then().statusCode(200)
                    .body("quantity", equalTo(15));
        }
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku)
                .then().body("quantity", equalTo(15));
    }

    @Test(description = "Different keys are separate requests and each is applied")
    public void differentKeysAreApplied() {
        String sku = ApiClient.createItem(admin, "Two Receives", 10);
        ApiClient.authed(admin).header("Idempotency-Key", UUID.randomUUID().toString()).body(json("quantity", 2))
                .when().post("/api/items/" + sku + "/receive")
                .then().statusCode(200);
        ApiClient.authed(admin).header("Idempotency-Key", UUID.randomUUID().toString()).body(json("quantity", 2))
                .when().post("/api/items/" + sku + "/receive")
                .then().statusCode(200);
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku)
                .then().body("quantity", equalTo(14));
    }

    @Test(description = "A retried shipment with the same key is applied only once")
    public void sameKeyShipsOnce() {
        String sku = ApiClient.createItem(admin, "Retry Ship", 10);
        String orderId = ApiClient.authed(admin).body(json("customer", "Shop", "sku", sku, "quantity", 4))
                .when().post("/api/sales-orders")
                .then().statusCode(201)
                .extract().path("id").toString();
        String key = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            ApiClient.authed(admin).header("Idempotency-Key", key).body(json("quantity", 4))
                    .when().post("/api/sales-orders/" + orderId + "/ship")
                    .then().statusCode(200)
                    .body("status", equalTo("SHIPPED"));
        }
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku)
                .then().body("quantity", equalTo(6));
    }
}
