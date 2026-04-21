package com.one.actscraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class FileStorage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

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

    /**
     * Save a single analysis result to JSON file
     */
    public static void saveAnalysisResultToJson(AnalysisResult result) {
        try {
            List<AnalysisResult> results = loadAnalysisResultsFromJson();
            results.add(result);
            saveAnalysisResultsToJson(results);
        } catch (Exception e) {
            System.out.println("  [ERROR] saving analysis result to JSON: " + e.getMessage());
        }
    }

    /**
     * Save multiple analysis results to JSON file
     */
    public static void saveAnalysisResultsToJson(List<AnalysisResult> results) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(results);
            Files.writeString(Paths.get("analysis_results.json"), json);
            System.out.println("  [SAVED] " + results.size() + " results to analysis_results.json");
        } catch (IOException e) {
            System.out.println("  [ERROR] saving analysis results to JSON: " + e.getMessage());
        }
    }

    /**
     * Load all analysis results from JSON file
     */
    public static List<AnalysisResult> loadAnalysisResultsFromJson() {
        try {
            java.nio.file.Path path = Paths.get("analysis_results.json");
            if (!Files.exists(path)) {
                return new ArrayList<>();
            }

            String content = Files.readString(path);
            List<AnalysisResult> results = OBJECT_MAPPER.readValue(content,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, AnalysisResult.class));
            return results != null ? results : new ArrayList<>();
        } catch (IOException e) {
            System.out.println("  [ERROR] loading analysis results from JSON: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Load only relevant (flagged) analysis results from JSON file.
     */
    public static List<AnalysisResult> loadRelevantAnalysisResultsFromJson() {
        return loadAnalysisResultsFromJson()
                .stream()
                .filter(AnalysisResult::isFlagged)
                .toList();
    }

    /**
     * Get statistics from analysis results
     */
    public static AnalysisStatistics getStatistics() {
        List<AnalysisResult> results = loadAnalysisResultsFromJson();
        return new AnalysisStatistics(results);
    }

    /**
     * Get statistics from relevant (flagged) analysis results only.
     */
    public static AnalysisStatistics getRelevantStatistics() {
        List<AnalysisResult> relevantResults = loadRelevantAnalysisResultsFromJson();
        return new AnalysisStatistics(relevantResults);
    }

    /**
     * Inner class for statistics
     */
    public static class AnalysisStatistics {
        public int total;
        public int flagged;
        public int positive;
        public int negative;
        public int unknown;
        public java.util.Map<String, Integer> bySource;

        public AnalysisStatistics(List<AnalysisResult> results) {
            this.total = results.size();
            this.flagged = (int) results.stream().filter(AnalysisResult::isFlagged).count();
            this.positive = (int) results.stream().filter(r -> "POSITIVE".equals(r.getFeedbackTone())).count();
            this.negative = (int) results.stream().filter(r -> "NEGATIVE".equals(r.getFeedbackTone())).count();
            this.unknown = (int) results.stream().filter(r -> "UNKNOWN".equals(r.getFeedbackTone())).count();

            this.bySource = new java.util.HashMap<>();
            results.stream().forEach(r -> {
                String source = r.getSourceName();
                this.bySource.put(source, this.bySource.getOrDefault(source, 0) + 1);
            });
        }
    }
}


