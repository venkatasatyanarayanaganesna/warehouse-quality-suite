package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import com.ganesna.wms.support.CsvData;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;

@Epic("API")
@Feature("Data-driven stock rules")
public class DataDrivenStockApiTests {

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

    private static void assertClientError(Response response, String name) {
        int status = response.statusCode();
        Assert.assertTrue(status >= 400 && status < 500,
                name + ": expected a 4xx rejection but got " + status);
    }

    @DataProvider(name = "stockMovements")
    public Object[][] stockMovements() {
        return CsvData.rows("testdata/stock-movements.csv");
    }

    @DataProvider(name = "adjustments")
    public Object[][] adjustments() {
        return CsvData.rows("testdata/adjust-counts.csv");
    }

    @Test(dataProvider = "stockMovements", description = "Receive and pick rules from stock-movements.csv")
    public void receiveAndPickFollowTheRules(String name, String start, String action,
                                             String amount, String result, String expected) {
        String sku = ApiClient.createItem(admin, "DD " + name, Integer.parseInt(start));
        Response response = ApiClient.authed(admin)
                .body(json("quantity", Integer.parseInt(amount)))
                .when().post("/api/items/" + sku + "/" + action);
        if (result.equals("OK")) {
            Assert.assertEquals(response.statusCode(), 200, name);
        } else {
            assertClientError(response, name);
        }
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), Integer.parseInt(expected), name);
    }

    @Test(dataProvider = "adjustments", description = "Stock counts from adjust-counts.csv")
    public void stockCountsFollowTheRules(String name, String start, String counted, String reason,
                                          String result, String expected, String delta) {
        String sku = ApiClient.createItem(admin, "DD " + name, Integer.parseInt(start));
        Response response = ApiClient.authed(admin)
                .body(json("location", "T-LOC", "quantity", Integer.parseInt(counted), "reason", reason))
                .when().post("/api/items/" + sku + "/adjust");
        if (result.equals("OK")) {
            Assert.assertEquals(response.statusCode(), 200, name);
            ApiClient.authed(admin)
                    .when().get("/api/items/" + sku + "/history")
                    .then().statusCode(200)
                    .body("[0].type", equalTo("ADJUST"))
                    .body("[0].quantity", equalTo(Integer.parseInt(delta)))
                    .body("[0].note", equalTo(reason + " (was " + start + ", now " + counted + ")"));
        } else {
            assertClientError(response, name);
        }
        Assert.assertEquals(ApiClient.quantityOf(admin, sku), Integer.parseInt(expected), name);
    }
}
