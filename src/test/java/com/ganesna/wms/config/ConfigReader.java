package com.ganesna.wms.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Reads config.properties, then layers config-&lt;env&gt;.properties on top when -Denv=name is given.
 * Any value can still be overridden with -Dkey=value, which always wins.
 */
public final class ConfigReader {

    private static final Properties PROPS = new Properties();

    static {
        load("config.properties");
        String env = System.getProperty("env");
        if (env != null && !env.isBlank()) {
            load("config-" + env.trim() + ".properties");
        }
    }

    private ConfigReader() {
    }

    private static void load(String fileName) {
        try (InputStream in = ConfigReader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (in == null) {
                throw new IllegalStateException(fileName + " not found on classpath");
            }
            PROPS.load(in);   // later files overwrite keys from earlier ones
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + fileName, e);
        }
    }

    public static String get(String key) {
        String override = System.getProperty(key);
        return override != null ? override : PROPS.getProperty(key);
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }
}