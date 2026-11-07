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
@Feature("Expiry and batches")
public class ExpiryApiTests {

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

    private static String freshBin() {
        return ApiClient.uniqueSku();
    }

    private  String emptyItem() {
        String sku = ApiClient.uniqueSku();
        ApiClient.authed(admin).body(json("sku", sku, "name", "Expiry Item", "quantity", 0))
                .when().post("/api/items").then().statusCode(201);
        return sku;
    }

    @Test(description = "A receipt with a batch and expiry is recorded as a lot")
    public void receiveRecordsLot() {
        String sku = emptyItem();
        String bin = freshBin();
        ApiClient.authed(admin).body(json("quantity", 3, "location", bin, "batch", "B-7", "expiry", "2099-03-01"))
                .when().post("/api/items/" + sku + "/receive").then().statusCode(200);
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku + "/lots")
                .then().statusCode(200)
                .body("find { it.batch == 'B-7' }.expiry", equalTo("2099-03-01"))
                .body("find { it.batch == 'B-7' }.status", equalTo("OK"))
                .body("find { it.batch == 'B-7' }.quantity", equalTo(3));
    }

    @Test(description = "Expired stock is counted in the total but cannot be picked")
    public void expiredStockCannotBePicked() {
        String sku = emptyItem();
        String bin = freshBin();
        ApiClient.authed(admin).body(json("quantity", 5, "location", bin, "batch", "OLD", "expiry", "2020-01-01"))
                .when().post("/api/items/" + sku + "/receive").then().statusCode(200);
        ApiClient.authed(admin).body(json("quantity", 5, "location", bin, "batch", "NEW", "expiry", "2099-01-01"))
                .when().post("/api/items/" + sku + "/receive").then().statusCode(200);
        ApiClient.authed(admin).body(json("quantity", 6))
                .when().post("/api/items/" + sku + "/pick")
                .then().statusCode(409)
                .body("error", containsString("Insufficient stock"));
        ApiClient.authed(admin).body(json("quantity", 5))
                .when().post("/api/items/" + sku + "/pick")
                .then().statusCode(200)
                .body("quantity", equalTo(5));
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku + "/lots")
                .then().statusCode(200)
                .body("find { it.batch == 'OLD' }.status", equalTo("EXPIRED"));
    }

    @Test(description = "Picks use the earliest expiry first, even across bins")
    public void pickUsesEarliestExpiryFirst() {
        String sku = emptyItem();
        String bin = freshBin();
        ApiClient.authed(admin).body(json("quantity", 4, "location", bin, "batch", "LATE", "expiry", "2099-06-01"))
                .when().post("/api/items/" + sku + "/receive").then().statusCode(200);
        ApiClient.authed(admin).body(json("quantity", 4, "location", bin, "batch", "EARLY", "expiry", "2099-01-01"))
                .when().post("/api/items/" + sku + "/receive").then().statusCode(200);
        ApiClient.authed(admin).body(json("quantity", 4))
                .when().post("/api/items/" + sku + "/pick").then().statusCode(200);
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku + "/lots")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].batch", equalTo("LATE"))
                .body("[0].quantity", equalTo(4));
    }

    @Test(description = "A transfer keeps the batch and expiry of the stock it moves")
    public void transferKeepsExpiry() {
        String sku = emptyItem();
        String from = freshBin();
        String to = freshBin();
        ApiClient.authed(admin).body(json("quantity", 4, "location", from, "batch", "KEEP", "expiry", "2099-02-02"))
                .when().post("/api/items/" + sku + "/receive").then().statusCode(200);
        ApiClient.authed(admin).body(json("from", from, "to", to, "quantity", 2))
                .when().post("/api/items/" + sku + "/transfer").then().statusCode(200);
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku + "/lots")
                .then().statusCode(200)
                .body("find { it.location == '" + to + "' }.batch", equalTo("KEEP"))
                .body("find { it.location == '" + to + "' }.expiry", equalTo("2099-02-02"))
                .body("find { it.location == '" + to + "' }.quantity", equalTo(2));
    }

    @Test(description = "An expired batch appears in the expiring list")
    public void expiredBatchIsListed() {
        String sku = emptyItem();
        String bin = freshBin();
        ApiClient.authed(admin).body(json("quantity", 2, "location", bin, "batch", "GONE", "expiry", "2019-05-05"))
                .when().post("/api/items/" + sku + "/receive").then().statusCode(200);
        ApiClient.authed(admin)
                .when().get("/api/expiring?days=30")
                .then().statusCode(200)
                .body("find { it.sku == '" + sku + "' && it.batch == 'GONE' }.status", equalTo("EXPIRED"));
    }

    @Test(description = "An expiry that is not a date is rejected")
    public void badExpiryIs400() {
        String sku = emptyItem();
        ApiClient.authed(admin).body(json("quantity", 1, "expiry", "31/12/2099"))
                .when().post("/api/items/" + sku + "/receive")
                .then().statusCode(400)
                .body("error", containsString("YYYY-MM-DD"));
    }
}
