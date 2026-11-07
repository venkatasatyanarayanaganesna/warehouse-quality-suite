package com.ganesna.wms.tests.e2e;

import com.ganesna.wms.base.BaseUiTest;
import com.ganesna.wms.base.DriverFactory;
import com.ganesna.wms.pages.InventoryPage;
import com.ganesna.wms.pages.LoginPage;
import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.Assert;
import org.testng.annotations.Test;

/** The same stock must read the same in the UI and in the API. */
@Epic("End-to-end")
@Feature("Stock consistency")
public class StockConsistencyTests extends BaseUiTest {

    @Test(description = "An item created through the API shows the same quantity in the UI")
    public void apiCreatedItemIsVisibleInUi() {
        String token = ApiClient.adminToken();
        String sku = ApiClient.createItem(token, "API Created", 15);

        InventoryPage page = new LoginPage(DriverFactory.get()).loginAsAdmin();
        page.waitForRow(sku);
        Assert.assertEquals(page.quantityOf(sku), 15);
    }

    @Test(description = "Picking stock in the UI is reflected by the API")
    public void uiPickIsReflectedInApi() {
        String token = ApiClient.adminToken();
        String sku = ApiClient.createItem(token, "UI Pick", 20);

        InventoryPage page = new LoginPage(DriverFactory.get()).loginAsAdmin();
        page.waitForRow(sku);
        page.pick(sku, 5);
        page.waitForQuantity(sku, 15);

        Assert.assertEquals(ApiClient.quantityOf(token, sku), 15);
    }

    @Test(description = "Receiving stock in the UI clears the low stock flag in the API and UI")
    public void receiveClearsLowStockEverywhere() {
        String token = ApiClient.adminToken();
        String sku = ApiClient.createItem(token, "Low Stock", 5);

        InventoryPage page = new LoginPage(DriverFactory.get()).loginAsAdmin();
        page.waitForRow(sku);
        Assert.assertTrue(page.isLowStock(sku));

        page.receive(sku, 10);
        page.waitForQuantity(sku, 15);
        page.waitForLowStock(sku, false);

        Boolean lowInApi = ApiClient.authed(token).when().get("/api/items/" + sku)
                .then().statusCode(200).extract().path("lowStock");
        Assert.assertFalse(lowInApi);
    }
}
