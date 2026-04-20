package com.one.actscraper.Gemini;

import com.one.actscraper.ActscraperApplication;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class GeminiEntryPromptService {

    private static final long REQUEST_DELAY_MS = 250L;
    private static final ActscraperApplication APP = ActscraperApplication.getActscraperApplication();
    private static final Random RANDOM = new Random();

    public static boolean checkSingle(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return checkBatch(List.of(text))[0];
    }

    public static boolean[] checkEachSequentially(List<String> entries) {
        return checkBatch(entries);
    }

    public static boolean[] checkBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new boolean[0];
        }

        int expectedSize = texts.size();
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean strict = (attempt > 1);
            String prompt = buildBulkPrompt(texts, strict);

            try {
                String response = APP.getGeminiResponse(prompt);
                boolean[] parsed = parseBooleanArray(response, expectedSize);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception e) {
                System.out.println("  [ERROR] Gemini bulk check failed (attempt " + attempt + "): " + e.getMessage());
            }

            if (attempt < maxAttempts) {
                sleepExponential(attempt);
            }
        }

        System.out.println("  [ERROR] Gemini bulk check completely failed after " + maxAttempts + " attempts. Returning false array.");
        return new boolean[expectedSize];
    }

    private static String buildBulkPrompt(List<String> texts, boolean strict) {
        StringBuilder sb = new StringBuilder();
        if (strict) {
            sb.append("CRITICAL: You MUST return EXACTLY a valid JSON array of booleans. NO MARKDOWN FENCES. NO EXTRA TEXT.\n");
        } else {
            sb.append("Return ONLY a JSON array of booleans with true for YES and false for NO. No markdown, no explanations.\n");
        }
        sb.append(ActscraperApplication.getActscraperApplication().getSystemPrompt()).append("\n");

        for (int i = 0; i < texts.size(); i++) {
            sb.append(i + 1).append(". ").append(texts.get(i)).append("\n");
        }
        return sb.toString();
    }

    private static boolean[] parseBooleanArray(String answer, int expectedSize) {
        if (answer == null || answer.isBlank()) return null;

        String clean = answer.replaceAll("(?s)^```(?:json)?\\s*|```$", "").trim();
        if (!clean.startsWith("[") || !clean.endsWith("]")) return null;

        String inner = clean.substring(1, clean.length() - 1).trim();
        boolean[] result = new boolean[expectedSize];
        if (inner.isEmpty()) {
            return result;
        }

        String[] tokens = inner.split(",");
        int validCount = 0;

        for (String t : tokens) {
            String token = t.trim().toLowerCase();
            if (token.equals("true") || token.equals("false")) {
                if (validCount < expectedSize) {
                    result[validCount] = token.equals("true");
                }
                validCount++;
            } else {
                return null;
            }
        }

        if (validCount != expectedSize) {
            System.out.println("  [WARNING] Count mismatch in bulk prompt (expected " + expectedSize + ", got " + validCount + "). Padding/trimming applied.");
        }

        return result;
    }

    private static void sleepExponential(int attempt) {
        try {
            long delay = (long) (Math.pow(2, attempt) * 500) + RANDOM.nextInt(500);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
