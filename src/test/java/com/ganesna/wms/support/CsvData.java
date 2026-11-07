package com.ganesna.wms.support;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads rows from a CSV in src/test/resources for TestNG data providers. */
public final class CsvData {

    private CsvData() {
    }

    /** Skips blank lines and lines starting with #. Every cell is returned as a trimmed String. */
    public static Object[][] rows(String resource) {
        InputStream in = CsvData.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalArgumentException("Test data file not found on classpath: " + resource);
        }
        List<Object[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split(",", -1);
                Object[] row = new Object[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    row[i] = parts[i].trim();
                }
                rows.add(row);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + resource, e);
        }
        return rows.toArray(new Object[0][]);
    }
}