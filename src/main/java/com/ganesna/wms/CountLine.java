package com.ganesna.wms;

/** One item in one bin: what the system expected and what was physically counted. */
public class CountLine {
    public final String sku;
    public final String location;
    public final int expected;
    public Integer counted;

    public CountLine(String sku, String location, int expected) {
        this.sku = sku;
        this.location = location;
        this.expected = expected;
    }

    public CountLine copy() {
        CountLine c = new CountLine(sku, location, expected);
        c.counted = counted;
        return c;
    }

    public Integer variance() {
        return counted == null ? null : counted - expected;
    }
}
