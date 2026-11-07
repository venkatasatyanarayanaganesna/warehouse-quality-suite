package com.ganesna.wms;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * In-memory warehouse model. Every public method is synchronized, so concurrent requests
 * can never oversell, overfill a bin or double-apply a count. Returned objects are copies.
 */
public class InventoryStore {

    public static final class BinInfo {
        public final String location;
        public final Integer capacity;
        public final int used;

        BinInfo(String location, Integer capacity, int used) {
            this.location = location;
            this.capacity = capacity;
            this.used = used;
        }
    }

    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Z0-9-]{1,30}$");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("^[A-Z0-9-]{1,20}$");

    private final Map<String, Item> items = new LinkedHashMap<>();
    private final List<PurchaseOrder> orders = new ArrayList<>();
    private final List<SalesOrder> salesOrders = new ArrayList<>();
    private final List<CountSession> counts = new ArrayList<>();
    private final List<Party> suppliers = new ArrayList<>();
    private final List<Party> customers = new ArrayList<>();
    private long nextPartyId = 1;
    private final Map<String, Integer> capacity = new LinkedHashMap<>();
    private final List<Event> events = new ArrayList<>();
    private long nextEventId = 1;
    private long nextOrderId = 1;
    private long nextSalesOrderId = 1;
    private long nextCountId = 1;

    public InventoryStore() {
        reset();
    }

    public synchronized void reset() {
        items.clear();
        orders.clear();
        salesOrders.clear();
        counts.clear();
        suppliers.clear();
        customers.clear();
        nextPartyId = 1;
        capacity.clear();
        events.clear();
        nextEventId = 1;
        nextOrderId = 1;
        nextSalesOrderId = 1;
        nextCountId = 1;
        seed("SKU-1001", "Pallet Jack", "A-01", 20, "E-09", 5);
        seed("SKU-1002", "Barcode Scanner", "B-02", 40);
        seed("SKU-1003", "Shrink Wrap Roll", "C-03", 120);
        seed("SKU-1004", "Forklift Battery", "D-04", 6);
        for (String s : new String[]{"Acme", "Acme Supply"}) {
            suppliers.add(new Party(nextPartyId++, s));
        }
        for (String c : new String[]{"Shop Ltd", "Shop", "Shop Co"}) {
            customers.add(new Party(nextPartyId++, c));
        }
    }

    private void seed(String sku, String name, Object... locationQuantityPairs) {
        Item item = new Item(sku, name, Item.DEFAULT_REORDER_LEVEL);
        for (int i = 0; i < locationQuantityPairs.length; i += 2) {
            item.add((String) locationQuantityPairs[i], (Integer) locationQuantityPairs[i + 1]);
        }
        items.put(sku, item);
    }

    // ---------- items ----------

    public synchronized List<Item> list(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Item> result = new ArrayList<>();
        for (Item item : items.values()) {
            if (q.isEmpty()
                    || item.sku.toLowerCase(Locale.ROOT).contains(q)
                    || item.name.toLowerCase(Locale.ROOT).contains(q)) {
                result.add(item.copy());
            }
        }
        return result;
    }

    public synchronized Item get(String sku) {
        return find(sku).copy();
    }

    public synchronized Item create(String sku, String name, int quantity, String location,
                                    String batch, LocalDate expiry, String user) {
        String key = normalise(sku);
        if (!SKU_PATTERN.matcher(key).matches()) {
            throw new ApiException(400, "Invalid SKU (use letters, digits and dashes, max 30)");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new ApiException(400, "Name is required");
        }
        if (quantity < 0) {
            throw new ApiException(400, "Quantity cannot be negative");
        }
        if (items.containsKey(key)) {
            throw new ApiException(409, "SKU already exists");
        }
        String loc = blank(location) ? Item.UNASSIGNED : locationKey(location);
        assertRoom(loc, quantity);
        Item item = new Item(key, name.trim(), Item.DEFAULT_REORDER_LEVEL);
        if (quantity > 0) {
            item.add(loc, quantity, batch, expiry);
        }
        items.put(key, item);
        log("CREATE", key, loc, quantity, user, name.trim());
        return item.copy();
    }

    public synchronized Item receive(String sku, int quantity, String location,
                                     String batch, LocalDate expiry, String user) {
        Item item = find(sku);
        String loc = blank(location) ? item.primaryLocation() : locationKey(location);
        assertRoom(loc, quantity);
        item.add(loc, quantity, batch, expiry);
        log("RECEIVE", item.sku, loc, quantity, user, batch == null ? null : "batch " + batch);
        return item.copy();
    }

    /** Only usable stock (not expired, not reserved) can be picked. */
    public synchronized Item pick(String sku, int quantity, String location, String user) {
        Item item = find(sku);
        int reserved = reserved(item.sku);
        int available = item.usableTotal() - reserved;
        if (quantity > available) {
            throw new ApiException(409, "Insufficient stock (" + Math.max(0, available)
                    + " available after " + reserved + " reserved)");
        }
        if (!blank(location)) {
            String loc = locationKey(location);
            if (item.at(loc) == 0) {
                throw new ApiException(409, "No stock at location " + loc);
            }
            if (item.usableAt(loc) < quantity) {
                throw new ApiException(409, "Insufficient stock at " + loc);
            }
            item.take(loc, quantity, false);
            log("PICK", item.sku, loc, quantity, user, null);
        } else {
            for (Item.Lot taken : item.takeFefo(quantity)) {
                log("PICK", item.sku, taken.location, taken.quantity, user,
                        taken.batch.equals(Item.NO_BATCH) ? null : "batch " + taken.batch);
            }
        }
        return item.copy();
    }

    public synchronized Item transfer(String sku, String from, String to, int quantity, String user) {
        if (blank(from) || blank(to)) {
            throw new ApiException(400, "from and to locations are required");
        }
        Item item = find(sku);
        String src = locationKey(from);
        String dst = locationKey(to);
        if (src.equals(dst)) {
            throw new ApiException(400, "Source and destination must be different");
        }
        if (item.usableAt(src) < quantity) {
            throw new ApiException(409, "Insufficient stock at " + src);
        }
        assertRoom(dst, quantity);
        item.transfer(src, dst, quantity);
        log("TRANSFER", item.sku, src, quantity, user, "to " + dst);
        return item.copy();
    }

    /** Sets the counted quantity at one bin. The difference is recorded. */
    public synchronized Item adjust(String sku, String location, int newQuantity, String reason, String user) {
        if (blank(location)) {
            throw new ApiException(400, "Location is required");
        }
        if (blank(reason)) {
            throw new ApiException(400, "A reason is required for adjustments");
        }
        Item item = find(sku);
        String loc = locationKey(location);
        int before = item.at(loc);
        assertRoom(loc, newQuantity - before);
        item.set(loc, newQuantity);
        log("ADJUST", item.sku, loc, newQuantity - before, user,
                reason.trim() + " (was " + before + ", now " + newQuantity + ")");
        return item.copy();
    }

    public synchronized void delete(String sku, String user) {
        Item item = find(sku);
        items.remove(item.sku);
        log("DELETE", item.sku, null, item.total(), user, item.name);
    }

    public synchronized List<Item> alerts() {
        List<Item> low = new ArrayList<>();
        for (Item item : items.values()) {
            if (item.isLowStock()) {
                low.add(item.copy());
            }
        }
        return low;
    }

    /** Batches for one item, in bin order. */
    public synchronized List<Item.Lot> lotsOf(String sku) {
        List<Item.Lot> result = new ArrayList<>();
        for (Item.Lot l : find(sku).lots) {
            result.add(l.copy());
        }
        return result;
    }

    /** Batches across the warehouse that are expired or expire within the given number of days. */
    public synchronized List<Item.Lot> expiring(int days) {
        LocalDate limit = LocalDate.now().plusDays(days);
        List<Item.Lot> result = new ArrayList<>();
        for (Item item : items.values()) {
            for (Item.Lot l : item.lots) {
                if (l.expiry != null && !l.expiry.isAfter(limit)) {
                    result.add(l.copy());
                }
            }
        }
        return result;
    }

    // ---------- purchase orders ----------

    public synchronized PurchaseOrder createOrder(String supplier, String sku, int quantity,
                                                  String location, String user) {
        String supplierName = resolveParty("supplier", supplier);
        Item item = find(sku);
        String loc = blank(location) ? item.primaryLocation() : locationKey(location);
        PurchaseOrder order = new PurchaseOrder(nextOrderId++, supplierName, item.sku, quantity);
        orders.add(order);
        log("PO_CREATE", item.sku, loc, quantity, user, "PO-" + order.id + " from " + order.supplier);
        return order.copy();
    }

    public synchronized PurchaseOrder receiveOrder(long id, int quantity, String location, String user) {
        PurchaseOrder order = findOrder(id);
        if (order.outstanding() == 0) {
            throw new ApiException(409, "Order is already fully received");
        }
        if (quantity > order.outstanding()) {
            throw new ApiException(409, "Quantity exceeds outstanding amount (" + order.outstanding() + ")");
        }
        Item item = find(order.sku);
        String loc = blank(location) ? item.primaryLocation() : locationKey(location);
        assertRoom(loc, quantity);
        item.add(loc, quantity);
        order.received += quantity;
        log("PO_RECEIVE", item.sku, loc, quantity, user, "PO-" + id);
        return order.copy();
    }

    public synchronized List<PurchaseOrder> orders() {
        List<PurchaseOrder> copies = new ArrayList<>();
        for (PurchaseOrder o : orders) {
            copies.add(o.copy());
        }
        return copies;
    }

    // ---------- sales orders ----------

    public synchronized SalesOrder createSalesOrder(String customer, String sku, int quantity, String user) {
        String customerName = resolveParty("customer", customer);
        Item item = find(sku);
        int available = item.usableTotal() - reserved(item.sku);
        if (quantity > available) {
            throw new ApiException(409, "Insufficient available stock ("
                    + Math.max(0, available) + " available after reservations)");
        }
        SalesOrder order = new SalesOrder(nextSalesOrderId++, customerName, item.sku, quantity);
        salesOrders.add(order);
        log("SO_CREATE", item.sku, null, quantity, user, "SO-" + order.id + " for " + order.customer);
        return order.copy();
    }

    public synchronized SalesOrder shipSalesOrder(long id, int quantity, String user) {
        SalesOrder order = findSalesOrder(id);
        if (order.outstanding() == 0) {
            throw new ApiException(409, "Order is already fully shipped");
        }
        if (quantity > order.outstanding()) {
            throw new ApiException(409, "Quantity exceeds outstanding amount (" + order.outstanding() + ")");
        }
        Item item = find(order.sku);
        if (item.usableTotal() < quantity) {
            throw new ApiException(409, "Insufficient stock to ship");
        }
        for (Item.Lot taken : item.takeFefo(quantity)) {
            log("SHIP", item.sku, taken.location, taken.quantity, user,
                    "SO-" + id + " to " + order.customer);
        }
        order.shipped += quantity;
        return order.copy();
    }

    public synchronized List<SalesOrder> salesOrders() {
        List<SalesOrder> copies = new ArrayList<>();
        for (SalesOrder o : salesOrders) {
            copies.add(o.copy());
        }
        return copies;
    }

    // ---------- bins and capacity ----------

    public synchronized BinInfo setCapacity(String location, int limit, String user) {
        String loc = locationKey(location);
        if (limit < 0) {
            throw new ApiException(400, "Capacity cannot be negative");
        }
        int used = binUsage(loc);
        if (limit < used) {
            throw new ApiException(409, "Capacity " + limit + " is below current usage " + used);
        }
        capacity.put(loc, limit);
        log("CAPACITY", null, loc, limit, user, "capacity set to " + limit);
        return new BinInfo(loc, limit, used);
    }

    public synchronized List<BinInfo> locations() {
        Set<String> names = new TreeSet<>(capacity.keySet());
        for (Item item : items.values()) {
            names.addAll(item.stock.keySet());
        }
        List<BinInfo> result = new ArrayList<>();
        for (String name : names) {
            result.add(new BinInfo(name, capacity.get(name), binUsage(name)));
        }
        return result;
    }

    // ---------- cycle counts ----------

    /** Snapshots every item held in the chosen bins. Counting happens against this snapshot. */
    public synchronized CountSession createCount(List<String> bins, String user) {
        if (bins == null || bins.isEmpty()) {
            throw new ApiException(400, "At least one bin is required");
        }
        List<String> keys = new ArrayList<>();
        for (String bin : bins) {
            String key = locationKey(bin);
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        CountSession session = new CountSession(nextCountId++, keys);
        for (Item item : items.values()) {
            for (Map.Entry<String, Integer> e : item.stock.entrySet()) {
                if (keys.contains(e.getKey())) {
                    session.lines.add(new CountLine(item.sku, e.getKey(), e.getValue()));
                }
            }
        }
        counts.add(session);
        log("COUNT_START", null, String.join(",", keys), session.lines.size(), user, "Count #" + session.id);
        return session.copy();
    }

    public synchronized CountSession count(long id) {
        return findCount(id).copy();
    }

    public synchronized List<CountSession> countList() {
        List<CountSession> copies = new ArrayList<>();
        for (CountSession s : counts) {
            copies.add(s.copy());
        }
        return copies;
    }

    public synchronized CountSession recordCount(long id, String sku, String location, int counted, String user) {
        CountSession session = findCount(id);
        if (!"OPEN".equals(session.status)) {
            throw new ApiException(409, "Count is " + session.status + ", not open for recording");
        }
        String key = normalise(sku);
        String loc = locationKey(location);
        for (CountLine line : session.lines) {
            if (line.sku.equals(key) && line.location.equals(loc)) {
                line.counted = counted;
                return session.copy();
            }
        }
        throw new ApiException(404, "No count line for " + key + " at " + loc);
    }

    public synchronized CountSession submitCount(long id, String user) {
        CountSession session = findCount(id);
        if (!"OPEN".equals(session.status)) {
            throw new ApiException(409, "Count is already " + session.status);
        }
        long missing = session.lines.stream().filter(l -> l.counted == null).count();
        if (missing > 0) {
            throw new ApiException(409, missing + " line(s) not counted yet");
        }
        session.status = "SUBMITTED";
        log("COUNT_SUBMIT", null, null, session.lines.size(), user, "Count #" + id);
        return session.copy();
    }

    /** Applies every submitted count. Checks everything first, so either all lines apply or none do. */
    public synchronized CountSession approveCount(long id, String user) {
        CountSession session = findCount(id);
        if (!"SUBMITTED".equals(session.status)) {
            throw new ApiException(409, "Count must be submitted before approval (status " + session.status + ")");
        }
        Map<String, Integer> incomingByBin = new LinkedHashMap<>();
        for (CountLine line : session.lines) {
            Item item = find(line.sku);
            int before = item.at(line.location);
            int delta = line.counted - before;
            if (delta > 0) {
                incomingByBin.merge(line.location, delta, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : incomingByBin.entrySet()) {
            assertRoom(e.getKey(), e.getValue());
        }
        for (CountLine line : session.lines) {
            Item item = find(line.sku);
            int before = item.at(line.location);
            if (line.counted != before) {
                item.set(line.location, line.counted);
                log("ADJUST", item.sku, line.location, line.counted - before, user,
                        "Count #" + id + " (expected " + line.expected + ", counted " + line.counted + ")");
            }
        }
        session.status = "APPROVED";
        log("COUNT_APPROVE", null, String.join(",", session.bins), session.lines.size(), user, "Count #" + id);
        return session.copy();
    }

    // ---------- audit trail ----------

    /** Newest first. */
    public synchronized List<Event> history(String sku) {
        String key = find(sku).sku;
        List<Event> result = new ArrayList<>();
        for (Event e : events) {
            if (key.equals(e.sku)) {
                result.add(0, e);
            }
        }
        return result;
    }

    /** Newest first, at most limit entries. */
    public synchronized List<Event> recentEvents(int limit) {
        List<Event> result = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0 && result.size() < limit; i--) {
            result.add(events.get(i));
        }
        return result;
    }

    // ---------- suppliers and customers ----------

    public synchronized List<Party> parties(String kind) {
        return new ArrayList<>(partyList(kind));
    }

    public synchronized Party addParty(String kind, String name, String user) {
        String label = capitalise(kind);
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            throw new ApiException(400, label + " name is required");
        }
        if (clean.length() > 60) {
            throw new ApiException(400, "Name is too long (max 60 characters)");
        }
        for (Party p : partyList(kind)) {
            if (p.name.equalsIgnoreCase(clean)) {
                throw new ApiException(409, label + " already exists");
            }
        }
        Party party = new Party(nextPartyId++, clean);
        partyList(kind).add(party);
        log("PARTY_CREATE", null, null, 0, user, kind + " " + clean);
        return party;
    }

    private List<Party> partyList(String kind) {
        return "supplier".equals(kind) ? suppliers : customers;
    }

    /** Orders must name a managed party, matched without regard to case. */
    private String resolveParty(String kind, String name) {
        if (blank(name)) {
            throw new ApiException(400, capitalise(kind) + " is required");
        }
        for (Party p : partyList(kind)) {
            if (p.name.equalsIgnoreCase(name.trim())) {
                return p.name;
            }
        }
        throw new ApiException(400, "Unknown " + kind + ": " + name.trim());
    }

    private static String capitalise(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------- CSV import ----------

    /** All rows are checked against the warehouse first; either every row is created or none is. */
    public synchronized int importItems(List<CsvImport.Row> rows, String user) {
        Map<String, Integer> incoming = new HashMap<>();
        for (CsvImport.Row r : rows) {
            if (items.containsKey(r.sku)) {
                throw new ApiException(409, "SKU already exists: " + r.sku);
            }
            if (r.quantity > 0) {
                incoming.merge(r.location == null ? Item.UNASSIGNED : r.location, r.quantity, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : incoming.entrySet()) {
            assertRoom(e.getKey(), e.getValue());
        }
        for (CsvImport.Row r : rows) {
            create(r.sku, r.name, r.quantity, r.location, r.batch, r.expiry, user);
        }
        return rows.size();
    }

    // ---------- helpers ----------

    private int reserved(String sku) {
        int total = 0;
        for (SalesOrder order : salesOrders) {
            if (order.sku.equals(sku)) {
                total += order.outstanding();
            }
        }
        return total;
    }

    private int binUsage(String location) {
        int used = 0;
        for (Item item : items.values()) {
            used += item.at(location);
        }
        return used;
    }

    private void assertRoom(String location, int incoming) {
        Integer limit = capacity.get(location);
        if (limit == null || incoming <= 0) {
            return;
        }
        int used = binUsage(location);
        if (used + incoming > limit) {
            throw new ApiException(409, "Bin " + location + " is full (capacity " + limit
                    + ", current " + used + ", incoming " + incoming + ")");
        }
    }

    private Item find(String sku) {
        Item item = items.get(normalise(sku));
        if (item == null) {
            throw new ApiException(404, "Item not found");
        }
        return item;
    }

    private PurchaseOrder findOrder(long id) {
        for (PurchaseOrder o : orders) {
            if (o.id == id) {
                return o;
            }
        }
        throw new ApiException(404, "Purchase order not found");
    }

    private SalesOrder findSalesOrder(long id) {
        for (SalesOrder o : salesOrders) {
            if (o.id == id) {
                return o;
            }
        }
        throw new ApiException(404, "Sales order not found");
    }

    private CountSession findCount(long id) {
        for (CountSession s : counts) {
            if (s.id == id) {
                return s;
            }
        }
        throw new ApiException(404, "Count not found");
    }

    private void log(String type, String sku, String location, int quantity, String user, String note) {
        events.add(new Event(nextEventId++, Instant.now().toString(), type, sku, location, quantity, user, note));
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String locationKey(String raw) {
        String loc = normalise(raw);
        if (!LOCATION_PATTERN.matcher(loc).matches()) {
            throw new ApiException(400, "Invalid location (use letters, digits and dashes, max 20)");
        }
        return loc;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
