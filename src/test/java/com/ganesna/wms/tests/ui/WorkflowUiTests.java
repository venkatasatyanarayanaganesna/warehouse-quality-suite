package com.ganesna.wms.tests.ui;

import com.ganesna.wms.base.BaseUiTest;
import com.ganesna.wms.base.DriverFactory;
import com.ganesna.wms.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.time.Duration;

@Epic("UI")
@Feature("Stock movements and roles")
public class WorkflowUiTests extends BaseUiTest {

    @Test(description = "An admin moves stock between bins from the UI")
    public void transferFromUiUpdatesBins() {
        WebDriver driver = DriverFactory.get();
        new LoginPage(driver).loginAsAdmin();

        new Select(driver.findElement(By.id("xfer-sku"))).selectByValue("SKU-1002");
        driver.findElement(By.id("xfer-from")).sendKeys("B-02");
        driver.findElement(By.id("xfer-to")).sendKeys("F-06");
        driver.findElement(By.id("xfer-qty")).clear();
        driver.findElement(By.id("xfer-qty")).sendKeys("4");
        driver.findElement(By.id("xfer-btn")).click();

        new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.textToBePresentInElementLocated(
                By.cssSelector("tr[data-sku='SKU-1002'] td.locations"), "F-06: 4"));
    }

    @Test(description = "An admin can see the stock count card")
    public void adminSeesStockCountCard() {
        new LoginPage(DriverFactory.get()).loginAsAdmin();
        Assert.assertTrue(DriverFactory.get().findElement(By.id("adjust-card")).isDisplayed());
    }

    @Test(description = "A picker cannot see the stock count card")
    public void pickerCannotSeeStockCountCard() {
        new LoginPage(DriverFactory.get()).loginAsPicker();
        Assert.assertFalse(DriverFactory.get().findElement(By.id("adjust-card")).isDisplayed());
    }
}
