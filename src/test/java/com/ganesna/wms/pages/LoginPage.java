package com.ganesna.wms.pages;

import com.ganesna.wms.config.ConfigReader;
import io.qameta.allure.Step;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

public class LoginPage extends BasePage {

    private final By username = By.id("username");
    private final By password = By.id("password");
    private final By loginButton = By.id("login-btn");
    private final By error = By.id("login-error");

    public LoginPage(WebDriver driver) {
        super(driver);
    }

    @Step("Open the Warehouse app")
    public LoginPage open() {
        String url = ConfigReader.get("base.url");
        driver.get(url);
        try {
            visible(username);
        } catch (TimeoutException first) {
            System.out.println("[ui] login form not visible. url=" + driver.getCurrentUrl()
                    + " title=" + driver.getTitle() + " - loading the page once more");
            driver.get(url);
            visible(username);
        }
        return this;
    }

    @Step("Log in as {user}")
    public LoginPage login(String user, String pass) {
        type(username, user);
        type(password, pass);
        click(loginButton);
        return this;
    }

    public InventoryPage loginAsAdmin() {
        open().login(ConfigReader.get("admin.user"), ConfigReader.get("admin.pass"));
        InventoryPage page = new InventoryPage(driver);
        page.waitForLoaded();
        return page;
    }

    public InventoryPage loginAsPicker() {
        open().login(ConfigReader.get("picker.user"), ConfigReader.get("picker.pass"));
        InventoryPage page = new InventoryPage(driver);
        page.waitForLoaded();
        return page;
    }

    public String errorMessage() {
        wait.until(d -> !d.findElement(error).getText().isBlank());
        return driver.findElement(error).getText();
    }

    public boolean isLoginFormVisible() {
        return visible(loginButton).isDisplayed();
    }
}
