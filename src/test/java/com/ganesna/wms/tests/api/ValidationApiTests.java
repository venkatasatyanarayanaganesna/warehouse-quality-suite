package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.ValidatableResponse;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/** One case per validated field. Each request is refused before anything changes. */
@Epic("API")
@Feature("Input validation")
public class ValidationApiTests {

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

    @DataProvider(name = "invalidRequests")
    public Object[][] invalidRequests() {
        return new Object[][]{
                {"item SKU contains a space", "POST", "/api/items", json("sku", "bad sku", "name", "X", "quantity", 1), 400, "Invalid SKU"},
                {"item SKU longer than 30 characters", "POST", "/api/items", json("sku", "A".repeat(31), "name", "X", "quantity", 1), 400, "Invalid SKU"},
                {"item name is blank", "POST", "/api/items", json("sku", ApiClient.uniqueSku(), "name", "   ", "quantity", 1), 400, "Name is required"},
                {"item quantity is negative", "POST", "/api/items", json("sku", ApiClient.uniqueSku(), "name", "X", "quantity", -1), 400, "cannot be negative"},
                {"item quantity is text", "POST", "/api/items", json("sku", ApiClient.uniqueSku(), "name", "X", "quantity", "abc"), 400, "whole number"},
                {"item bin contains a space", "POST", "/api/items", json("sku", ApiClient.uniqueSku(), "name", "X", "quantity", 1, "location", "A 1"), 400, "Invalid location"},
                {"item expiry is not YYYY-MM-DD", "POST", "/api/items", json("sku", ApiClient.uniqueSku(), "name", "X", "quantity", 1, "expiry", "31/12/2099"), 400, "YYYY-MM-DD"},
                {"receive quantity is zero", "POST", "/api/items/SKU-1001/receive", json("quantity", 0), 400, "positive"},
                {"purchase order supplier is blank", "POST", "/api/purchase-orders", json("supplier", "", "sku", "SKU-1001", "quantity", 1), 400, "Supplier is required"},
                {"purchase order supplier unknown", "POST", "/api/purchase-orders", json("supplier", "Nobody Ltd", "sku", "SKU-1001", "quantity", 1), 400, "Unknown supplier"},
                {"sales order customer unknown", "POST", "/api/sales-orders", json("customer", "Nobody Ltd", "sku", "SKU-1001", "quantity", 1), 400, "Unknown customer"},
                {"sales order quantity is text", "POST", "/api/sales-orders", json("customer", "Shop Ltd", "sku", "SKU-1001", "quantity", "many"), 400, "whole number"},
                {"supplier name is blank", "POST", "/api/suppliers", json("name", ""), 400, "name is required"},
                {"customer name already exists", "POST", "/api/customers", json("name", "Shop Ltd"), 409, "already exists"},
                {"bin capacity is negative", "PUT", "/api/locations/VAL-BIN/capacity", json("capacity", -1), 400, "cannot be negative"},
                {"count has no bins", "POST", "/api/counts", json("bins", ""), 400, "At least one bin"},
                {"transfer has no destination", "POST", "/api/items/SKU-1001/transfer", json("from", "A-01", "quantity", 1), 400, "required"},
                {"adjustment has no reason", "POST", "/api/items/SKU-1001/adjust", json("location", "A-01", "quantity", 5), 400, "reason"}
        };
    }

    @Test(dataProvider = "invalidRequests", description = "Invalid input is refused with a clear message")
    public void rejectsInvalidInput(String label, String method, String path,
                                    Map<String, Object> body, int status, String message) {
        ValidatableResponse response = "PUT".equals(method)
                ? ApiClient.authed(admin).body(body).when().put(path).then()
                : ApiClient.authed(admin).body(body).when().post(path).then();
        response.statusCode(status)
                .body("status", equalTo(status))
                .body("error", containsString(message));
    }
}
