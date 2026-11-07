package com.ganesna.wms.base;

import com.ganesna.wms.WarehouseApp;
import com.ganesna.wms.config.ConfigReader;
import com.sun.net.httpserver.HttpServer;
import io.restassured.RestAssured;
import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.io.IOException;
import java.net.URI;

/**
 * Starts the Warehouse app before the suite (or reuses it if it is already running,
 * for example started from VS Code) and resets its data to the seed items.
 * With app.start=false it never starts the app and just tests the one at base.url
 * (for example an app running in another container).
 */
public class AppLifecycleListener implements ISuiteListener {

    private HttpServer server;

    @Override
    public void onStart(ISuite suite) {
        String baseUrl = ConfigReader.get("base.url");
        if (ConfigReader.getBoolean("app.start", true)) {
            int port = URI.create(baseUrl).getPort();
            try {
                server = WarehouseApp.start(port);
                System.out.println("[suite] Started Warehouse app on " + baseUrl);
            } catch (IOException e) {
                System.out.println("[suite] Port " + port + " is busy, using the app already running at " + baseUrl);
            }
        } else {
            System.out.println("[suite] app.start=false, testing the app already running at " + baseUrl);
        }
        RestAssured.given().baseUri(baseUrl).post("/api/test/reset").then().statusCode(204);
    }

    @Override
    public void onFinish(ISuite suite) {
        if (server != null) {
            server.stop(0);
            System.out.println("[suite] Stopped Warehouse app");
        }
    }
}