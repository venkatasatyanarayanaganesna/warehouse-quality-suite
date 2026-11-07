package com.ganesna.wms.tests.ui;

import com.ganesna.wms.base.BaseUiTest;
import com.ganesna.wms.base.DriverFactory;
import com.ganesna.wms.pages.InventoryPage;
import com.ganesna.wms.pages.LoginPage;
import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.Assert;
import org.testng.annotations.Test;

@Epic("UI")
@Feature("Inventory")
public class InventoryUiTests extends BaseUiTest {

    private InventoryPage admin() {
        return new LoginPage(DriverFactory.get()).loginAsAdmin();
    }

    @Test(description = "Seed items show the right quantity")
    public void seedItemShowsQuantity() {
        Assert.assertEquals(admin().quantityOf("SKU-1001"), 25);
    }

    @Test(description = "Search narrows the table")
    public void searchFiltersRows() {
        InventoryPage page = admin();
        page.search("scanner");
        page.waitForRow("SKU-1002");
        page.waitForRowGone("SKU-1001");
    }

    @Test(description = "A new item added in the UI appears in the table")
    public void addItemAppearsInTable() {
        String sku = ApiClient.uniqueSku();
        InventoryPage page = admin();
        page.addItem(sku, "Dock Plate", 12, "E-05");
        page.waitForMessageContaining("Added");
        page.waitForRow(sku);
        Assert.assertEquals(page.quantityOf(sku), 12);
    }

    @Test(description = "Adding an existing SKU shows an error")
    public void duplicateSkuShowsError() {
        InventoryPage page = admin();
        page.addItem("SKU-1001", "Duplicate", 1, "X-01");
        page.waitForMessageContaining("SKU already exists");
    }

    @Test(description = "Receiving stock raises the quantity")
    public void receiveIncreasesQuantity() {
        String sku = ApiClient.createItem(ApiClient.adminToken(), "Receive Test", 20);
        InventoryPage page = admin();
        page.waitForRow(sku);
        page.receive(sku, 5);
        page.waitForQuantity(sku, 25);
    }

    @Test(description = "Picking stock lowers the quantity")
    public void pickDecreasesQuantity() {
        String sku = ApiClient.createItem(ApiClient.adminToken(), "Pick Test", 20);
        InventoryPage page = admin();
        page.waitForRow(sku);
        page.pick(sku, 8);
        page.waitForQuantity(sku, 12);
    }

    @Test(description = "Picking more than the stock is refused and the quantity is unchanged")
    public void pickMoreThanStockShowsError() {
        String sku = ApiClient.createItem(ApiClient.adminToken(), "Overpick Test", 3);
        InventoryPage page = admin();
        page.waitForRow(sku);
        page.pick(sku, 10);
        page.waitForMessageContaining("Insufficient stock");
        Assert.assertEquals(page.quantityOf(sku), 3);
    }

    @Test(description = "Low stock items carry a badge")
    public void lowStockBadgeShowsForLowItems() {
        InventoryPage page = admin();
        Assert.assertTrue(page.isLowStock("SKU-1004"), "SKU-1004 has 6 units and should be low");
        Assert.assertFalse(page.isLowStock("SKU-1003"), "SKU-1003 has 120 units and should not be low");
    }

    @Test(description = "A picker does not get delete buttons")
    public void pickerHasNoDeleteButton() {
        InventoryPage page = new LoginPage(DriverFactory.get()).loginAsPicker();
        Assert.assertFalse(page.hasDeleteButton("SKU-1001"));
    }

    @Test(description = "An admin can delete an item")
    public void adminCanDeleteItem() {
        String sku = ApiClient.createItem(ApiClient.adminToken(), "Delete Test", 4);
        InventoryPage page = admin();
        page.waitForRow(sku);
        page.delete(sku);
        page.waitForRowGone(sku);
    }
}
