package com.ganesna.wms;

/** One entry in the audit trail. Every stock change and admin action creates one. */
public class Event {
    public final long id;
    public final String time;
    public final String type;
    public final String sku;
    public final String location;
    public final int quantity;
    public final String user;
    public final String note;

    public Event(long id, String time, String type, String sku, String location,
                 int quantity, String user, String note) {
        this.id = id;
        this.time = time;
        this.type = type;
        this.sku = sku;
        this.location = location;
        this.quantity = quantity;
        this.user = user;
        this.note = note;
    }
}
