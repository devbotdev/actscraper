package com.one.actscraper;

import java.util.HashSet;
import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;

public final class Inscribe {

    private final Set<String> seenUrls;
    private final Path storagePath;

    public Inscribe() {
        seenUrls = new HashSet<>();
        storagePath = Paths.get("seen_urls.txt");
        try {
            if (Files.exists(storagePath)) {
                seenUrls.addAll(Files.readAllLines(storagePath));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Set<String> getSeenUrls() {
        return seenUrls;
    }

    public void addUrl(String url) {
        if (seenUrls.add(url)) {
            try {
                Files.writeString(storagePath, url + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
