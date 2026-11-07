package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.module.jsv.JsonSchemaValidator;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/** Checks the shape of every response type against a JSON schema, so breaking changes are caught. */
@Epic("API")
@Feature("Response contracts")
public class ContractApiTests {

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

    private static JsonSchemaValidator schema(String name) {
        return JsonSchemaValidator.matchesJsonSchemaInClasspath("schemas/" + name + ".json");
    }

    @Test(description = "Login response")
    public void loginContract() {
        given().spec(ApiClient.spec()).body(json("username", "admin", "password", "admin123"))
                .when().post("/api/login").then().statusCode(200)
                .body(schema("login"));
    }

    @Test(description = "Item response")
    public void itemContract() {
        ApiClient.authed(admin).when().get("/api/items/SKU-1001").then().statusCode(200)
                .body(schema("item-schema"));
    }

    @Test(description = "Item list response")
    public void itemListContract() {
        ApiClient.authed(admin).when().get("/api/items").then().statusCode(200)
                .body(schema("item-list"));
    }

    @Test(description = "Batch list response")
    public void lotListContract() {
        ApiClient.authed(admin).when().get("/api/items/SKU-1001/lots").then().statusCode(200)
                .body(schema("lot-list"));
    }

    @Test(description = "Expiring list response")
    public void expiringContract() {
        ApiClient.authed(admin).when().get("/api/expiring?days=400").then().statusCode(200)
                .body(schema("lot-list"));
    }

    @Test(description = "Bin list response")
    public void binListContract() {
        ApiClient.authed(admin).when().get("/api/locations").then().statusCode(200)
                .body(schema("bin-list"));
    }

    @Test(description = "Alert list response")
    public void alertListContract() {
        ApiClient.authed(admin).when().get("/api/alerts").then().statusCode(200)
                .body(schema("alert-list"));
    }

    @Test(description = "Audit trail response")
    public void auditContract() {
        ApiClient.authed(admin).when().get("/api/audit?limit=30").then().statusCode(200)
                .body(schema("event-list"));
    }

    @Test(description = "Purchase order and list responses")
    public void purchaseOrderContract() {
        String sku = ApiClient.createItem(admin, "Contract PO", 1);
        ApiClient.authed(admin).body(json("supplier", "Acme", "sku", sku, "quantity", 2))
                .when().post("/api/purchase-orders").then().statusCode(201)
                .body(schema("purchase-order"));
        ApiClient.authed(admin).when().get("/api/purchase-orders").then().statusCode(200)
                .body(schema("purchase-order-list"));
    }

    @Test(description = "Sales order and list responses")
    public void salesOrderContract() {
        String sku = ApiClient.createItem(admin, "Contract SO", 3);
        ApiClient.authed(admin).body(json("customer", "Shop Ltd", "sku", sku, "quantity", 1))
                .when().post("/api/sales-orders").then().statusCode(201)
                .body(schema("sales-order"));
        ApiClient.authed(admin).when().get("/api/sales-orders").then().statusCode(200)
                .body(schema("sales-order-list"));
    }

    @Test(description = "Count session and list responses")
    public void countContract() {
        String bin = ApiClient.uniqueSku();
        ApiClient.authed(admin).body(json("bins", bin))
                .when().post("/api/counts").then().statusCode(201)
                .body(schema("count"));
        ApiClient.authed(admin).when().get("/api/counts").then().statusCode(200)
                .body(schema("count-list"));
    }

    @Test(description = "Supplier and customer responses")
    public void partyContract() {
        ApiClient.authed(admin).when().get("/api/suppliers").then().statusCode(200)
                .body(schema("party-list"));
        ApiClient.authed(admin).when().get("/api/customers").then().statusCode(200)
                .body(schema("party-list"));
        ApiClient.authed(admin).body(json("name", "Contract Party " + System.nanoTime()))
                .when().post("/api/suppliers").then().statusCode(201)
                .body(schema("party"));
    }

    @Test(description = "Import success response")
    public void importContract() {
        String sku = ApiClient.uniqueSku();
        ApiClient.authed(picker).body("sku,name,quantity,location,batch,expiry\n" + sku + ",Contract,1,,,\n")
                .when().post("/api/items/import").then().statusCode(201)
                .body(schema("import"));
    }

    @Test(description = "Import rejection response lists its errors")
    public void importErrorContract() {
        ApiClient.authed(admin).body("sku,name,quantity,location,batch,expiry\nBAD SKU,A,1,,,\n")
                .when().post("/api/items/import").then().statusCode(400)
                .body(schema("error"));
    }

    @Test(description = "Error response for a missing item")
    public void notFoundErrorContract() {
        ApiClient.authed(admin).when().get("/api/items/NO-SUCH-ITEM").then().statusCode(404)
                .body(schema("error"));
    }

    @Test(description = "Error response for a conflict")
    public void conflictErrorContract() {
        ApiClient.authed(admin).body(json("sku", "SKU-1001", "name", "Clash", "quantity", 1))
                .when().post("/api/items").then().statusCode(409)
                .body(schema("error"));
    }

    @Test(description = "Error response for a missing login")
    public void unauthorizedErrorContract() {
        given().spec(ApiClient.spec())
                .when().get("/api/items/SKU-1001").then().statusCode(401)
                .body(schema("error"));
    }
}
