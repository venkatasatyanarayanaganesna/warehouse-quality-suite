package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@Epic("API")
@Feature("CSV import")
public class ImportApiTests {

    private static final String HEADER = "sku,name,quantity,location,batch,expiry";

    private String admin;
    private String picker;

    @BeforeClass
    public void tokens() {
        admin = ApiClient.adminToken();
        picker = ApiClient.pickerToken();
    }

    private static String csv(String... lines) {
        return String.join("\n", lines) + "\n";
    }

    @Test(description = "A valid file creates every item, with batches and expiry dates")
    public void validFileImportsAllRows() {
        String a = ApiClient.uniqueSku();
        String b = ApiClient.uniqueSku();
        ApiClient.authed(admin).body(csv(HEADER,
                        a + ",Widget,5,IMP-A,B-1,2099-01-01",
                        b + ",Gadget,0,,,"))
                .when().post("/api/items/import")
                .then().statusCode(201)
                .body("imported", equalTo(2));
        ApiClient.authed(admin)
                .when().get("/api/items/" + a + "/lots")
                .then().statusCode(200)
                .body("find { it.batch == 'B-1' }.expiry", equalTo("2099-01-01"));
    }

    @Test(description = "One bad row rejects the whole file and reports its line number")
    public void badRowRejectsWholeFile() {
        String good = ApiClient.uniqueSku();
        ApiClient.authed(admin).body(csv(HEADER,
                        good + ",Good,1,,,",
                        ApiClient.uniqueSku() + ",Broken,x,,,"))
                .when().post("/api/items/import")
                .then().statusCode(400)
                .body("error", containsString("1 row(s) have errors"))
                .body("errors", hasSize(1))
                .body("errors[0].line", equalTo(3));
        ApiClient.authed(admin)
                .when().get("/api/items/" + good)
                .then().statusCode(404);
    }

    @Test(description = "The same SKU twice in one file is rejected")
    public void duplicateInFileIs400() {
        String sku = ApiClient.uniqueSku();
        ApiClient.authed(admin).body(csv(HEADER, sku + ",A,1,,,", sku + ",B,1,,,"))
                .when().post("/api/items/import")
                .then().statusCode(400)
                .body("errors[0].error", containsString("Duplicate SKU"));
    }

    @Test(description = "A SKU already in the warehouse is a conflict")
    public void existingSkuIs409() {
        ApiClient.authed(admin).body(csv(HEADER, "SKU-1001,Clash,1,,,"))
                .when().post("/api/items/import")
                .then().statusCode(409)
                .body("error", containsString("already exists"));
    }

    @Test(description = "A file with the wrong header is rejected")
    public void wrongHeaderIs400() {
        ApiClient.authed(admin).body(csv("sku,name", "X-1,Y"))
                .when().post("/api/items/import")
                .then().statusCode(400)
                .body("errors[0].error", containsString("Header must be"));
    }

    @Test(description = "A file with no data rows is rejected")
    public void headerOnlyIs400() {
        ApiClient.authed(admin).body(csv(HEADER))
                .when().post("/api/items/import")
                .then().statusCode(400)
                .body("error", containsString("no data rows"));
    }

    @Test(description = "A picker can import items")
    public void pickerCanImport() {
        ApiClient.authed(picker).body(csv(HEADER, ApiClient.uniqueSku() + ",Picker Import,2,,,"))
                .when().post("/api/items/import")
                .then().statusCode(201);
    }
}
