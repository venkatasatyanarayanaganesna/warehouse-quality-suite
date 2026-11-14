package com.ganesna.wms.tests.ui;

import com.deque.html.axecore.results.Results;
import com.deque.html.axecore.results.Rule;
import com.deque.html.axecore.selenium.AxeBuilder;
import com.ganesna.wms.base.BaseUiTest;
import com.ganesna.wms.base.DriverFactory;
import com.ganesna.wms.pages.InventoryPage;
import com.ganesna.wms.pages.LoginPage;
import io.qameta.allure.Allure;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

/**
 * Automated accessibility scans (axe-core, WCAG 2.0 A and AA rules).
 * By default the findings are printed and attached to the Allure report without failing the test.
 * Run with -Da11y.strict=true to make any violation fail the build.
 */
@Epic("UI")
@Feature("Accessibility")
public class AccessibilityUiTests extends BaseUiTest {

    private static final boolean STRICT = Boolean.parseBoolean(System.getProperty("a11y.strict", "false"));

    private void scan(String pageName) {
        Results results = new AxeBuilder()
                .withTags(List.of("wcag2a", "wcag2aa"))
                .analyze(DriverFactory.get());
        Assert.assertNotNull(results, "axe returned no results for " + pageName);

        List<Rule> violations = results.getViolations();
        StringBuilder report = new StringBuilder();
        for (Rule rule : violations) {
            report.append(rule.getImpact()).append(" | ")
                    .append(rule.getId()).append(" | ")
                    .append(rule.getHelp()).append(System.lineSeparator());
        }
        String summary = pageName + ": " + violations.size() + " accessibility rule(s) violated";
        System.out.println("[a11y] " + summary + System.lineSeparator() + report);
        Allure.addAttachment("Accessibility findings: " + pageName, summary + System.lineSeparator() + report);

        if (STRICT) {
            Assert.assertTrue(violations.isEmpty(), summary + System.lineSeparator() + report);
        }
    }

    @Test(description = "Login page: axe scan for WCAG 2.0 A and AA violations")
    public void loginPageScan() {
        new LoginPage(DriverFactory.get()).open();
        scan("login page");
    }

    @Test(description = "Inventory page: axe scan for WCAG 2.0 A and AA violations")
    public void inventoryPageScan() {
        InventoryPage inventory = new LoginPage(DriverFactory.get()).loginAsAdmin();
        Assert.assertTrue(inventory.hasRow("SKU-1001"), "stock list did not load");
        scan("inventory page");
    }
}
