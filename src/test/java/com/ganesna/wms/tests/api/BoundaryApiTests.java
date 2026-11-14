package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/**
 * Edge cases around the validation rules: limits exactly on the boundary, requests that
 * must be refused without changing stock, and hostile input that must never crash the server.
 */
@Epic("API")
@Feature("Boundaries and robustness")
public class BoundaryApiTests {

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

    private static void assertNoServerError(Response response, String what) {
        int status = response.statusCode();
        Assert.assertTrue(status < 500,
                what + ": the server answered " + status + " (5xx means an unhandled error): " + response.asString());
    }

    private static void assertClientError(Response response, String what) {
        int status = response.statusCode();
        Assert.assertTrue(status >= 400 && status < 500,
                what + ": expected a 4xx refusal but got " + status + ": " + response.asString());
    }

    private Response pick(String sku, int quantity) {
        return ApiClient.authed(admin).body(json("quantity", quantity))
                .when().post("/api/items/" + sku + "/pick");
    }

    @Test(description = "A SKU of exactly 30 characters (the limit) is accepted")
    public void skuAtTheLimitIsAccepted() {
        String sku = ("BND" + UUID.randomUUID().toString().replace("-", "").toUpperCase()).substring(0, 30);
        Assert.assertEquals(sku.length(), 30);
        ApiClient.authed(admin).body(ApiClient.itemBody(sku, "Boundary SKU 30", 1))
                .when().post("/api/items")
                .then().statusCode(201);
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), 1);
    }

    @Test(description = "An item can be created with quantity 0 (only negative numbers are refused)")
    public void zeroQuantityItemIsAccepted() {
        String sku = ApiClient.uniqueSku();
        ApiClient.authed(admin).body(ApiClient.itemBody(sku, "Boundary zero", 0))
                .when().post("/api/items")
                .then().statusCode(201);
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), 0);
    }

    @Test(description = "Picking exactly the stock leaves 0, and one more pick is then refused")
    public void pickingExactlyTheStockLeavesZero() {
        String sku = ApiClient.createItem(admin, "Boundary exact pick", 5);
        Assert.assertEquals(pick(sku, 5).statusCode(), 200);
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), 0);
        Assert.assertEquals(pick(sku, 1).statusCode(), 409, "stock is empty, so the pick must be refused");
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), 0);
    }

    @Test(description = "Picking more than the stock is refused and changes nothing")
    public void pickingMoreThanStockChangesNothing() {
        String sku = ApiClient.createItem(admin, "Boundary over pick", 3);
        Assert.assertEquals(pick(sku, 4).statusCode(), 409);
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), 3);
    }

    @Test(description = "A negative pick quantity is refused and changes nothing")
    public void negativePickIsRefused() {
        String sku = ApiClient.createItem(admin, "Boundary negative pick", 3);
        assertClientError(pick(sku, -1), "pick of -1");
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), 3);
    }

    @Test(description = "A quantity beyond the integer range never causes a server error")
    public void quantityBeyondIntegerRangeIsHandled() {
        Response response = ApiClient.authed(admin)
                .body(ApiClient.itemBody(ApiClient.uniqueSku(), "Boundary overflow", 2147483648L))
                .when().post("/api/items");
        assertNoServerError(response, "quantity 2147483648");
    }

    @DataProvider(name = "hostileNames")
    public Object[][] hostileNames() {
        return new Object[][]{
                {"SQL-looking text", "'; DROP TABLE items;--"},
                {"HTML and script tags", "<script>alert(1)</script>"},
                {"Unicode and emoji", "Caf\u00e9 \u00dc\u00f1\u00ef\u00e7\u00f8d\u00e9 \uD83D\uDE00"},
                {"5000 characters", "A".repeat(5000)}
        };
    }

    @Test(dataProvider = "hostileNames", description = "Unusual item names never crash the server")
    public void hostileNamesNeverCauseServerError(String label, String name) {
        Response response = ApiClient.authed(admin)
                .body(ApiClient.itemBody(ApiClient.uniqueSku(), name, 1))
                .when().post("/api/items");
        assertNoServerError(response, label);
        given().spec(ApiClient.spec()).when().get("/api/health").then().statusCode(200);
    }

    @Test(description = "A body that is not valid JSON is refused without crashing the server")
    public void malformedJsonIsRefusedCleanly() {
        Response response = ApiClient.authed(admin).body("{not valid json")
                .when().post("/api/items");
        assertNoServerError(response, "malformed JSON");
        given().spec(ApiClient.spec()).when().get("/api/health").then().statusCode(200);
    }

    @Test(description = "An unknown SKU gives a client error, not a server error")
    public void unknownSkuIsAClientError() {
        Response response = ApiClient.authed(admin).when().get("/api/items/NO-SUCH-SKU-" + UUID.randomUUID());
        assertClientError(response, "GET unknown SKU");
    }

    @Test(description = "Requests without a token are rejected with 401")
    public void missingTokenIsRejected() {
        given().spec(ApiClient.spec()).when().get("/api/items").then().statusCode(401);
    }


    @Test(description = "A request body above the size cap is refused with 413")
    public void oversizedBodyIsRefusedWith413() {   
        Response response = ApiClient.authed(admin)
                .body(ApiClient.itemBody(ApiClient.uniqueSku(), "A".repeat(300_000), 1))
                .when().post("/api/items");
        Assert.assertEquals(response.statusCode(), 413, response.asString());
        given().spec(ApiClient.spec()).when().get("/api/health").then().statusCode(200);
    }
}

