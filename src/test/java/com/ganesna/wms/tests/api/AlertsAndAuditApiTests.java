package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

@Epic("API")
@Feature("Reorder alerts and audit trail")
public class AlertsAndAuditApiTests {

    private String admin;
    private String picker;

    @BeforeClass
    public void tokens() {
        admin = ApiClient.adminToken();
        picker = ApiClient.pickerToken();
    }

    @Test(description = "An item below its reorder level appears in alerts with a suggested order")
    public void lowStockItemAppearsInAlerts() {
        String sku = ApiClient.createItem(admin, "Alert Me", 3);
        ApiClient.authed(admin)
                .when().get("/api/alerts")
                .then().statusCode(200)
                .body("find { it.sku == '" + sku + "' }.suggestedOrder", equalTo(17));
    }

    @Test(description = "An item above its reorder level is not in alerts")
    public void healthyItemNotInAlerts() {
        String sku = ApiClient.createItem(admin, "Healthy", 50);
        ApiClient.authed(admin)
                .when().get("/api/alerts")
                .then().statusCode(200)
                .body("find { it.sku == '" + sku + "' }", nullValue());
    }

    @Test(description = "The audit trail records who created an item")
    public void auditRecordsCreateWithUser() {
        String sku = ApiClient.createItem(admin, "Audited", 5);
        ApiClient.authed(admin)
                .when().get("/api/audit?limit=500")
                .then().statusCode(200)
                .body("find { it.sku == '" + sku + "' && it.type == 'CREATE' }.user", equalTo("admin"));
    }

    @Test(description = "Actions are attributed to the user who made them")
    public void pickerActionsAreAttributed() {
        String sku = ApiClient.createItem(admin, "Attributed", 5);
        Map<String, Object> body = new HashMap<>();
        body.put("quantity", 1);
        ApiClient.authed(picker).body(body)
                .when().post("/api/items/" + sku + "/pick")
                .then().statusCode(200);
        ApiClient.authed(admin)
                .when().get("/api/items/" + sku + "/history")
                .then().statusCode(200)
                .body("[0].type", equalTo("PICK"))
                .body("[0].user", equalTo("picker"));
    }

    @Test(description = "The audit endpoint respects the limit parameter")
    public void auditLimitIsRespected() {
        ApiClient.authed(admin)
                .when().get("/api/audit?limit=3")
                .then().statusCode(200)
                .body("size()", lessThanOrEqualTo(3));
    }
}
