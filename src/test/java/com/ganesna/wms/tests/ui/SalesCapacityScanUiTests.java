package com.ganesna.wms.tests.ui;

import com.ganesna.wms.base.BaseUiTest;
import com.ganesna.wms.base.DriverFactory;
import com.ganesna.wms.pages.LoginPage;
import com.ganesna.wms.support.ApiClient;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Epic("UI")
@Feature("Sales, capacity and scanning")
public class SalesCapacityScanUiTests extends BaseUiTest {

    private WebDriver login() {
        WebDriver driver = DriverFactory.get();
        new LoginPage(driver).loginAsAdmin();
        return driver;
    }

    private WebDriverWait wait(WebDriver driver) {
        return new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @Test(description = "An admin creates a sales order from the UI and sees it OPEN")
    public void createSalesOrderFromUi() {
        WebDriver driver = login();
        String customer = "UI Shop " + UUID.randomUUID().toString().substring(0, 6);
        // customers must be managed records, so register this one first
        ApiClient.authed(ApiClient.adminToken()).body(Map.of("name", customer))
                .when().post("/api/customers").then().statusCode(201);

        new Select(driver.findElement(By.id("so-sku"))).selectByValue("SKU-1002");
        driver.findElement(By.id("so-customer")).sendKeys(customer);
        driver.findElement(By.id("so-qty")).clear();
        driver.findElement(By.id("so-qty")).sendKeys("2");
        driver.findElement(By.id("so-btn")).click();

        wait(driver).until(ExpectedConditions.textToBePresentInElementLocated(By.id("so-body"), customer));
        Assert.assertTrue(driver.findElement(By.id("so-body")).getText().contains("OPEN"));
    }

    @Test(description = "Scanning a SKU highlights its row and focuses the quantity box")
    public void scanHighlightsRow() {
        WebDriver driver = login();
        driver.findElement(By.id("scan-input")).sendKeys("SKU-1003" + Keys.ENTER);

        wait(driver).until(ExpectedConditions.attributeContains(
                By.cssSelector("tr[data-sku='SKU-1003']"), "class", "hit"));
        Assert.assertEquals(driver.findElement(By.id("scan-hint")).getText(), "Found SKU-1003");
    }

    @Test(description = "An admin sets a bin limit and sees it in the bin list")
    public void setCapacityFromUi() {
        WebDriver driver = login();
        String bin = "UI-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        driver.findElement(By.id("cap-bin")).sendKeys(bin);
        driver.findElement(By.id("cap-limit")).clear();
        driver.findElement(By.id("cap-limit")).sendKeys("5");
        driver.findElement(By.id("cap-btn")).click();

        wait(driver).until(ExpectedConditions.textToBePresentInElementLocated(By.id("bins-list"), "of 5"));
        Assert.assertTrue(driver.findElement(By.id("bins-list")).getText().contains(bin));
    }
}
