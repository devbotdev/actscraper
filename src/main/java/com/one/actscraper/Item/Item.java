package com.one.actscraper.Item;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class Item {
    private final String url;
    private final String name;
    private final double weight;

    public Item(String url, String name, double weight) {
        this.url = url;
        this.name = name;
        this.weight = weight;
    }

    public String url() {
        return url;
    }

    public String name() {
        return name;
    }

    public double weight() {
        return weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item item)) return false;
        return Double.compare(item.weight, weight) == 0 &&
                java.util.Objects.equals(url, item.url) &&
                java.util.Objects.equals(name, item.name);
    }

    public static String syntheticId(String prefix, String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                md.update(part.getBytes(StandardCharsets.UTF_8));
            }
            return prefix + HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(url, name, weight);
    }

    @Override
    public String toString() {
        return "Item(" + "url=" + url + ", name=" + name + ", weight=" + weight + ')';
    }
}
