package com.one.actscraper.Gemini;

import com.one.actscraper.ActscraperApplication;
import com.one.actscraper.Error.GeminiFailure;
import com.one.actscraper.Item.FeedbackTone;
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
        GeminiFailure lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean strict = (attempt > 1);
            String prompt = buildBulkPrompt(texts, strict);

            try {
                String response = APP.getGeminiResponse(prompt);
                if (response == null || response.isBlank()) {
                    throw new GeminiFailure("Gemini returned an empty response.");
                }
                return parseEvaluationArray(response, expectedSize);
            } catch (GeminiFailure e) {
                lastFailure = e;
                System.out.println("  [ERROR] Gemini bulk check failed (attempt " + attempt + "): " + e.getMessage());
            } catch (Exception e) {
                lastFailure = new GeminiFailure("Gemini request failed.", e);
                System.out.println("  [ERROR] Gemini bulk check failed (attempt " + attempt + "): " + e.getMessage());
            }

            if (attempt < maxAttempts) {
                sleepExponential(attempt);
            }
        }

        String message = "Gemini bulk check failed after " + maxAttempts + " attempts.";
        if (lastFailure != null && lastFailure.getMessage() != null && !lastFailure.getMessage().isBlank()) {
            message += " Last error: " + lastFailure.getMessage();
        }
        throw new GeminiFailure(message, lastFailure);
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
        sb.append("3. If not relevant or no article body: set sentiment to UNKNOWN. Explain in a few words why that decision was made.\n");
        sb.append("4. For appraisal: provide 1-2 concise sentences explaining WHY people feel this way.\n\n");
        sb.append("Items to evaluate:\n");

        for (int i = 0; i < texts.size(); i++) {
            sb.append(i + 1).append(". ").append(texts.get(i)).append("\n");
        }
        return sb.toString();
    }

    private static EvaluationResult[] parseEvaluationArray(String answer, int expectedSize) {
        if (answer == null || answer.isBlank()) {
            throw new GeminiFailure("Gemini returned empty JSON payload.");
        }

        String clean = answer.replaceAll("(?s)^```(?:json)?\\s*|```$", "").trim();
        if (!clean.startsWith("[") || !clean.endsWith("]")) {
            throw new GeminiFailure("Gemini response is not a JSON array.");
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(clean);
            if (!root.isArray()) {
                throw new GeminiFailure("Gemini response root is not an array.");
            }

            if (root.size() != expectedSize) {
                throw new GeminiFailure("Gemini returned " + root.size() + " results but " + expectedSize + " were requested.");
            }

            EvaluationResult[] result = new EvaluationResult[expectedSize];

            for (int i = 0; i < expectedSize; i++) {
                JsonNode node = root.get(i);
                if (node == null || !node.isObject()) {
                    throw new GeminiFailure("Gemini response entry " + i + " is invalid.");
                }

                JsonNode relevantNode = node.get("relevant");
                if (relevantNode == null || !relevantNode.isBoolean()) {
                    throw new GeminiFailure("Gemini response entry " + i + " is missing boolean 'relevant'.");
                }

                String sentimentRaw = node.path("sentiment").asText("UNKNOWN");
                String appraisalText = node.path("appraisal").asText("");
                FeedbackTone tone = parseSentiment(sentimentRaw);
                result[i] = new EvaluationResult(relevantNode.asBoolean(), tone, appraisalText);
            }

            return result;
        } catch (Exception e) {
            if (e instanceof GeminiFailure geminiFailure) {
                throw geminiFailure;
            }
            throw new GeminiFailure("Failed to parse Gemini response JSON.", e);
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
