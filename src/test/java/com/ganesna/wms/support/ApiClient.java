package com.ganesna.wms.support;

import com.ganesna.wms.config.ConfigReader;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;

/** Small helper used by API, UI and cross-layer tests to prepare and check data. */
public final class ApiClient {

    private ApiClient() {
    }

    public static RequestSpecification spec() {
        return new RequestSpecBuilder()
                .setBaseUri(ConfigReader.get("base.url"))
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .addFilter(new AllureRestAssured())
                .build();
    }

    public static RequestSpecification authed(String token) {
        return given().spec(spec()).header("Authorization", "Bearer " + token);
    }

    public static String login(String username, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        return given().spec(spec()).body(body)
                .when().post("/api/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    public static String adminToken() {
        return login(ConfigReader.get("admin.user"), ConfigReader.get("admin.pass"));
    }

    public static String pickerToken() {
        return login(ConfigReader.get("picker.user"), ConfigReader.get("picker.pass"));
    }

    /** Unique SKU so tests running in parallel never touch each other's items. */
    public static String uniqueSku() {
        return "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public static Map<String, Object> itemBody(String sku, String name, Object quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("sku", sku);
        body.put("name", name);
        body.put("quantity", quantity);
        body.put("location", "T-LOC");
        return body;
    }

    public static String createItem(String token, String name, int quantity) {
        String sku = uniqueSku();
        authed(token).body(itemBody(sku, name, quantity))
                .when().post("/api/items")
                .then().statusCode(201);
        return sku;
    }

    public static int quantityOf(String token, String sku) {
        return authed(token)
                .when().get("/api/items/" + sku)
                .then().statusCode(200)
                .extract().path("quantity");
    }
}
