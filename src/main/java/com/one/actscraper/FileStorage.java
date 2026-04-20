package com.one.actscraper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class FileStorage {

    public static String readApiKeyFromFile(String api, String filename) {
        try {
            java.nio.file.Path path = Paths.get(filename);
            if (!Files.exists(path)) {
                Files.writeString(path, "YOUR_" + api.toUpperCase() + "API_KEY");
                return "YOUR_" + api.toUpperCase() + "API_KEY";
            }

            String content = Files.readString(path).trim();
            if (content.startsWith("YOUR_" + api.toUpperCase() + "API_KEY:")) {
                content = content.replace("YOUR_" + api.toUpperCase() + "API_KEY: ", "").trim();
            }
            return content;
        } catch (IOException e) {
            System.out.println("  [ERROR] reading API key from " + filename + ": " + e.getMessage());
            return null;
        }
    }

    public static void saveMentionToFile(Mention mention) {
        try {
            String record = mention.toString() + " | URL: " + mention.getUrl() + System.lineSeparator();
            Files.writeString(Paths.get("saved_mentions.txt"), record, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
