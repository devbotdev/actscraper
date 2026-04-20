package com.one.actscraper.Item;

public class Item {

    private final String url;
    private final String name;
    private final double weight;

    public Item(String url, String name, double weight) {
        this.url = url;
        this.name = name;
        this.weight = weight;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public double getWeight() {
        return weight;
    }
}
