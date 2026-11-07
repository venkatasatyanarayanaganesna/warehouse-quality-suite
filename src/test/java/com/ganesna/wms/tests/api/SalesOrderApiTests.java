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
@Feature("Sales orders and shipping")
public class SalesOrderApiTests {

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

    private int createOrder(String sku, int quantity) {
        return ApiClient.authed(admin).body(json("customer", "Shop Ltd", "sku", sku, "quantity", quantity))
                .when().post("/api/sales-orders")
                .then().statusCode(201)
                .body("status", equalTo("OPEN"))
                .extract().path("id");
    }

    @Test(description = "A sales order is created OPEN")
    public void createSalesOrderIsOpen() {
        String sku = ApiClient.createItem(admin, "Sellable", 10);
        ApiClient.authed(admin).body(json("customer", "Shop Ltd", "sku", sku, "quantity", 4))
                .when().post("/api/sales-orders")
                .then().statusCode(201)
                .body("status", equalTo("OPEN"))
                .body("outstanding", equalTo(4))
                .body("shipped", equalTo(0));
    }

    @Test(description = "Open orders reserve stock, so a second order cannot promise the same units")
    public void reservationBlocksOverCommitting() {
        String sku = ApiClient.createItem(admin, "Reserved", 10);
        createOrder(sku, 7);
        ApiClient.authed(admin).body(json("customer", "Shop Ltd", "sku", sku, "quantity", 4))
                .when().post("/api/sales-orders")
                .then().statusCode(409)
                .body("error", containsString("3 available after reservations"));
    }

    @Test(description = "Reserved stock cannot be picked by someone else")
    public void reservedStockCannotBePicked() {
        String sku = ApiClient.createItem(admin, "Held", 10);
        createOrder(sku, 8);
        ApiClient.authed(admin).body(json("quantity", 5))
                .when().post("/api/items/" + sku + "/pick")
                .then().statusCode(409)
                .body("error", containsString("Insufficient stock"));
        ApiClient.authed(admin).body(json("quantity", 2))
                .when().post("/api/items/" + sku + "/pick")
                .then().statusCode(200)
                .body("quantity", equalTo(8));
    }

    @Test(description = "Partial shipment, then the rest, updates status and stock")
    public void partialThenFullShipment() {
        String sku = ApiClient.createItem(admin, "Shipped", 10);
        int id = createOrder(sku, 7);
        ApiClient.authed(admin).body(json("quantity", 3))
                .when().post("/api/sales-orders/" + id + "/ship")
                .then().statusCode(200)
                .body("status", equalTo("PARTIAL"))
                .body("outstanding", equalTo(4));
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), 7);

        ApiClient.authed(admin).body(json("quantity", 4))
                .when().post("/api/sales-orders/" + id + "/ship")
                .then().statusCode(200)
                .body("status", equalTo("SHIPPED"));
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), 3);
    }

    @Test(description = "Shipping more than is outstanding is rejected")
    public void shipOverOutstandingIs409() {
        String sku = ApiClient.createItem(admin, "Over Ship", 10);
        int id = createOrder(sku, 2);
        ApiClient.authed(admin).body(json("quantity", 3))
                .when().post("/api/sales-orders/" + id + "/ship")
                .then().statusCode(409)
                .body("error", containsString("outstanding"));
    }

    @Test(description = "Shipping a completed order is rejected")
    public void shipAfterCompleteIs409() {
        String sku = ApiClient.createItem(admin, "Complete", 10);
        int id = createOrder(sku, 2);
        ApiClient.authed(admin).body(json("quantity", 2))
                .when().post("/api/sales-orders/" + id + "/ship")
                .then().statusCode(200);
        ApiClient.authed(admin).body(json("quantity", 1))
                .when().post("/api/sales-orders/" + id + "/ship")
                .then().statusCode(409)
                .body("error", equalTo("Order is already fully shipped"));
    }

    @Test(description = "A sales order for an unknown item is 404")
    public void unknownItemIs404() {
        ApiClient.authed(admin).body(json("customer", "Shop Ltd", "sku", "NOPE-SO", "quantity", 1))
                .when().post("/api/sales-orders")
                .then().statusCode(404);
    }

    @Test(description = "An unknown sales order is 404")
    public void unknownSalesOrderIs404() {
        ApiClient.authed(admin).body(json("quantity", 1))
                .when().post("/api/sales-orders/999999/ship")
                .then().statusCode(404);
    }

    @Test(description = "Shipping leaves a trail in the audit log")
    public void shipmentIsAudited() {
        String sku = ApiClient.createItem(admin, "Audit Ship", 5);
        int id = createOrder(sku, 2);
        ApiClient.authed(admin).body(json("quantity", 2))
                .when().post("/api/sales-orders/" + id + "/ship")
                .then().statusCode(200);
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku + "/history")
                .then().statusCode(200)
                .body("[0].type", equalTo("SHIP"))
                .body("[0].quantity", equalTo(2));
    }
}
