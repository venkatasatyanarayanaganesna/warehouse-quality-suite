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
@Feature("Stock movements")
public class StockMovementApiTests {

    private String admin;
    private String picker;

    @BeforeClass
    public void tokens() {
        admin = ApiClient.adminToken();
        picker = ApiClient.pickerToken();
    }

    static Map<String, Object> json(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test(description = "A transfer moves stock between bins and keeps the total")
    public void transferKeepsTotal() {
        String sku = ApiClient.createItem(admin, "Transfer Test", 20);
        ApiClient.authed(admin).body(json("from", "T-LOC", "to", "T-NEW", "quantity", 8))
                .when().post("/api/items/" + sku + "/transfer")
                .then().statusCode(200)
                .body("quantity", equalTo(20))
                .body("locations.find { it.location == 'T-NEW' }.quantity", equalTo(8))
                .body("locations.find { it.location == 'T-LOC' }.quantity", equalTo(12));
    }

    @Test(description = "Transfer to the same bin is rejected")
    public void transferToSameBinIs400() {
        String sku = ApiClient.createItem(admin, "Same Bin", 5);
        ApiClient.authed(admin).body(json("from", "T-LOC", "to", "T-LOC", "quantity", 1))
                .when().post("/api/items/" + sku + "/transfer")
                .then().statusCode(400)
                .body("error", containsString("different"));
    }

    @Test(description = "Transfer more than the bin holds is rejected")
    public void transferOverStockIs409() {
        String sku = ApiClient.createItem(admin, "Over Transfer", 5);
        ApiClient.authed(admin).body(json("from", "T-LOC", "to", "T-NEW", "quantity", 9))
                .when().post("/api/items/" + sku + "/transfer")
                .then().statusCode(409)
                .body("error", containsString("Insufficient stock at T-LOC"));
    }

    @Test(description = "A transfer without a source bin is rejected")
    public void transferWithoutFromIs400() {
        String sku = ApiClient.createItem(admin, "No From", 5);
        ApiClient.authed(admin).body(json("to", "T-NEW", "quantity", 1))
                .when().post("/api/items/" + sku + "/transfer")
                .then().statusCode(400);
    }

    @Test(description = "The history shows the transfer as the newest entry")
    public void historyShowsTransfer() {
        String sku = ApiClient.createItem(admin, "History Transfer", 10);
        ApiClient.authed(admin).body(json("from", "T-LOC", "to", "T-B", "quantity", 3))
                .when().post("/api/items/" + sku + "/transfer")
                .then().statusCode(200);
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku + "/history")
                .then().statusCode(200)
                .body("[0].type", equalTo("TRANSFER"))
                .body("[0].location", equalTo("T-LOC"))
                .body("[0].quantity", equalTo(3))
                .body("[0].note", equalTo("to T-B"));
    }

    @Test(description = "A pick without a bin drains the first bin, then takes from the next")
    public void pickAcrossBinsInPickOrder() {
        String sku = ApiClient.createItem(admin, "Multi Bin", 10);
        ApiClient.authed(admin).body(json("from", "T-LOC", "to", "T-B", "quantity", 4))
                .when().post("/api/items/" + sku + "/transfer")
                .then().statusCode(200);
        ApiClient.authed(admin).body(json("quantity", 8))
                .when().post("/api/items/" + sku + "/pick")
                .then().statusCode(200)
                .body("quantity", equalTo(2))
                .body("locations.size()", equalTo(1))
                .body("locations[0].location", equalTo("T-B"))
                .body("locations[0].quantity", equalTo(2));
    }

    @Test(description = "Picking from a bin with no stock is rejected")
    public void pickFromEmptyBinIs409() {
        String sku = ApiClient.createItem(admin, "Empty Bin", 5);
        ApiClient.authed(admin).body(json("quantity", 1, "location", "T-EMPTY"))
                .when().post("/api/items/" + sku + "/pick")
                .then().statusCode(409)
                .body("error", equalTo("No stock at location T-EMPTY"));
    }

    @Test(description = "A picker can transfer stock")
    public void pickerCanTransfer() {
        String sku = ApiClient.createItem(picker, "Picker Transfer", 6);
        ApiClient.authed(picker).body(json("from", "T-LOC", "to", "T-NEW", "quantity", 2))
                .when().post("/api/items/" + sku + "/transfer")
                .then().statusCode(200);
    }
}
