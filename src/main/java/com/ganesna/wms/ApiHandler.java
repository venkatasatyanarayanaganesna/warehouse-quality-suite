package com.ganesna.wms;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ApiHandler implements HttpHandler {

    /** Remembers what a request answered, so a repeated Idempotency-Key can be replayed. */
    private static final class Recorder {
        int status;
        String body;
    }

    private static final class Cached {
        final int status;
        final String body;

        Cached(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static final ThreadLocal<Recorder> RECORDER = new ThreadLocal<>();

    private final InventoryStore store;
    private final AuthService auth;
    private final Map<String, Cached> replays = new ConcurrentHashMap<>();

    public ApiHandler(InventoryStore store, AuthService auth) {
        this.store = store;
        this.auth = auth;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String idempotencyKey = ex.getRequestHeaders().getFirst("Idempotency-Key");
        String cacheKey = null;

        if ("POST".equals(method) && idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            cacheKey = (auth == null ? "" : auth) + "|" + path + "|" + idempotencyKey.trim();
            Cached hit = replays.get(cacheKey);
            if (hit != null) {
                write(ex, hit.status, hit.body);
                ex.close();
                return;
            }
        }

        Recorder recorder = new Recorder();
        RECORDER.set(recorder);
        try {
            route(ex);
        } catch (ApiException e) {
            send(ex, e.status, Json.error(e.status, e.getMessage()));
        } catch (IllegalStateException e) {
            send(ex, 409, Json.error(409, e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, Json.error(500, "Internal server error"));
        } finally {
            RECORDER.remove();
            ex.close();
        }
        if (cacheKey != null && recorder.status >= 200 && recorder.status < 300) {
            replays.put(cacheKey, new Cached(recorder.status, recorder.body));
        }
    }

    private void route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();

        if (path.equals("/api/health")) {
            send(ex, 200, "{\"status\":\"UP\"}");
            return;
        }
        if (path.equals("/api/login")) {
            requireMethod(method, "POST");
            Map<String, String> body = body(ex);
            String[] session = auth.login(body.get("username"), body.get("password"));
            send(ex, 200, "{\"token\":" + Json.quote(session[0])
                    + ",\"username\":" + Json.quote(body.get("username"))
                    + ",\"role\":" + Json.quote(session[1]) + "}");
            return;
        }
        if (path.equals("/api/test/reset")) {
            requireMethod(method, "POST");
            store.reset();
            replays.clear();
            send(ex, 204, null);
            return;
        }
        if (!path.startsWith("/api/")) {
            throw new ApiException(404, "Not found");
        }

        AuthService.User user = auth.authenticate(ex.getRequestHeaders().getFirst("Authorization"));
        String[] p = path.substring("/api/".length()).split("/");
        switch (p[0]) {
            case "items":
                items(ex, method, p, user);
                break;
            case "alerts":
                requireMethod(method, "GET");
                send(ex, 200, Json.alerts(store.alerts()));
                break;
            case "expiring":
                requireMethod(method, "GET");
                send(ex, 200, Json.lots(store.expiring(intParam(ex, "days", 30))));
                break;
            case "purchase-orders":
                purchaseOrders(ex, method, p, user);
                break;
            case "sales-orders":
                salesOrders(ex, method, p, user);
                break;
            case "locations":
                locations(ex, method, p, user);
                break;
            case "counts":
                counts(ex, method, p, user);
                break;
            case "suppliers":
                parties(ex, method, p, user, "supplier");
                break;
            case "customers":
                parties(ex, method, p, user, "customer");
                break;
            case "audit":
                requireMethod(method, "GET");
                send(ex, 200, Json.events(store.recentEvents(intParam(ex, "limit", 50))));
                break;
            default:
                throw new ApiException(404, "Not found");
        }
    }

    private void items(HttpExchange ex, String method, String[] p, AuthService.User user) throws IOException {
        if (p.length == 2 && p[1].equals("import")) {
            requireMethod(method, "POST");
            importCsv(ex, user);
            return;
        }
        if (p.length == 1) {
            if (method.equals("GET")) {
                send(ex, 200, Json.items(store.list(queryParam(ex, "q"))));
                return;
            }
            requireMethod(method, "POST");
            Map<String, String> b = body(ex);
            Item item = store.create(b.get("sku"), b.get("name"),
                    parseQuantity(b.get("quantity"), true), b.get("location"),
                    b.get("batch"), parseDate(b.get("expiry")), user.name);
            send(ex, 201, Json.item(item));
            return;
        }

        String sku = decode(p[1]);
        if (p.length == 2) {
            if (method.equals("GET")) {
                send(ex, 200, Json.item(store.get(sku)));
                return;
            }
            requireMethod(method, "DELETE");
            requireAdmin(user, "Only admins can delete items");
            store.delete(sku, user.name);
            send(ex, 204, null);
            return;
        }
        if (p.length != 3) {
            throw new ApiException(404, "Not found");
        }

        switch (p[2]) {
            case "receive": {
                requireMethod(method, "POST");
                Map<String, String> b = body(ex);
                int q = parseQuantity(b.get("quantity"), false);
                send(ex, 200, Json.item(store.receive(sku, q, b.get("location"),
                        b.get("batch"), parseDate(b.get("expiry")), user.name)));
                return;
            }
            case "pick": {
                requireMethod(method, "POST");
                Map<String, String> b = body(ex);
                int q = parseQuantity(b.get("quantity"), false);
                send(ex, 200, Json.item(store.pick(sku, q, b.get("location"), user.name)));
                return;
            }
            case "transfer": {
                requireMethod(method, "POST");
                Map<String, String> b = body(ex);
                int q = parseQuantity(b.get("quantity"), false);
                send(ex, 200, Json.item(store.transfer(sku, b.get("from"), b.get("to"), q, user.name)));
                return;
            }
            case "adjust": {
                requireMethod(method, "POST");
                requireAdmin(user, "Only admins can adjust stock");
                Map<String, String> b = body(ex);
                int q = parseQuantity(b.get("quantity"), true);
                send(ex, 200, Json.item(store.adjust(sku, b.get("location"), q, b.get("reason"), user.name)));
                return;
            }
            case "history":
                requireMethod(method, "GET");
                send(ex, 200, Json.events(store.history(sku)));
                return;
            case "lots":
                requireMethod(method, "GET");
                send(ex, 200, Json.lots(store.lotsOf(sku)));
                return;
            default:
                throw new ApiException(404, "Not found");
        }
    }

    private void purchaseOrders(HttpExchange ex, String method, String[] p, AuthService.User user) throws IOException {
        if (p.length == 1) {
            if (method.equals("GET")) {
                send(ex, 200, Json.orders(store.orders()));
                return;
            }
            requireMethod(method, "POST");
            requireAdmin(user, "Only admins can create purchase orders");
            Map<String, String> b = body(ex);
            int q = parseQuantity(b.get("quantity"), false);
            send(ex, 201, Json.order(store.createOrder(b.get("supplier"), b.get("sku"), q, b.get("location"), user.name)));
            return;
        }
        if (p.length == 3 && p[2].equals("receive")) {
            requireMethod(method, "POST");
            long id = parseId(p[1], "Purchase order not found");
            Map<String, String> b = body(ex);
            int q = parseQuantity(b.get("quantity"), false);
            send(ex, 200, Json.order(store.receiveOrder(id, q, b.get("location"), user.name)));
            return;
        }
        throw new ApiException(404, "Not found");
    }

    private void salesOrders(HttpExchange ex, String method, String[] p, AuthService.User user) throws IOException {
        if (p.length == 1) {
            if (method.equals("GET")) {
                send(ex, 200, Json.salesOrders(store.salesOrders()));
                return;
            }
            requireMethod(method, "POST");
            Map<String, String> b = body(ex);
            int q = parseQuantity(b.get("quantity"), false);
            send(ex, 201, Json.salesOrder(store.createSalesOrder(b.get("customer"), b.get("sku"), q, user.name)));
            return;
        }
        if (p.length == 3 && p[2].equals("ship")) {
            requireMethod(method, "POST");
            long id = parseId(p[1], "Sales order not found");
            Map<String, String> b = body(ex);
            int q = parseQuantity(b.get("quantity"), false);
            send(ex, 200, Json.salesOrder(store.shipSalesOrder(id, q, user.name)));
            return;
        }
        throw new ApiException(404, "Not found");
    }

    private void locations(HttpExchange ex, String method, String[] p, AuthService.User user) throws IOException {
        if (p.length == 1) {
            requireMethod(method, "GET");
            send(ex, 200, Json.bins(store.locations()));
            return;
        }
        if (p.length == 3 && p[2].equals("capacity")) {
            requireMethod(method, "PUT");
            requireAdmin(user, "Only admins can set bin capacity");
            Map<String, String> b = body(ex);
            int limit = parseQuantity(b.get("capacity"), true);
            send(ex, 200, Json.bin(store.setCapacity(decode(p[1]), limit, user.name)));
            return;
        }
        throw new ApiException(404, "Not found");
    }

    private void counts(HttpExchange ex, String method, String[] p, AuthService.User user) throws IOException {
        if (p.length == 1) {
            if (method.equals("GET")) {
                send(ex, 200, Json.counts(store.countList()));
                return;
            }
            requireMethod(method, "POST");
            Map<String, String> b = body(ex);
            send(ex, 201, Json.count(store.createCount(splitBins(b.get("bins")), user.name)));
            return;
        }
        long id = parseId(p[1], "Count not found");
        if (p.length == 2) {
            requireMethod(method, "GET");
            send(ex, 200, Json.count(store.count(id)));
            return;
        }
        if (p.length == 3) {
            switch (p[2]) {
                case "lines": {
                    requireMethod(method, "POST");
                    Map<String, String> b = body(ex);
                    int counted = parseQuantity(b.get("counted"), true);
                    send(ex, 200, Json.count(store.recordCount(id, b.get("sku"), b.get("location"), counted, user.name)));
                    return;
                }
                case "submit":
                    requireMethod(method, "POST");
                    send(ex, 200, Json.count(store.submitCount(id, user.name)));
                    return;
                case "approve":
                    requireMethod(method, "POST");
                    requireAdmin(user, "Only admins can approve counts");
                    send(ex, 200, Json.count(store.approveCount(id, user.name)));
                    return;
                default:
                    break;
            }
        }
        throw new ApiException(404, "Not found");
    }

    private void parties(HttpExchange ex, String method, String[] p, AuthService.User user, String kind)
            throws IOException {
        if (p.length == 1) {
            if (method.equals("GET")) {
                send(ex, 200, Json.parties(store.parties(kind)));
                return;
            }
            requireMethod(method, "POST");
            requireAdmin(user, "Only admins can add " + kind + "s");
            Map<String, String> b = body(ex);
            send(ex, 201, Json.party(store.addParty(kind, b.get("name"), user.name)));
            return;
        }
        throw new ApiException(404, "Not found");
    }

    /** Reads a CSV body. Any bad row rejects the whole file and every error is listed. */
    private void importCsv(HttpExchange ex, AuthService.User user) throws IOException {
        String text = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        CsvImport.Result result = CsvImport.parse(text);
        if (!result.errors.isEmpty()) {
            send(ex, 400, Json.importErrors(result.errors));
            return;
        }
        if (result.rows.isEmpty()) {
            throw new ApiException(400, "The file has no data rows");
        }
        int imported = store.importItems(result.rows, user.name);
        send(ex, 201, Json.imported(imported));
    }

    private static List<String> splitBins(String raw) {
        List<String> bins = new ArrayList<>();
        if (raw == null) {
            return bins;
        }
        for (String part : raw.split(",")) {
            if (!part.trim().isEmpty()) {
                bins.add(part.trim());
            }
        }
        return bins;
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(400, "Expiry must be a date (YYYY-MM-DD)");
        }
    }

    private static long parseId(String raw, String notFound) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new ApiException(404, notFound);
        }
    }

    private static int parseQuantity(String raw, boolean allowZero) {
        int value;
        try {
            value = Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Quantity must be a whole number");
        }
        if (value < 0 || (!allowZero && value == 0)) {
            throw new ApiException(400, allowZero
                    ? "Quantity cannot be negative"
                    : "Quantity must be a positive whole number");
        }
        return value;
    }

    private static void requireMethod(String actual, String expected) {
        if (!actual.equals(expected)) {
            throw new ApiException(405, "Method not allowed");
        }
    }

    private static void requireAdmin(AuthService.User user, String message) {
        if (!user.isAdmin()) {
            throw new ApiException(403, message);
        }
    }

    private static Map<String, String> body(HttpExchange ex) throws IOException {
        return Json.parseFlat(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String queryParam(HttpExchange ex, String name) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equals(name)) {
                return decode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static int intParam(HttpExchange ex, String name, int fallback) {
        String raw = queryParam(ex, name);
        try {
            return raw == null ? fallback : Math.min(500, Math.max(0, Integer.parseInt(raw)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        Recorder recorder = RECORDER.get();
        if (recorder != null) {
            recorder.status = status;
            recorder.body = body;
        }
        write(ex, status, body);
    }

    private static void write(HttpExchange ex, int status, String body) throws IOException {
        if (body == null) {
            ex.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
