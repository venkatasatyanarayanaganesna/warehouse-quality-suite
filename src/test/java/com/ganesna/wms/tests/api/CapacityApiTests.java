package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@Epic("API")
@Feature("Bin capacity")
public class CapacityApiTests {

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

    /** A bin name no other test uses, so parallel runs never share a bin. */
    private static String freshBin() {
        return ApiClient.uniqueSku();
    }

    private static String createAt(String token, String bin, int quantity) {
        String sku = ApiClient.uniqueSku();
        ApiClient.authed(token).body(json("sku", sku, "name", "Bin Item", "quantity", quantity, "location", bin))
                .when().post("/api/items")
                .then().statusCode(201);
        return sku;
    }

    @Test(description = "An admin sets a bin capacity and sees usage")
    public void setCapacityReturnsUsage() {
        String bin = freshBin();
        createAt(admin, bin, 3);
        ApiClient.authed(admin).body(json("capacity", 10))
                .when().put("/api/locations/" + bin + "/capacity")
                .then().statusCode(200)
                .body("capacity", equalTo(10))
                .body("used", equalTo(3));
    }

    @Test(description = "Receiving past a bin's capacity is rejected")
    public void receiveBeyondCapacityIs409() {
        String bin = freshBin();
        String sku = createAt(admin, bin, 6);
        ApiClient.authed(admin).body(json("capacity", 10))
                .when().put("/api/locations/" + bin + "/capacity")
                .then().statusCode(200);
        ApiClient.authed(admin).body(json("quantity", 5, "location", bin))
                .when().post("/api/items/" + sku + "/receive")
                .then().statusCode(409)
                .body("error", containsString("is full"));
    }

    @Test(description = "Receiving up to exactly the capacity is allowed")
    public void receiveUpToCapacityIs200() {
        String bin = freshBin();
        String sku = createAt(admin, bin, 6);
        ApiClient.authed(admin).body(json("capacity", 10))
                .when().put("/api/locations/" + bin + "/capacity")
                .then().statusCode(200);
        ApiClient.authed(admin).body(json("quantity", 4, "location", bin))
                .when().post("/api/items/" + sku + "/receive")
                .then().statusCode(200)
                .body("quantity", equalTo(10));
    }

    @Test(description = "A transfer into a full bin is rejected")
    public void transferIntoFullBinIs409() {
        String full = freshBin();
        createAt(admin, full, 10);
        ApiClient.authed(admin).body(json("capacity", 10))
                .when().put("/api/locations/" + full + "/capacity")
                .then().statusCode(200);
        String source = freshBin();
        String sku = createAt(admin, source, 5);
        ApiClient.authed(admin).body(json("from", source, "to", full, "quantity", 1))
                .when().post("/api/items/" + sku + "/transfer")
                .then().statusCode(409)
                .body("error", containsString("is full"));
    }

    @Test(description = "A capacity cannot be set below what the bin already holds")
    public void capacityBelowUsageIs409() {
        String bin = freshBin();
        createAt(admin, bin, 8);
        ApiClient.authed(admin).body(json("capacity", 5))
                .when().put("/api/locations/" + bin + "/capacity")
                .then().statusCode(409)
                .body("error", containsString("below current usage"));
    }

    @Test(description = "A picker cannot set bin capacity")
    public void pickerCannotSetCapacityIs403() {
        String bin = freshBin();
        ApiClient.authed(picker).body(json("capacity", 10))
                .when().put("/api/locations/" + bin + "/capacity")
                .then().statusCode(403);
    }

    @Test(description = "The locations list shows capacity and usage")
    public void locationsListShowsCapacity() {
        String bin = freshBin();
        createAt(admin, bin, 2);
        ApiClient.authed(admin).body(json("capacity", 9))
                .when().put("/api/locations/" + bin + "/capacity")
                .then().statusCode(200);
        ApiClient.authed(admin)
                .when().get("/api/locations")
                .then().statusCode(200)
                .body("find { it.location == '" + bin + "' }.capacity", equalTo(9))
                .body("find { it.location == '" + bin + "' }.used", equalTo(2));
    }
}
