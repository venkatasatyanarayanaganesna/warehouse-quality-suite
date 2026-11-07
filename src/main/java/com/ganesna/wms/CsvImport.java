package com.ganesna.wms;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses an item upload. Every row is checked, and all errors are reported with line numbers.
 * Fields cannot contain commas. The file must start with the header line.
 */
public final class CsvImport {

    public static final String HEADER = "sku,name,quantity,location,batch,expiry";
    private static final Pattern SKU = Pattern.compile("^[A-Z0-9-]{1,30}$");
    private static final Pattern LOCATION = Pattern.compile("^[A-Z0-9-]{1,20}$");

    public static final class Row {
        public final int line;
        public final String sku;
        public final String name;
        public final int quantity;
        public final String location;
        public final String batch;
        public final LocalDate expiry;

        Row(int line, String sku, String name, int quantity, String location, String batch, LocalDate expiry) {
            this.line = line;
            this.sku = sku;
            this.name = name;
            this.quantity = quantity;
            this.location = location;
            this.batch = batch;
            this.expiry = expiry;
        }
    }

    public static final class RowError {
        public final int line;
        public final String message;

        RowError(int line, String message) {
            this.line = line;
            this.message = message;
        }
    }

    public static final class Result {
        public final List<Row> rows = new ArrayList<>();
        public final List<RowError> errors = new ArrayList<>();
    }

    private CsvImport() {
    }

    public static Result parse(String text) {
        Result result = new Result();
        String[] lines = text.split("\r?\n", -1);
        if (!lines[0].trim().toLowerCase(Locale.ROOT).equals(HEADER)) {
            result.errors.add(new RowError(1, "Header must be: " + HEADER));
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 1; i < lines.length; i++) {
            int lineNo = i + 1;
            String raw = lines[i].trim();
            if (raw.isEmpty()) {
                continue;
            }
            String[] c = raw.split(",", -1);
            if (c.length != 6) {
                result.errors.add(new RowError(lineNo, "Expected 6 columns, found " + c.length));
                continue;
            }
            String sku = c[0].trim().toUpperCase(Locale.ROOT);
            String name = c[1].trim();
            String qtyRaw = c[2].trim();
            String location = c[3].trim().toUpperCase(Locale.ROOT);
            String batch = c[4].trim();
            String expRaw = c[5].trim();
            boolean ok = true;

            if (!SKU.matcher(sku).matches()) {
                result.errors.add(new RowError(lineNo, "Invalid SKU (use letters, digits and dashes, max 30)"));
                ok = false;
            }
            if (name.isEmpty()) {
                result.errors.add(new RowError(lineNo, "Name is required"));
                ok = false;
            } else if (name.length() > 60) {
                result.errors.add(new RowError(lineNo, "Name is too long (max 60 characters)"));
                ok = false;
            }
            int quantity = 0;
            try {
                quantity = Integer.parseInt(qtyRaw);
                if (quantity < 0) {
                    result.errors.add(new RowError(lineNo, "Quantity cannot be negative"));
                    ok = false;
                }
            } catch (NumberFormatException e) {
                result.errors.add(new RowError(lineNo, "Quantity must be a whole number"));
                ok = false;
            }
            if (!location.isEmpty() && !LOCATION.matcher(location).matches()) {
                result.errors.add(new RowError(lineNo, "Invalid location (use letters, digits and dashes, max 20)"));
                ok = false;
            }
            LocalDate expiry = null;
            if (!expRaw.isEmpty()) {
                try {
                    expiry = LocalDate.parse(expRaw);
                } catch (DateTimeParseException e) {
                    result.errors.add(new RowError(lineNo, "Expiry must be a date (YYYY-MM-DD)"));
                    ok = false;
                }
            }
            if (ok && !seen.add(sku)) {
                result.errors.add(new RowError(lineNo, "Duplicate SKU " + sku + " in this file"));
                ok = false;
            }
            if (ok) {
                result.rows.add(new Row(lineNo, sku, name, quantity,
                        location.isEmpty() ? null : location,
                        batch.isEmpty() ? null : batch, expiry));
            }
        }
        return result;
    }
}
