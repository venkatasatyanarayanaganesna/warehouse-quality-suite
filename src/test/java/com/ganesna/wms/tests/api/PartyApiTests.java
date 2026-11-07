package com.ganesna.wms.tests.api;

import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Epic("API")
@Feature("Suppliers and customers")
public class PartyApiTests {

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

    private static String freshName(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test(description = "Seeded suppliers are listed")
    public void seededSuppliersListed() {
        ApiClient.authed(admin)
                .when().get("/api/suppliers")
                .then().statusCode(200)
                .body("find { it.name == 'Acme' }.id", notNullValue());
    }

    @Test(description = "An admin adds a supplier")
    public void adminAddsSupplier() {
        String name = freshName("Supplier");
        ApiClient.authed(admin).body(json("name", name))
                .when().post("/api/suppliers")
                .then().statusCode(201)
                .body("name", equalTo(name));
    }

    @Test(description = "A supplier name already in use is rejected, ignoring case")
    public void duplicateSupplierIs409() {
        String name = freshName("Dupe");
        ApiClient.authed(admin).body(json("name", name))
                .when().post("/api/suppliers").then().statusCode(201);
        ApiClient.authed(admin).body(json("name", name.toLowerCase()))
                .when().post("/api/suppliers")
                .then().statusCode(409)
                .body("error", containsString("already exists"));
    }

    @Test(description = "A picker cannot add a customer")
    public void pickerCannotAddCustomerIs403() {
        ApiClient.authed(picker).body(json("name", freshName("Blocked")))
                .when().post("/api/customers")
                .then().statusCode(403);
    }

    @Test(description = "A purchase order names a managed supplier, matched without regard to case")
    public void orderUsesManagedSupplierName() {
        String name = freshName("Managed");
        ApiClient.authed(admin).body(json("name", name)).when().post("/api/suppliers").then().statusCode(201);
        String sku = ApiClient.createItem(admin, "Ordered", 1);
        ApiClient.authed(admin).body(json("supplier", name.toUpperCase(), "sku", sku, "quantity", 3))
                .when().post("/api/purchase-orders")
                .then().statusCode(201)
                .body("supplier", equalTo(name));
    }

    @Test(description = "A purchase order for an unknown supplier is rejected")
    public void unknownSupplierIs400() {
        String sku = ApiClient.createItem(admin, "No Supplier", 1);
        ApiClient.authed(admin).body(json("supplier", "Nobody Ltd", "sku", sku, "quantity", 3))
                .when().post("/api/purchase-orders")
                .then().statusCode(400)
                .body("error", containsString("Unknown supplier"));
    }

    @Test(description = "A customer added by an admin can be used on a sales order")
    public void newCustomerUsableOnSalesOrder() {
        String name = freshName("Customer");
        ApiClient.authed(admin).body(json("name", name)).when().post("/api/customers").then().statusCode(201);
        String sku = ApiClient.createItem(admin, "Sold", 2);
        ApiClient.authed(admin).body(json("customer", name, "sku", sku, "quantity", 1))
                .when().post("/api/sales-orders")
                .then().statusCode(201)
                .body("customer", equalTo(name));
    }
}
