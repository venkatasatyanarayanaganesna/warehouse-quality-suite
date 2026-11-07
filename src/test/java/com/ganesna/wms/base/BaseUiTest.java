package com.ganesna.wms.base;

import io.qameta.allure.Allure;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class BaseUiTest {

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        // reuses this thread's browser; only starts one if there isn't a live one
        DriverFactory.startIfNeeded();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
    WebDriver driver = DriverFactory.get();
    if (driver == null) {
        return;
    }
    // TestNG marks an attempt that will be retried as SKIP (with a throwable), not FAILURE
    boolean failed = result.getStatus() == ITestResult.FAILURE
            || (result.getStatus() == ITestResult.SKIP && result.getThrowable() != null);
    if (failed) {
        try {
            captureFailureDetails(driver, result.getName());
        } finally {
            // a failed test may leave the browser in a bad state: next test gets a fresh one
            DriverFactory.quit();
        }
    } else {
        DriverFactory.resetSession();
    }
}

    /** Closes every browser once all tests in the suite have finished. */
    @AfterSuite(alwaysRun = true)
    public void closeAllBrowsers() {
        DriverFactory.quitAll();
    }

    /** Prints what the browser was showing and saves a screenshot to target/screenshots. */
    private void captureFailureDetails(WebDriver driver, String testName) {
        try {
            String source = driver.getPageSource();
            System.out.println("[ui] FAILED " + testName
                    + " | url=" + driver.getCurrentUrl()
                    + " | title=" + driver.getTitle()
                    + " | page starts: " + source.substring(0, Math.min(200, source.length())).replaceAll("\\s+", " "));

            byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Allure.addAttachment("Screenshot on failure", "image/png", new ByteArrayInputStream(png), "png");
            Allure.addAttachment("Page source", "text/html", source, ".html");

            Path dir = Path.of("target", "screenshots");
            Files.createDirectories(dir);
            Files.write(dir.resolve(testName + "-" + System.currentTimeMillis() + ".png"), png);
        } catch (Exception e) {
            System.out.println("[ui] could not capture failure details: " + e.getMessage());
        }
    }
}