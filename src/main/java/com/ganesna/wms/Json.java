package com.ganesna.wms;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Minimal JSON helpers so the demo app needs no libraries. Parses flat objects only. */
public final class Json {

    private Json() {
    }

    public static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    public static String item(Item i) {
        String locations = i.stock.entrySet().stream()
                .map(e -> "{\"location\":" + quote(e.getKey()) + ",\"quantity\":" + e.getValue() + "}")
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"sku\":" + quote(i.sku)
                + ",\"name\":" + quote(i.name)
                + ",\"quantity\":" + i.total()
                + ",\"location\":" + quote(i.primaryLocation())
                + ",\"reorderLevel\":" + i.reorderLevel
                + ",\"lowStock\":" + i.isLowStock()
                + ",\"locations\":" + locations + "}";
    }

    public static String items(List<Item> items) {
        return items.stream().map(Json::item).collect(Collectors.joining(",", "[", "]"));
    }

    public static String alerts(List<Item> low) {
        return low.stream()
                .map(i -> "{\"sku\":" + quote(i.sku)
                        + ",\"name\":" + quote(i.name)
                        + ",\"quantity\":" + i.total()
                        + ",\"reorderLevel\":" + i.reorderLevel
                        + ",\"suggestedOrder\":" + Math.max(1, i.reorderLevel * 2 - i.total()) + "}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    public static String event(Event e) {
        return "{\"id\":" + e.id
                + ",\"time\":" + quote(e.time)
                + ",\"type\":" + quote(e.type)
                + ",\"sku\":" + quote(e.sku)
                + ",\"location\":" + quote(e.location)
                + ",\"quantity\":" + e.quantity
                + ",\"user\":" + quote(e.user)
                + ",\"note\":" + quote(e.note) + "}";
    }

    public static String events(List<Event> events) {
        return events.stream().map(Json::event).collect(Collectors.joining(",", "[", "]"));
    }

    public static String order(PurchaseOrder o) {
        return "{\"id\":" + o.id
                + ",\"supplier\":" + quote(o.supplier)
                + ",\"sku\":" + quote(o.sku)
                + ",\"ordered\":" + o.ordered
                + ",\"received\":" + o.received
                + ",\"outstanding\":" + o.outstanding()
                + ",\"status\":" + quote(o.status()) + "}";
    }

    public static String orders(List<PurchaseOrder> orders) {
        return orders.stream().map(Json::order).collect(Collectors.joining(",", "[", "]"));
    }

    public static String salesOrder(SalesOrder o) {
        return "{\"id\":" + o.id
                + ",\"customer\":" + quote(o.customer)
                + ",\"sku\":" + quote(o.sku)
                + ",\"ordered\":" + o.ordered
                + ",\"shipped\":" + o.shipped
                + ",\"outstanding\":" + o.outstanding()
                + ",\"status\":" + quote(o.status()) + "}";
    }

    public static String salesOrders(List<SalesOrder> orders) {
        return orders.stream().map(Json::salesOrder).collect(Collectors.joining(",", "[", "]"));
    }

    public static String bin(InventoryStore.BinInfo b) {
        return "{\"location\":" + quote(b.location)
                + ",\"capacity\":" + (b.capacity == null ? "null" : b.capacity.toString())
                + ",\"used\":" + b.used + "}";
    }

    public static String bins(List<InventoryStore.BinInfo> bins) {
        return bins.stream().map(Json::bin).collect(Collectors.joining(",", "[", "]"));
    }

    /** Status of a batch: EXPIRED, EXPIRING (within 30 days), OK, or NO_EXPIRY. */
    public static String lotStatus(Item.Lot l) {
        if (l.expiry == null) {
            return "NO_EXPIRY";
        }
        LocalDate today = LocalDate.now();
        if (l.expiry.isBefore(today)) {
            return "EXPIRED";
        }
        return l.expiry.isAfter(today.plusDays(30)) ? "OK" : "EXPIRING";
    }

    public static String lot(Item.Lot l) {
        return "{\"sku\":" + quote(l.sku)
                + ",\"batch\":" + quote(l.batch)
                + ",\"location\":" + quote(l.location)
                + ",\"expiry\":" + (l.expiry == null ? "null" : quote(l.expiry.toString()))
                + ",\"quantity\":" + l.quantity
                + ",\"status\":" + quote(lotStatus(l)) + "}";
    }

    public static String lots(List<Item.Lot> lots) {
        return lots.stream().map(Json::lot).collect(Collectors.joining(",", "[", "]"));
    }

    public static String countLine(CountLine l) {
        return "{\"sku\":" + quote(l.sku)
                + ",\"location\":" + quote(l.location)
                + ",\"expected\":" + l.expected
                + ",\"counted\":" + (l.counted == null ? "null" : l.counted.toString())
                + ",\"variance\":" + (l.variance() == null ? "null" : l.variance().toString()) + "}";
    }

    public static String count(CountSession s) {
        String bins = s.bins.stream().map(Json::quote).collect(Collectors.joining(",", "[", "]"));
        String lines = s.lines.stream().map(Json::countLine).collect(Collectors.joining(",", "[", "]"));
        return "{\"id\":" + s.id
                + ",\"status\":" + quote(s.status)
                + ",\"bins\":" + bins
                + ",\"lines\":" + lines + "}";
    }

    public static String counts(List<CountSession> sessions) {
        return sessions.stream().map(Json::count).collect(Collectors.joining(",", "[", "]"));
    }

    public static String party(Party p) {
        return "{\"id\":" + p.id + ",\"name\":" + quote(p.name) + "}";
    }

    public static String parties(List<Party> parties) {
        return parties.stream().map(Json::party).collect(Collectors.joining(",", "[", "]"));
    }

    public static String imported(int count) {
        return "{\"imported\":" + count + "}";
    }

    public static String importErrors(List<CsvImport.RowError> errors) {
        String list = errors.stream()
                .map(e -> "{\"line\":" + e.line + ",\"error\":" + quote(e.message) + "}")
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"status\":400,\"error\":"
                + quote("Import rejected: " + errors.size() + " row(s) have errors")
                + ",\"errors\":" + list + "}";
    }

    /** Every error has the same shape: {"status": 409, "error": "message"}. */
    public static String error(int status, String message) {
        return "{\"status\":" + status + ",\"error\":" + quote(message) + "}";
    }

    /**
     * Parses a flat JSON object into a map of strings. Hand-written scanner (no regex), so very
     * long string values cannot cause a StackOverflowError.
     */
    public static Map<String, String> parseFlat(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null) {
            return map;
        }
        int n = body.length();
        int i = 0;
        int[] end = new int[1];
        while (i < n) {
            if (body.charAt(i) != '"') {
                i++;
                continue;
            }
            String key = readString(body, i, end);
            i = end[0];
            int j = skipWs(body, i);
            if (j >= n || body.charAt(j) != ':') {
                continue; // that string was a value, not a key
            }
            j = skipWs(body, j + 1);
            if (j >= n) {
                break;
            }
            if (body.charAt(j) == '"') {
                map.put(key, readString(body, j, end));
                i = end[0];
            } else {
                int k = j;
                while (k < n && ",} \t\r\n".indexOf(body.charAt(k)) < 0) {
                    k++;
                }
                map.put(key, body.substring(j, k));
                i = k;
            }
        }
        return map;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    /** Reads a JSON string starting at the opening quote; end[0] = index after the closing quote. */
    private static String readString(String s, int start, int[] end) {
        StringBuilder sb = new StringBuilder();
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') {
                break;
            }
            if (c == '\\' && i < s.length()) {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'u':
                        if (i + 4 <= s.length()) {
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {
                                sb.append('u');
                            }
                        }
                        break;
                    default: sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
        end[0] = i;
        return sb.toString();
    }
}