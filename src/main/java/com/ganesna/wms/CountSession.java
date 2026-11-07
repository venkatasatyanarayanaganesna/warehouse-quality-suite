package com.ganesna.wms;

import java.util.ArrayList;
import java.util.List;

/** A cycle count: snapshot the chosen bins, record what is counted, then an admin approves. */
public class CountSession {
    public final long id;
    public final List<String> bins;
    public String status = "OPEN";
    public final List<CountLine> lines = new ArrayList<>();

    public CountSession(long id, List<String> bins) {
        this.id = id;
        this.bins = new ArrayList<>(bins);
    }

    public CountSession copy() {
        CountSession c = new CountSession(id, bins);
        c.status = status;
        for (CountLine l : lines) {
            c.lines.add(l.copy());
        }
        return c;
    }
}
