package com.one.actscraper;

import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.io.IOException;

/**
 * Manages URLs that were fetched but are outside the current date range.
 * When dates change, this file is cleared to allow those URLs to be re-evaluated
 * against the new date range.
 */
public final class OutOfDateUrls {

    private final Set<String> outOfDateUrls;
    private final Path storagePath;

    public OutOfDateUrls() {
        outOfDateUrls = new HashSet<>();
        storagePath = Paths.get("out-of-date-urls.txt");
        loadFromFile();
    }

    private void loadFromFile() {
        try {
            if (Files.exists(storagePath)) {
                outOfDateUrls.addAll(Files.readAllLines(storagePath));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Set<String> getOutOfDateUrls() {
        return outOfDateUrls;
    }

    /**
     * Add a URL to the out-of-date list
     */
    public void addUrl(String url) {
        if (outOfDateUrls.add(url)) {
            try {
                Files.writeString(storagePath, url + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Check if a URL is in the out-of-date list
     */
    public boolean contains(String url) {
        return outOfDateUrls.contains(url);
    }

    /**
     * Clear all out-of-date URLs (called when date range changes)
     */
    public void clear() {
        try {
            if (Files.exists(storagePath)) {
                Files.delete(storagePath);
            }
            outOfDateUrls.clear();
            System.out.println("  [CLEARED] Out-of-date URLs file cleared due to date range change");
        } catch (IOException e) {
            System.out.println("  [ERROR] Failed to clear out-of-date URLs: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

