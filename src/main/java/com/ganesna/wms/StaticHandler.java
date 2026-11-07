package com.ganesna.wms;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Serves the single-page UI from the classpath (static/index.html). */
public class StaticHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (!path.equals("/") && !path.equals("/index.html")) {
            ex.sendResponseHeaders(404, -1);
            ex.close();
            return;
        }
        try (InputStream in = StaticHandler.class.getResourceAsStream("/static/index.html")) {
            if (in == null) {
                ex.sendResponseHeaders(500, -1);
                return;
            }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(bytes);
            }
        } finally {
            ex.close();
        }
    }
}
