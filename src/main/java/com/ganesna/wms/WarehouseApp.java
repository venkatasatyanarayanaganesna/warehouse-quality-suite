package com.ganesna.wms;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Warehouse Inventory demo app (the system under test).
 * Run it, then open http://localhost:8080  (admin/admin123 or picker/picker123).
 */
public class WarehouseApp {

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getProperty("port", System.getenv().getOrDefault("PORT", "8080")));
        start(port);
        System.out.println("Warehouse app running at http://localhost:" + port);
        System.out.println("Logins: admin / admin123   picker / picker123   (Ctrl+C to stop)");
    }

    public static HttpServer start(int port) throws IOException {
    // Must be set before the first HttpServer is created (the JDK reads it once)
    if (System.getProperty("sun.net.httpserver.maxIdleConnections") == null) {
        System.setProperty("sun.net.httpserver.maxIdleConnections", "5000");
    }
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 1024);
        server.createContext("/api/", new ApiHandler(new InventoryStore(), new AuthService()));
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newFixedThreadPool(64, task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return server;
    }
}

