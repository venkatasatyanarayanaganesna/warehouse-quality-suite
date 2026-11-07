package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.module.jsv.JsonSchemaValidator;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;

@Epic("API")
@Feature("Warehouse API")
public class WarehouseApiTests {

    private String admin;
    private String picker;

    @BeforeClass
    public void tokens() {
        admin = ApiClient.adminToken();
        picker = ApiClient.pickerToken();
    }

    // ---- authentication ----

    @Test(description = "Valid login returns a token and role")
    public void loginReturnsToken() {
        Map<String, Object> body = new HashMap<>();
        body.put("username", "picker");
        body.put("password", "picker123");
        given().spec(ApiClient.spec()).body(body)
                .when().post("/api/login")
                .then().statusCode(200)
                .body("token", not(emptyOrNullString()))
                .body("role", equalTo("PICKER"));
    }

    @Test(description = "Wrong password is rejected")
    public void wrongPasswordIs401() {
        Map<String, Object> body = new HashMap<>();
        body.put("username", "admin");
        body.put("password", "nope");
        given().spec(ApiClient.spec()).body(body)
                .when().post("/api/login")
                .then().statusCode(401)
                .body("error", equalTo("Invalid username or password"));
    }

    @Test(description = "Items cannot be listed without a token")
    public void listWithoutTokenIs401() {
        given().spec(ApiClient.spec())
                .when().get("/api/items")
                .then().statusCode(401);
    }

    @Test(description = "A made-up token is rejected")
    public void fakeTokenIs401() {
        ApiClient.authed("not-a-real-token")
                .when().get("/api/items")
                .then().statusCode(401);
    }

    // ---- reading ----

    @Test(description = "The list contains the seed items")
    public void listContainsSeedItems() {
        ApiClient.authed(admin)
                .when().get("/api/items")
                .then().statusCode(200)
                .body("size()", greaterThanOrEqualTo(4))
                .body("sku", hasItem("SKU-1001"));
    }

    @Test(description = "Search by name narrows the list")
    public void searchByName() {
        ApiClient.authed(admin)
                .when().get("/api/items?q=scanner")
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].sku", equalTo("SKU-1002"));
    }

    @Test(description = "An item matches the JSON schema")
    public void itemMatchesSchema() {
        ApiClient.authed(admin)
                .when().get("/api/items/SKU-1001")
                .then().statusCode(200)
                .body(JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/item-schema.json"));
    }

    @Test(description = "An unknown SKU returns 404")
    public void unknownSkuIs404() {
        ApiClient.authed(admin)
                .when().get("/api/items/DOES-NOT-EXIST")
                .then().statusCode(404);
    }

    // ---- creating ----

    @Test(description = "Create an item, then read it back")
    public void createItem() {
        String sku = ApiClient.uniqueSku();
        ApiClient.authed(admin).body(ApiClient.itemBody(sku, "Created Item", 30))
                .when().post("/api/items")
                .then().statusCode(201)
                .body("sku", equalTo(sku))
                .body("quantity", equalTo(30))
                .body("lowStock", equalTo(false))
                .body(JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/item-schema.json"));
    }

    @Test(description = "A duplicate SKU returns 409")
    public void duplicateSkuIs409() {
        ApiClient.authed(admin).body(ApiClient.itemBody("SKU-1001", "Duplicate", 1))
                .when().post("/api/items")
                .then().statusCode(409)
                .body("error", equalTo("SKU already exists"));
    }

    @DataProvider(name = "invalidItems")
    public Object[][] invalidItems() {
        return new Object[][]{
                {"", "Widget", 5},
                {"bad sku!", "Widget", 5},
                {"T-NONAME", "", 5},
                {"T-NEGATIVE", "Widget", -1}
        };
    }

    @Test(dataProvider = "invalidItems", description = "Invalid items are rejected with 400")
    public void invalidItemIs400(String sku, String name, int quantity) {
        ApiClient.authed(admin).body(ApiClient.itemBody(sku, name, quantity))
                .when().post("/api/items")
                .then().statusCode(400)
                .body("error", not(emptyOrNullString()));
    }

    // ---- stock movements ----

    @Test(description = "Receiving stock adds to the quantity")
    public void receiveAddsStock() {
        String sku = ApiClient.createItem(admin, "Receive", 10);
        Map<String, Object> body = new HashMap<>();
        body.put("quantity", 15);
        ApiClient.authed(admin).body(body)
                .when().post("/api/items/" + sku + "/receive")
                .then().statusCode(200)
                .body("quantity", equalTo(25));
    }

    @Test(description = "Picking stock subtracts from the quantity")
    public void pickRemovesStock() {
        String sku = ApiClient.createItem(picker, "Pick", 10);
        Map<String, Object> body = new HashMap<>();
        body.put("quantity", 4);
        ApiClient.authed(picker).body(body)
                .when().post("/api/items/" + sku + "/pick")
                .then().statusCode(200)
                .body("quantity", equalTo(6))
                .body("lowStock", equalTo(true));
    }

    @Test(description = "Picking more than available returns 409 and leaves stock unchanged")
    public void overpickIs409AndStockUnchanged() {
        String sku = ApiClient.createItem(admin, "Overpick", 3);
        Map<String, Object> body = new HashMap<>();
        body.put("quantity", 10);
        ApiClient.authed(admin).body(body)
                .when().post("/api/items/" + sku + "/pick")
                .then().statusCode(409)
                .body("error", containsString("Insufficient"));
        org.testng.Assert.assertEquals(ApiClient.quantityOf(admin, sku), 3);
    }

    @DataProvider(name = "badQuantities")
    public Object[][] badQuantities() {
        return new Object[][]{{0}, {-5}, {"abc"}};
    }

    @Test(dataProvider = "badQuantities", description = "Zero, negative and non-numeric quantities are rejected")
    public void badQuantityIs400(Object quantity) {
        String sku = ApiClient.createItem(admin, "Bad Qty", 10);
        Map<String, Object> body = new HashMap<>();
        body.put("quantity", quantity);
        ApiClient.authed(admin).body(body)
                .when().post("/api/items/" + sku + "/receive")
                .then().statusCode(400);
    }

    // ---- permissions and delete ----

    @Test(description = "A picker cannot delete items")
    public void pickerCannotDelete() {
        String sku = ApiClient.createItem(admin, "Protected", 5);
        ApiClient.authed(picker)
                .when().delete("/api/items/" + sku)
                .then().statusCode(403);
        org.testng.Assert.assertEquals(ApiClient.quantityOf(admin, sku), 5);
    }

    @Test(description = "An admin deletes an item and it is gone")
    public void adminDeletesItem() {
        String sku = ApiClient.createItem(admin, "Disposable", 5);
        ApiClient.authed(admin)
                .when().delete("/api/items/" + sku)
                .then().statusCode(204);
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku)
                .then().statusCode(404);
    }
}
