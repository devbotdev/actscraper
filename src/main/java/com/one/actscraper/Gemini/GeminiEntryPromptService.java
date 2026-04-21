package com.one.actscraper.Gemini;

import com.one.actscraper.ActscraperApplication;
import com.one.actscraper.Item.FeedbackTone;
import com.one.actscraper.Mention;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Random;

public final class GeminiEntryPromptService {

    private static final ActscraperApplication APP = ActscraperApplication.getActscraperApplication();
    private static final Random RANDOM = new Random();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static final class EvaluationResult {
        private final boolean relevant;
        private final FeedbackTone feedbackTone;
        private final String appraisal;

        EvaluationResult(boolean relevant, FeedbackTone feedbackTone, String appraisal) {
            this.relevant = relevant;
            this.feedbackTone = feedbackTone == null ? FeedbackTone.UNKNOWN : feedbackTone;
            this.appraisal = appraisal == null ? "" : appraisal;
        }

        static EvaluationResult fallback() {
            return new EvaluationResult(false, FeedbackTone.UNKNOWN, "Unable to evaluate - Gemini request failed");
        }

        public boolean isRelevant() {
            return relevant;
        }

        public FeedbackTone getFeedbackTone() {
            return feedbackTone;
        }

        public String getAppraisal() {
            return appraisal;
        }
    }

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
        EvaluationResult[] evaluations = checkBatchEvaluations(texts);
        boolean[] flags = new boolean[evaluations.length];
        for (int i = 0; i < evaluations.length; i++) {
            flags[i] = evaluations[i].isRelevant();
        }
        return flags;
    }

    public static EvaluationResult[] checkBatchEvaluations(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new EvaluationResult[0];
        }

        int expectedSize = texts.size();
        int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean strict = (attempt > 1);
            String prompt = buildBulkPrompt(texts, strict);

            try {
                String response = APP.getGeminiResponse(prompt);
                EvaluationResult[] parsed = parseEvaluationArray(response, expectedSize);
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

        System.out.println("  [ERROR] Gemini bulk check completely failed after " + maxAttempts + " attempts. Returning fallback evaluation array.");
        EvaluationResult[] fallback = new EvaluationResult[expectedSize];
        for (int i = 0; i < expectedSize; i++) {
            fallback[i] = EvaluationResult.fallback();
        }
        return fallback;
    }

    private static String buildBulkPrompt(List<String> texts, boolean strict) {
        StringBuilder sb = new StringBuilder();
        if (strict) sb.append("CRITICAL: You MUST return EXACTLY a valid JSON array. NO MARKDOWN FENCES. NO EXTRA TEXT.\n");
        else sb.append("Return ONLY a JSON array of objects. No markdown, no explanations.\n");

        sb.append("Each object MUST have fields: relevant (boolean), sentiment (string: POSITIVE, NEGATIVE, or UNKNOWN), appraisal (string: 1-2 sentences explaining WHY people are happy/unhappy).\n");
        sb.append("\nFor each item:\n");
        sb.append("1. Determine if it is relevant to: ").append(ActscraperApplication.getActscraperApplication().getSystemPrompt()).append("\n");
        sb.append("2. If relevant AND full article body is provided: analyze the entire article text to determine if it contains POSITIVE or NEGATIVE sentiment/feedback from people.\n");
        sb.append("   - POSITIVE: article or people express approval, support, satisfaction, praise\n");
        sb.append("   - NEGATIVE: article or people express criticism, disapproval, concerns, complaints\n");
        sb.append("   - UNKNOWN: sentiment cannot be determined or is neutral/mixed\n");
        sb.append("A sentiment appraisal must STRICTLY be based on the tone of the article / opinions provided, NEVER on what YOU perceive as the 'actual' sentiment. If the article contains both positive and negative feedback, choose the dominant tone or UNKNOWN if balanced.\n");
        sb.append("3. If not relevant or no article body: set sentiment to UNKNOWN.\n");
        sb.append("4. For appraisal: provide 1-2 concise sentences explaining WHY people feel this way.\n\n");
        sb.append("Items to evaluate:\n");

        for (int i = 0; i < texts.size(); i++) {
            sb.append(i + 1).append(". ").append(texts.get(i)).append("\n");
        }
        return sb.toString();
    }

    private static EvaluationResult[] parseEvaluationArray(String answer, int expectedSize) {
        if (answer == null || answer.isBlank()) return null;

        String clean = answer.replaceAll("(?s)^```(?:json)?\\s*|```$", "").trim();
        if (!clean.startsWith("[") || !clean.endsWith("]")) return null;

        try {
            JsonNode root = OBJECT_MAPPER.readTree(clean);
            if (!root.isArray()) {
                return null;
            }

            EvaluationResult[] result = new EvaluationResult[expectedSize];
            int limit = Math.min(expectedSize, root.size());

            for (int i = 0; i < limit; i++) {
                JsonNode node = root.get(i);
                if (node == null || !node.isObject()) {
                    return null;
                }

                JsonNode relevantNode = node.get("relevant");
                if (relevantNode == null || !relevantNode.isBoolean()) {
                    return null;
                }

                String sentimentRaw = node.path("sentiment").asText("UNKNOWN");
                String appraisalText = node.path("appraisal").asText("");
                FeedbackTone tone = parseSentiment(sentimentRaw);
                result[i] = new EvaluationResult(relevantNode.asBoolean(), tone, appraisalText);
            }

            for (int i = limit; i < expectedSize; i++) {
                result[i] = EvaluationResult.fallback();
            }

            if (root.size() != expectedSize) {
                System.out.println("  [WARNING] Count mismatch in bulk prompt (expected " + expectedSize + ", got " + root.size() + "). Padding/trimming applied.");
            }

            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static FeedbackTone parseSentiment(String value) {
        if (value == null) {
            return FeedbackTone.UNKNOWN;
        }

        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "POSITIVE" -> FeedbackTone.POSITIVE;
            case "NEGATIVE" -> FeedbackTone.NEGATIVE;
            default -> FeedbackTone.UNKNOWN;
        };
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
