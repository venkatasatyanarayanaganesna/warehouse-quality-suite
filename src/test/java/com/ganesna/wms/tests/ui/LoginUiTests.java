package com.ganesna.wms.tests.ui;

import com.ganesna.wms.base.BaseUiTest;
import com.ganesna.wms.base.DriverFactory;
import com.ganesna.wms.pages.InventoryPage;
import com.ganesna.wms.pages.LoginPage;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@Epic("UI")
@Feature("Login")
public class LoginUiTests extends BaseUiTest {

    @DataProvider(name = "badLogins")
    public Object[][] badLogins() {
        return new Object[][]{
                {"admin", "wrong-password", "Invalid username or password"},
                {"nobody", "admin123", "Invalid username or password"},
                {"", "", "required"},
                {"admin", "", "required"}
        };
    }

    @Test(description = "Admin logs in and sees the stock list")
    public void adminLoginShowsInventory() {
    InventoryPage inventory = new LoginPage(DriverFactory.get()).loginAsAdmin();
    Assert.assertTrue(inventory.loggedInAs().contains("admin"));
    Assert.assertTrue(inventory.hasRow("SKU-1001"));
}

    @Test(dataProvider = "badLogins", description = "Bad logins show a clear error")
    public void badLoginShowsError(String user, String pass, String expectedPart) {
        LoginPage login = new LoginPage(DriverFactory.get()).open().login(user, pass);
        Assert.assertTrue(login.errorMessage().contains(expectedPart),
                "Unexpected error: " + login.errorMessage());
    }

    @Test(description = "Logging out returns to the login form")
    public void logoutReturnsToLogin() {
        LoginPage login = new LoginPage(DriverFactory.get());
        login.loginAsAdmin().logout();
        Assert.assertTrue(login.isLoginFormVisible());
    }
}
