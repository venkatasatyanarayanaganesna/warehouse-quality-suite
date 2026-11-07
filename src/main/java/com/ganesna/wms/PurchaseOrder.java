package com.ganesna.wms;

public class PurchaseOrder {
    public final long id;
    public final String supplier;
    public final String sku;
    public final int ordered;
    public int received;

    public PurchaseOrder(long id, String supplier, String sku, int ordered) {
        this.id = id;
        this.supplier = supplier;
        this.sku = sku;
        this.ordered = ordered;
    }

    public PurchaseOrder copy() {
        PurchaseOrder c = new PurchaseOrder(id, supplier, sku, ordered);
        c.received = received;
        return c;
    }

    public int outstanding() {
        return ordered - received;
    }

    public String status() {
        if (received == 0) {
            return "OPEN";
        }
        return received < ordered ? "PARTIAL" : "RECEIVED";
    }
}
