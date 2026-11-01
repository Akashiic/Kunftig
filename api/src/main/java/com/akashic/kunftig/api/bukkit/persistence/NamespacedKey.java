package com.akashic.kunftig.api.bukkit.persistence;

import java.util.Locale;
import java.util.regex.Pattern;

import org.bukkit.plugin.Plugin;

/**
 * Bukkit 1.12+'s {@code NamespacedKey} for 1.7.10: a {@code namespace:key} identifier with the same rules, used to
 * name persistent data so plugins never collide.
 */
public final class NamespacedKey {

    private static final Pattern VALID_NAMESPACE = Pattern.compile("[a-z0-9._-]+");
    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9/._-]+");

    private final String namespace;
    private final String key;

    /** A key in the plugin's namespace (its name, lower-cased). */
    public NamespacedKey(Plugin plugin, String key) {
        this(plugin.getName().toLowerCase(Locale.ROOT), key);
    }

    /** A key in an explicit namespace. Prefer {@link #NamespacedKey(Plugin, String)}. */
    public NamespacedKey(String namespace, String key) {
        if (namespace == null || !VALID_NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid namespace '" + namespace + "': must match [a-z0-9._-]+");
        }
        if (key == null || !VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid key '" + key + "': must match [a-z0-9/._-]+");
        }
        if (namespace.length() + 1 + key.length() > 256) {
            throw new IllegalArgumentException("NamespacedKey longer than 256 characters: " + namespace + ":" + key);
        }
        this.namespace = namespace;
        this.key = key;
    }

    /** Parses {@code namespace:key}; returns {@code null} when invalid (like Bukkit's {@code fromString}). */
    public static NamespacedKey fromString(String value) {
        if (value == null) {
            return null;
        }
        int colon = value.indexOf(':');
        if (colon <= 0 || colon != value.lastIndexOf(':') || colon == value.length() - 1) {
            return null;
        }
        try {
            return new NamespacedKey(value.substring(0, colon), value.substring(colon + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String getNamespace() {
        return namespace;
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof NamespacedKey)) {
            return false;
        }
        NamespacedKey that = (NamespacedKey) other;
        return namespace.equals(that.namespace) && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return 31 * namespace.hashCode() + key.hashCode();
    }

    @Override
    public String toString() {
        return namespace + ":" + key;
    }
}
