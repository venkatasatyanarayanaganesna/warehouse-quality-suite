package com.ganesna.wms;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Item {
    public static final int DEFAULT_REORDER_LEVEL = 10;
    public static final String UNASSIGNED = "UNASSIGNED";
    public static final String NO_BATCH = "-";

    /** A batch of stock in one bin, with an optional expiry date. */
    public static final class Lot {
        public final String sku;
        public final String batch;
        public final String location;
        public final LocalDate expiry;
        public int quantity;

        public Lot(String sku, String batch, String location, LocalDate expiry, int quantity) {
            this.sku = sku;
            this.batch = batch;
            this.location = location;
            this.expiry = expiry;
            this.quantity = quantity;
        }

        public Lot copy() {
            return new Lot(sku, batch, location, expiry, quantity);
        }

        public boolean isExpired(LocalDate today) {
            return expiry != null && expiry.isBefore(today);
        }
    }

    public final String sku;
    public String name;
    public int reorderLevel;
    /** location -> quantity. Insertion order is the bin order. Always equals the sum of its lots. */
    public final Map<String, Integer> stock = new LinkedHashMap<>();
    public final List<Lot> lots = new ArrayList<>();

    public Item(String sku, String name, int reorderLevel) {
        this.sku = sku;
        this.name = name;
        this.reorderLevel = reorderLevel;
    }

    public Item copy() {
        Item c = new Item(sku, name, reorderLevel);
        c.stock.putAll(stock);
        for (Lot l : lots) {
            c.lots.add(l.copy());
        }
        return c;
    }

    public int total() {
        int sum = 0;
        for (int q : stock.values()) {
            sum += q;
        }
        return sum;
    }

    public int at(String location) {
        return stock.getOrDefault(location, 0);
    }

    public int expiredAt(String location) {
        LocalDate today = LocalDate.now();
        int n = 0;
        for (Lot l : lots) {
            if (l.location.equals(location) && l.isExpired(today)) {
                n += l.quantity;
            }
        }
        return n;
    }

    public int usableAt(String location) {
        return at(location) - expiredAt(location);
    }

    public int usableTotal() {
        int n = 0;
        for (String loc : stock.keySet()) {
            n += usableAt(loc);
        }
        return n;
    }

    public void add(String location, int quantity) {
        add(location, quantity, NO_BATCH, null);
    }

    public void add(String location, int quantity, String batch, LocalDate expiry) {
    stock.merge(location, quantity, Integer::sum);
    String b = (batch == null || batch.trim().isEmpty()) ? NO_BATCH : batch.trim();
    for (Lot l : lots) {
        if (l.location.equals(location) && l.batch.equals(b)
                && java.util.Objects.equals(l.expiry, expiry)) {
            l.quantity += quantity;
            return;
        }
    }
    lots.add(new Lot(sku, b, location, expiry, quantity));
    }

    /** Lots in one bin, earliest expiry first (no expiry last). Expired lots are left out unless asked. */
    private List<Lot> fefoAt(String location, boolean includeExpired) {
        LocalDate today = LocalDate.now();
        List<Lot> result = new ArrayList<>();
        for (Lot l : lots) {
            if (l.location.equals(location) && (includeExpired || !l.isExpired(today))) {
                result.add(l);
            }
        }
        result.sort(Comparator.comparing((Lot l) -> l.expiry == null ? LocalDate.MAX : l.expiry));
        return result;
    }

    /** Removes stock from one bin, earliest expiry first. Callers check availability first. */
    public void take(String location, int quantity, boolean includeExpired) {
        List<Lot> order = fefoAt(location, includeExpired);
        int available = 0;
        for (Lot l : order) {
            available += l.quantity;
        }
        if (available < quantity) {
            throw new IllegalStateException("Not enough stock at " + location);
        }
        int remaining = quantity;
        for (Lot l : order) {
            if (remaining == 0) {
                break;
            }
            int t = Math.min(l.quantity, remaining);
            l.quantity -= t;
            remaining -= t;
        }
        lots.removeIf(l -> l.quantity == 0);
        int left = at(location) - quantity;
        if (left <= 0) {
            stock.remove(location);
        } else {
            stock.put(location, left);
        }
    }

    /**
     * Removes usable stock across all bins, earliest expiry first.
     * Returns what was taken, one entry per batch and bin.
     */
    public List<Lot> takeFefo(int quantity) {
        LocalDate today = LocalDate.now();
        List<Lot> order = new ArrayList<>();
        for (Lot l : lots) {
            if (!l.isExpired(today)) {
                order.add(l);
            }
        }
        order.sort(Comparator.comparing((Lot l) -> l.expiry == null ? LocalDate.MAX : l.expiry));
        int available = 0;
        for (Lot l : order) {
            available += l.quantity;
        }
        if (available < quantity) {
            throw new IllegalStateException("Not enough usable stock");
        }
        List<Lot> taken = new ArrayList<>();
        int remaining = quantity;
        for (Lot l : order) {
            if (remaining == 0) {
                break;
            }
            int t = Math.min(l.quantity, remaining);
            taken.add(new Lot(sku, l.batch, l.location, l.expiry, t));
            l.quantity -= t;
            remaining -= t;
            int left = at(l.location) - t;
            if (left <= 0) {
                stock.remove(l.location);
            } else {
                stock.put(l.location, left);
            }
        }
        lots.removeIf(l -> l.quantity == 0);
        return taken;
    }

    /** Moves usable stock to another bin, keeping each batch and its expiry. */
    public void transfer(String from, String to, int quantity) {
        List<Lot> order = fefoAt(from, false);
        int available = 0;
        for (Lot l : order) {
            available += l.quantity;
        }
        if (available < quantity) {
            throw new IllegalStateException("Not enough stock at " + from);
        }
        int remaining = quantity;
        for (Lot l : order) {
            if (remaining == 0) {
                break;
            }
            int t = Math.min(l.quantity, remaining);
            l.quantity -= t;
            remaining -= t;
            add(to, t, l.batch, l.expiry);
        }
        lots.removeIf(l -> l.quantity == 0);
        int left = at(from) - quantity;
        if (left <= 0) {
            stock.remove(from);
        } else {
            stock.put(from, left);
        }
    }

    /** Sets the counted quantity for one bin. Increases add an ADJUST lot; decreases take earliest stock. */
    public void set(String location, int quantity) {
        int before = at(location);
        if (quantity > before) {
            add(location, quantity - before, "ADJUST", null);
        } else if (quantity < before) {
            take(location, before - quantity, true);
        }
    }

    public String primaryLocation() {
        return stock.isEmpty() ? UNASSIGNED : stock.keySet().iterator().next();
    }

    public boolean isLowStock() {
        return total() < reorderLevel;
    }
}
