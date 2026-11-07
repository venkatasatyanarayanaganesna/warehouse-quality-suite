package com.ganesna.wms;

/** A managed supplier or customer. Orders must name one of these. */
public class Party {
    public final long id;
    public final String name;

    public Party(long id, String name) {
        this.id = id;
        this.name = name;
    }
}
