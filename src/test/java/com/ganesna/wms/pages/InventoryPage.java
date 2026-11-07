package com.ganesna.wms.pages;

import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class InventoryPage extends BasePage {

    private final By currentUser = By.id("current-user");
    private final By rows = By.cssSelector("#items-body tr");
    private final By searchInput = By.id("search-input");
    private final By message = By.id("message");
    private final By logoutButton = By.id("logout-btn");

    public InventoryPage(WebDriver driver) {
        super(driver);
    }

    private By row(String sku) {
        return By.cssSelector("tr[data-sku='" + sku + "']");
    }

    private By qty(String sku) {
        return By.cssSelector("tr[data-sku='" + sku + "'] .qty");
    }

    public void waitForLoaded() {
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(rows, 0));
    }

    public String loggedInAs() {
        return text(currentUser);
    }

    public boolean hasRow(String sku) {
        return !driver.findElements(row(sku)).isEmpty();
    }

    public void waitForRow(String sku) {
        visible(row(sku));
    }

    public void waitForRowGone(String sku) {
        wait.until(ExpectedConditions.numberOfElementsToBe(row(sku), 0));
    }

    public int quantityOf(String sku) {
        return Integer.parseInt(text(qty(sku)));
    }

    public void waitForQuantity(String sku, int expected) {
        wait.until(ExpectedConditions.textToBe(qty(sku), String.valueOf(expected)));
    }

    public boolean isLowStock(String sku) {
        visible(row(sku));
        return !driver.findElements(By.cssSelector("tr[data-sku='" + sku + "'] .badge-low")).isEmpty();
    }

    public void waitForLowStock(String sku, boolean expected) {
        By low = By.cssSelector("tr[data-sku='" + sku + "'] .badge-low");
        wait.until(ExpectedConditions.numberOfElementsToBe(low, expected ? 1 : 0));
    }

    public boolean hasDeleteButton(String sku) {
        visible(row(sku));
        return !driver.findElements(By.id("delete-" + sku)).isEmpty();
    }

    @Step("Search for {term}")
    public InventoryPage search(String term) {
        type(searchInput, term);
        return this;
    }

    @Step("Receive {amount} of {sku}")
    public InventoryPage receive(String sku, int amount) {
        type(By.id("qty-" + sku), String.valueOf(amount));
        click(By.id("receive-" + sku));
        return this;
    }

    @Step("Pick {amount} of {sku}")
    public InventoryPage pick(String sku, int amount) {
        type(By.id("qty-" + sku), String.valueOf(amount));
        click(By.id("pick-" + sku));
        return this;
    }

    @Step("Delete {sku}")
    public InventoryPage delete(String sku) {
        click(By.id("delete-" + sku));
        return this;
    }

    @Step("Add item {sku}")
    public InventoryPage addItem(String sku, String name, int quantity, String location) {
        type(By.id("new-sku"), sku);
        type(By.id("new-name"), name);
        type(By.id("new-qty"), String.valueOf(quantity));
        type(By.id("new-location"), location);
        click(By.id("add-btn"));
        return this;
    }

    public void waitForMessageContaining(String expected) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(message, expected));
    }

    @Step("Log out")
    public void logout() {
        click(logoutButton);
    }
}
