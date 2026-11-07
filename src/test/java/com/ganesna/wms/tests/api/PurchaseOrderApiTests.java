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
@Feature("Purchase orders")
public class PurchaseOrderApiTests {

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

    private int createOrder(String sku, int quantity) {
        return ApiClient.authed(admin)
                .body(json("supplier", "Acme", "sku", sku, "quantity", quantity, "location", "T-DOCK"))
                .when().post("/api/purchase-orders")
                .then().statusCode(201)
                .body("status", equalTo("OPEN"))
                .extract().path("id");
    }

    @Test(description = "An admin creates an open purchase order")
    public void createOrderIsOpen() {
        String sku = ApiClient.createItem(admin, "Ordered", 2);
        ApiClient.authed(admin)
                .body(json("supplier", "Acme", "sku", sku, "quantity", 10))
                .when().post("/api/purchase-orders")
                .then().statusCode(201)
                .body("status", equalTo("OPEN"))
                .body("outstanding", equalTo(10))
                .body("received", equalTo(0));
    }

    @Test(description = "Partial then final receipt updates status and stock")
    public void partialThenFullReceive() {
        String sku = ApiClient.createItem(admin, "Staged", 2);
        int id = createOrder(sku, 10);
        ApiClient.authed(admin).body(json("quantity", 4))
                .when().post("/api/purchase-orders/" + id + "/receive")
                .then().statusCode(200)
                .body("status", equalTo("PARTIAL"))
                .body("outstanding", equalTo(6));
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), 6);

        ApiClient.authed(admin).body(json("quantity", 6))
                .when().post("/api/purchase-orders/" + id + "/receive")
                .then().statusCode(200)
                .body("status", equalTo("RECEIVED"));
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), 12);
    }

    @Test(description = "Receiving more than is outstanding is rejected")
    public void overReceiveIs409() {
        String sku = ApiClient.createItem(admin, "Over Receive", 0);
        int id = createOrder(sku, 5);
        ApiClient.authed(admin).body(json("quantity", 6))
                .when().post("/api/purchase-orders/" + id + "/receive")
                .then().statusCode(409)
                .body("error", containsString("outstanding"));
    }

    @Test(description = "A completed order cannot be received again")
    public void receiveAfterCompleteIs409() {
        String sku = ApiClient.createItem(admin, "Done", 0);
        int id = createOrder(sku, 3);
        ApiClient.authed(admin).body(json("quantity", 3))
                .when().post("/api/purchase-orders/" + id + "/receive")
                .then().statusCode(200);
        ApiClient.authed(admin).body(json("quantity", 1))
                .when().post("/api/purchase-orders/" + id + "/receive")
                .then().statusCode(409)
                .body("error", equalTo("Order is already fully received"));
    }

    @Test(description = "A picker cannot create purchase orders")
    public void pickerCannotCreateIs403() {
        String sku = ApiClient.createItem(admin, "No PO For Picker", 1);
        ApiClient.authed(picker).body(json("supplier", "Acme", "sku", sku, "quantity", 5))
                .when().post("/api/purchase-orders")
                .then().statusCode(403);
    }

    @Test(description = "A purchase order for an unknown item is 404")
    public void unknownItemIs404() {
        ApiClient.authed(admin).body(json("supplier", "Acme", "sku", "NOPE-404", "quantity", 5))
                .when().post("/api/purchase-orders")
                .then().statusCode(404);
    }

    @Test(description = "An unknown purchase order is 404")
    public void unknownOrderIs404() {
        ApiClient.authed(admin).body(json("quantity", 1))
                .when().post("/api/purchase-orders/999999/receive")
                .then().statusCode(404);
    }
}
