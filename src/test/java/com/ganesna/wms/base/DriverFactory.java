package com.ganesna.wms.base;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.Capabilities;

import java.net.MalformedURLException;
import java.net.URI;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One WebDriver per thread so parallel tests never share a browser.
 * The browser is reused across the tests that run on the same thread; call
 * resetSession() between tests to clear cookies and storage instead of
 * starting a new browser each time.
 */
public final class DriverFactory {

    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    /** Every browser started, so none is left running when the run ends. */
    private static final Set<WebDriver> ALL = ConcurrentHashMap.newKeySet();

    // Strong references: java.util.logging only holds loggers weakly, so without
    // these fields the log levels set below could be garbage-collected and lost.
    private static final Logger CDP_LOG = Logger.getLogger("org.openqa.selenium.devtools.CdpVersionFinder");
    private static final Logger SM_LOG = Logger.getLogger("org.openqa.selenium.manager.SeleniumManager");

    static {
        // Tests don't use DevTools, so the "closest CDP version" warning is just noise
        CDP_LOG.setLevel(Level.OFF);
        // Parallel threads can race on Selenium Manager's cache file; only show real errors
        SM_LOG.setLevel(Level.SEVERE);
        // safety net: close any browser still open if the JVM exits early
        Runtime.getRuntime().addShutdownHook(new Thread(DriverFactory::quitAll, "quit-all-browsers"));
    }

    private DriverFactory() {
    }

    /** Returns this thread's browser, starting one only if there isn't a live one. */
    public static WebDriver startIfNeeded() {
        WebDriver driver = DRIVER.get();
        if (driver == null || !ALL.contains(driver)) {
            start();
            driver = DRIVER.get();
        }
        return driver;
    }

    public static void start() {
        String browser = System.getProperty("browser", "chrome").toLowerCase();
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));
        String gridUrl = System.getProperty("grid.url", "").trim();

        WebDriver driver;
        if (browser.equals("firefox")) {
            FirefoxOptions options = new FirefoxOptions();
            if (headless) {
                options.addArguments("-headless");
            }
            driver = gridUrl.isBlank() ? new FirefoxDriver(options) : remote(gridUrl, options);
        } else {
            ChromeOptions options = new ChromeOptions();
            options.setPageLoadStrategy(PageLoadStrategy.EAGER);
            if (headless) {
                options.addArguments("--headless=new");
            }
            options.addArguments("--window-size=1920,1080", "--disable-notifications",
                    "--disable-gpu", "--no-first-run", "--no-default-browser-check",
                    "--disable-background-networking", "--disable-extensions",
                    "--disable-sync", "--disable-component-update");
            // Sauce Demo shows a "change your password" popup in Chrome; turn it off
            options.setExperimentalOption("prefs", java.util.Map.of(
                    "credentials_enable_service", false,
                    "profile.password_manager_enabled", false,
                    "profile.password_manager_leak_detection", false));
            driver = gridUrl.isBlank() ? new ChromeDriver(options) : remote(gridUrl, options);
        }
        driver.manage().window().maximize();
        DRIVER.set(driver);
        ALL.add(driver);
    }

    /** Starts a browser on a Selenium Grid; used when -Dgrid.url is set (for example http://localhost:4444). */
    private static WebDriver remote(String gridUrl, Capabilities options) {
        try {
            return new RemoteWebDriver(URI.create(gridUrl).toURL(), options);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid grid.url: " + gridUrl, e);
        }
    }

    public static WebDriver get() {
        return DRIVER.get();
    }

    /**
     * Gets this thread's browser ready for the next test: dismisses any open alert,
     * clears cookies, localStorage and sessionStorage, and goes to a blank page.
     * If anything goes wrong the browser is closed, and the next test starts a fresh one.
     */
    public static void resetSession() {
        WebDriver driver = DRIVER.get();
        if (driver == null) {
            return;
        }
        try {
            try {
                driver.switchTo().alert().dismiss();
            } catch (NoAlertPresentException ignored) {
                // no alert open, nothing to do
            }
            driver.manage().deleteAllCookies();
            ((JavascriptExecutor) driver).executeScript(
                    "try { window.localStorage.clear(); window.sessionStorage.clear(); } catch (e) {}");
            driver.get("about:blank");
        } catch (RuntimeException e) {
            System.out.println("[ui] could not reset browser, starting fresh next time: " + e.getMessage());
            quit();
        }
    }

    /** Closes this thread's browser (used after a failure, so the next test gets a clean one). */
    public static void quit() {
        WebDriver driver = DRIVER.get();
        if (driver != null) {
            try {
                driver.quit();
            } catch (RuntimeException ignored) {
                // browser may already be gone
            } finally {
                ALL.remove(driver);
                DRIVER.remove();
            }
        }
    }

    /** Closes every browser started by any thread. Safe to call more than once. */
    public static void quitAll() {
        for (WebDriver driver : ALL) {
            try {
                driver.quit();
            } catch (RuntimeException ignored) {
                // already closed
            }
        }
        ALL.clear();
    }
}