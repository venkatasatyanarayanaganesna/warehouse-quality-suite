package com.ganesna.wms;

public class SalesOrder {
    public final long id;
    public final String customer;
    public final String sku;
    public final int ordered;
    public int shipped;

    public SalesOrder(long id, String customer, String sku, int ordered) {
        this.id = id;
        this.customer = customer;
        this.sku = sku;
        this.ordered = ordered;
    }

    public SalesOrder copy() {
        SalesOrder c = new SalesOrder(id, customer, sku, ordered);
        c.shipped = shipped;
        return c;
    }

    public int outstanding() {
        return ordered - shipped;
    }

    public String status() {
        if (shipped == 0) {
            return "OPEN";
        }
        return shipped < ordered ? "PARTIAL" : "SHIPPED";
    }
}
