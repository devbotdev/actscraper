package com.one.actscraper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.one.actscraper.Error.GeminiFailure;
import com.one.actscraper.Gemini.GeminiEntryPromptService;
import com.one.actscraper.Item.FeedbackTone;
import com.one.actscraper.Browser.ArticleExtractor;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Scrapers {

    // From rapid API, there's plenty more
    private static final String igAPIString = "instagram-scraper-20251.p.rapidapi.com";
    private static final String fbAPIString = "facebook-scraper-api4.p.rapidapi.com";

    private static final String subReddit = "r/albania";
    private static final String instagramUsername = "one.albania";
    private static final String facebookID = "100064865822999"; // Facebook won't work with usernames

    private static final short redditComments = 10;

    private static final int MAX_CHARS = 15_000;

    public class PendingItem {
        Mention mention;
        String textToPrompt;
        String urlToInscribe;
        boolean webpageItem;
        String fullArticleContent;
        boolean assumeRelevance;

        public PendingItem(Mention mention, String textToPrompt, String urlToInscribe, boolean webpageItem, boolean assumeRelevance) {
            this.mention = mention;
            this.textToPrompt = textToPrompt;
            this.urlToInscribe = urlToInscribe;
            this.webpageItem = webpageItem;
            this.fullArticleContent = null;
            this.assumeRelevance = assumeRelevance;
        }

        String buildPromptText() {
            String basePrompt = textToPrompt;
            if (urlToInscribe != null && !textToPrompt.contains(urlToInscribe)) {
                basePrompt += "\nURL: " + urlToInscribe;
            }

            if (!webpageItem) {
                return basePrompt;
            }

            if (ActscraperApplication.getActscraperApplication().isFulltextEnabled()) {
                if (fullArticleContent != null && !fullArticleContent.isBlank()) {
                    return basePrompt
                            + "\n\n=== FULL ARTICLE BODY ===\n"
                            + fullArticleContent
                            + "\n=== END ARTICLE ===";
                } else {
                    // Fallback: provide title, description, and URL as the body so Gemini evaluates sentiment
                    return "=== FULL ARTICLE BODY & ARTICLE URL ===\n"
                            + basePrompt
                            + "\n=== END ARTICLE ===";
                }
            } else {
                // Seems to be more efficient
                return "=== FULL ARTICLE BODY & ARTICLE URL ===\n"
                        + basePrompt
                        + "\n=== END ARTICLE ===";
            }
        }
    }

    private final List<PendingItem> pendingItems = new ArrayList<>();

    public void processPendingItems(Inscribe inscribe) {
        if (pendingItems.isEmpty()) {
            System.out.println("No new articles to process through Gemini.");
            return;
        }

        System.out.println("Processing " + pendingItems.size() + " accumulated items through Gemini in bulk...");

        boolean fullTextEnabled = ActscraperApplication.getActscraperApplication().isFulltextEnabled();

        // Step 1: Check relevance first!
        List<String> textToPrompts = new ArrayList<>();
        List<Integer> indexToPrompts = new ArrayList<>();
        boolean[] relevanceFlags = new boolean[pendingItems.size()];

        if (fullTextEnabled) {
            for (int i = 0; i < pendingItems.size(); i++) {
                if (pendingItems.get(i).assumeRelevance) {
                    relevanceFlags[i] = true;
                } else {
                    textToPrompts.add(pendingItems.get(i).textToPrompt);
                    indexToPrompts.add(i);
                }
            }

            if (!textToPrompts.isEmpty()) {
                try {
                    boolean[] geminiResults = GeminiEntryPromptService.checkBatchRelevance(textToPrompts);
                    for (int i = 0; i < geminiResults.length; i++) {
                        relevanceFlags[indexToPrompts.get(i)] = geminiResults[i];
                    }
                } catch (GeminiFailure e) {
                    System.out.println("  [ERROR] Gemini failed to establish relevance. Skipping persistence for this run: " + e.getMessage());
                    for (PendingItem item : pendingItems) {
                        String retryKey = item.urlToInscribe != null ? item.urlToInscribe : item.mention.getUrl();
                        System.out.println("  [RETRY] Not persisted due to Gemini failure: " + retryKey);
                    }
                    pendingItems.clear();
                    return;
                }
            }

            // Fetch full article content for webpage items that are relevant before processing sentiment
            boolean needsDelay = false;
            System.out.println("Fetching full article content for relevant webpage sources...");
            for (int i = 0; i < pendingItems.size(); i++) {
                PendingItem item = pendingItems.get(i);
                item.mention.setFlagged(relevanceFlags[i]);

                if (relevanceFlags[i] && item.webpageItem && item.urlToInscribe != null) {
                    if (needsDelay) {
                        try {
                            Thread.sleep(500 + ThreadLocalRandom.current().nextInt(1000));
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    String content = fetchArticleContent(item.urlToInscribe);
                    if (content != null && !content.isBlank()) {
                        item.fullArticleContent = content;
                        System.out.println("  [FETCHED] " + item.urlToInscribe + " (" + content.length() + " chars)");
                    }
                    if (ActscraperApplication.getRateLimiting()) needsDelay = true;
                }
            }
        } else {
            // Assume relevance is true for now so they get passed to the evaluation stage, which will determine relevance
            for (int i = 0; i < pendingItems.size(); i++) {
                relevanceFlags[i] = true;
                pendingItems.get(i).mention.setFlagged(true);
            }
        }

        List<String> texts = new ArrayList<>();
        List<PendingItem> relevantItems = new ArrayList<>();
        for (PendingItem item : pendingItems) {
            if (item.mention.isFlagged()) { // Process sentiment only for relevant items
                texts.add(item.buildPromptText());
                relevantItems.add(item);
            }
        }

        GeminiEntryPromptService.EvaluationResult[] evaluations = new GeminiEntryPromptService.EvaluationResult[0];
        if (!texts.isEmpty()) {
            try {
                evaluations = GeminiEntryPromptService.checkBatchEvaluations(texts);
            } catch (GeminiFailure e) {
                System.out.println("  [ERROR] Gemini failed to evaluate batch sentiment. Skipping persistence for this run: " + e.getMessage());
                for (PendingItem item : pendingItems) {
                    String retryKey = item.urlToInscribe != null ? item.urlToInscribe : item.mention.getUrl();
                    System.out.println("  [RETRY] Not persisted due to Gemini failure: " + retryKey);
                }
                // Clear failed in-memory batch to avoid duplicate accumulation; they will be re-fetched next run.
                pendingItems.clear();
                return;
            }
        }

        System.out.println("\n=== Results & Saving ===");

        // Load existing results from JSON to avoid overwriting
        List<AnalysisResult> analysisResults = new ArrayList<>(FileStorage.loadAnalysisResultsFromJson());

        int evalIndex = 0;
        for (PendingItem item : pendingItems) {
            if (item.urlToInscribe != null && inscribe != null) {
                inscribe.addUrl(item.urlToInscribe);
            }

            if (item.mention.isFlagged()) {
                boolean isEvalRelevant = evaluations[evalIndex].isRelevant();
                item.mention.setFeedbackTone(evaluations[evalIndex].getFeedbackTone());
                item.mention.setAppraisal(evaluations[evalIndex].getAppraisal());

                if (isEvalRelevant) {
                    System.out.println(item.mention);
                    FileStorage.saveMentionToFile(item.mention);

                    // Create AnalysisResult and save to JSON
                    AnalysisResult result = new AnalysisResult(item.mention, item.mention.getAppraisal());
                    analysisResults.add(result);
                } else {
                    item.mention.setFlagged(false);
                    item.mention.setFeedbackTone(FeedbackTone.UNKNOWN);
                    System.out.println("  [IGNORED] Full article false positive: " + (item.urlToInscribe != null ? item.urlToInscribe : item.textToPrompt));
                    FileStorage.saveMentionToFile(item.mention);
                }

                evalIndex++;
            } else {
                item.mention.setFeedbackTone(FeedbackTone.UNKNOWN);
                System.out.println("  [IGNORED] Irrelevant item: " + item.textToPrompt);
                FileStorage.saveMentionToFile(item.mention);
            }
        }

        // Save all results to JSON (existing + new)
        if (!analysisResults.isEmpty()) {
            FileStorage.saveAnalysisResultsToJson(analysisResults);
        }

        pendingItems.clear();
    }

    public void fetchRedditComments(Inscribe inscribe) {
        System.out.println("Fetching Reddit comments...");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.reddit.com/" + subReddit + "/comments.json?limit=" + redditComments))
                    .header("User-Agent", "ActScraper/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String json = response.body();

                JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                JsonArray children = root.getAsJsonObject("data").getAsJsonArray("children");
                for (JsonElement child : children) {
                    JsonObject data = child.getAsJsonObject().getAsJsonObject("data");
                    String part = data.has("body") ? data.get("body").getAsString() : null;
                    long createdUtc = data.has("created_utc") ? data.get("created_utc").getAsLong() : 0;
                    Date publishedDate = createdUtc > 0 ? new Date(createdUtc * 1000L) : new Date();

                    if (part != null && part.length() > 5) {
                        String fUrl = syntheticId("reddit-", part);
                        if (inscribe.getSeenUrls().contains(fUrl)) continue;

                        Mention m = new Mention(
                                "Reddit Comment: " + (part.length() > 30 ? part.substring(0, 30) + "..." : part),
                                fUrl, publishedDate, "Reddit", 0.60);
                        if (m.getPublishedDate().after(ActscraperApplication.getStartDate()) && m.getPublishedDate().before(ActscraperApplication.getEndDate()))
                            pendingItems.add(new PendingItem(m, part, fUrl, false, false));
                        else
                            ActscraperApplication.getActscraperApplication().getOutOfDateUrls().addUrl(fUrl);
                    }
                }
            } else {
                System.out.println("Reddit API returned " + response.statusCode());
            }

        } catch (Exception e) {
            System.out.println("  [ERROR] Reddit: " + e.getMessage());
        }
    }

    public void fetchSocialMediaComments() {
        System.out.println("Fetching Facebook and Instagram comments via RapidAPI...");
        try {
            String rapidApiKey = FileStorage.readApiKeyFromFile("rapid", "rapidapi_key.txt");
            if (rapidApiKey == null || rapidApiKey.isEmpty() || rapidApiKey.equals("YOUR_RAPIDAPI_KEY")) {
                System.out.println("  [WARNING] RapidAPI key not set in rapidapi_key.txt. Skipping RapidAPI fetch.");
                return;
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            // FACEBOOK
            HttpRequest fbPostsRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + fbAPIString + "/get_facebook_page_posts_details_from_id?profile_id=" + facebookID + "&timezone=UTC"))
                    .header("x-rapidapi-key", rapidApiKey)
                    .header("x-rapidapi-host", fbAPIString)
                    .GET().build();

            HttpResponse<String> fbPostsResponse = client.send(fbPostsRequest, HttpResponse.BodyHandlers.ofString());
            if (fbPostsResponse.statusCode() == 200) {
                List<String> postLinks = extractJsonValues(fbPostsResponse.body(), "post_link");
                for (String link : postLinks) {
                    String encoded = java.net.URLEncoder.encode(link, StandardCharsets.UTF_8);
                    HttpRequest commentsReq = HttpRequest.newBuilder()
                            .uri(URI.create("https://" + fbAPIString + "/get_facebook_post_comments_details?link=" + encoded))
                            .header("x-rapidapi-key", rapidApiKey)
                            .header("x-rapidapi-host", fbAPIString)
                            .GET().build();

                    HttpResponse<String> commentsResp = client.send(commentsReq, HttpResponse.BodyHandlers.ofString());
                    if (commentsResp.statusCode() == 200) {
                        List<String> comments = extractJsonValues(commentsResp.body(), "comment_text");
                        for (String comment : comments) {
                            String url = syntheticId("fb-", link, comment);
                            Mention m = new Mention(
                                    "FB Comment: " + (comment.length() > 30 ? comment.substring(0, 30) + "..." : comment),
                                    url, new Date(), "Facebook", 0.70);
                            if (m.getPublishedDate().after(ActscraperApplication.getStartDate()) && m.getPublishedDate().before(ActscraperApplication.getEndDate()))
                                pendingItems.add(new PendingItem(m, comment, null, false, false));
                            else
                                ActscraperApplication.getActscraperApplication().getOutOfDateUrls().addUrl(url);
                        }
                    } else {
                        System.out.println("  [ERROR] Facebook comments fetch returned " + commentsResp.statusCode() + ": " + commentsResp.body());
                    }
                }
            } else {
                System.out.println("  [ERROR] Facebook posts fetch returned " + fbPostsResponse.statusCode() + ": " + fbPostsResponse.body());
            }

            // INSTAGRAM
            HttpRequest igPostsRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + igAPIString + "/userposts/?username_or_id=" + instagramUsername))
                    .header("x-rapidapi-key", rapidApiKey)
                    .header("x-rapidapi-host", igAPIString)
                    .GET().build();

            HttpResponse<String> igPostsResponse = client.send(igPostsRequest, HttpResponse.BodyHandlers.ofString());
            if (igPostsResponse.statusCode() == 200) {
                List<String> postCodes = extractJsonValues(igPostsResponse.body(), "code");
                for (String code : postCodes) {
                    HttpRequest commentsReq = HttpRequest.newBuilder()
                            .uri(URI.create("https://" + igAPIString + "/postcomments/?code_or_url=" + code))
                            .header("x-rapidapi-key", rapidApiKey)
                            .header("x-rapidapi-host", igAPIString)
                            .GET().build();

                    HttpResponse<String> commentsResp = client.send(commentsReq, HttpResponse.BodyHandlers.ofString());
                    if (commentsResp.statusCode() == 200) {
                        List<String> comments = extractJsonValues(commentsResp.body(), "text");
                        for (String comment : comments) {
                            String url = syntheticId("ig-", code, comment);
                            Mention m = new Mention(
                                    "IG Comment: " + (comment.length() > 30 ? comment.substring(0, 30) + "..." : comment),
                                    url, new Date(), "Instagram", 0.70);
                            if (m.getPublishedDate().after(ActscraperApplication.getStartDate()) && m.getPublishedDate().before(ActscraperApplication.getEndDate()))
                                pendingItems.add(new PendingItem(m, comment, null, false, false));
                            else
                                ActscraperApplication.getActscraperApplication().getOutOfDateUrls().addUrl(url);
                        }
                    } else {
                        System.out.println("  [ERROR] Instagram comments fetch returned " + commentsResp.statusCode() + ": " + commentsResp.body());
                    }
                }
            } else {
                System.out.println("  [ERROR] Instagram posts fetch returned " + igPostsResponse.statusCode() + ": " + igPostsResponse.body());
            }
        } catch (Exception e) {
            System.out.println("  [ERROR] RapidAPI: " + e.getMessage());
        }
    }

    private static List<String> extractJsonValues(String json, String key) {
        List<String> results = new ArrayList<>();
        collectValues(JsonParser.parseString(json), key, results);
        return results;
    }

    public void fetch(String url, String sourceName, double weight, Inscribe inscribe) {
        fetch(url, sourceName, weight, false, inscribe);
    }

    public void fetch(String url, String sourceName, double weight, boolean allowDoctypes, Inscribe inscribe) {
        SyndFeed feed;
        try {
            SyndFeedInput input = new SyndFeedInput();
            java.net.URLConnection conn = URI.create(url).toURL().openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENTS.get(ThreadLocalRandom.current().nextInt(USER_AGENTS.size())));

            if (allowDoctypes) {
                String raw = new String(conn.getInputStream().readAllBytes());
                String cleaned = raw.replaceAll("<!DOCTYPE[^>]*>", "");
                feed = input.build(new StringReader(cleaned));
            } else {
                feed = input.build(new XmlReader(conn.getInputStream()));
            }
        } catch (Exception e) {
            if (!allowDoctypes && e.getMessage() != null && e.getMessage().contains("DOCTYPE")) {
                System.out.println("  [RETRY] " + sourceName + " needs DOCTYPE — retrying...");
                fetch(url, sourceName, weight, true, inscribe);
            } else {
                System.out.println("  [ERROR] " + sourceName + ": " + e.getMessage());
            }
            return;
        }

        if (feed == null) return;

        List<SyndEntry> entries = feed.getEntries();
        System.out.println("\n=== " + sourceName + " — " + entries.size() + " articles ===");

        // Filter to unseen entries only
        List<SyndEntry> newEntries = new ArrayList<>();
        for (SyndEntry entry : entries) {
            if (!inscribe.getSeenUrls().contains(entry.getLink()) &&
                    !ActscraperApplication.getActscraperApplication().getOutOfDateUrls().contains(entry.getLink())) {
                newEntries.add(entry);
            }
        }

        if (newEntries.isEmpty()) {
            System.out.println("  No new entries.");
            return;
        }

        for (SyndEntry entry : newEntries) {
            String text = entry.getTitle()
                    + " "
                    + (entry.getDescription() != null ? entry.getDescription().getValue() : "");

            Mention mention = new Mention(
                    entry.getTitle(),
                    entry.getLink(),
                    entry.getPublishedDate(),
                    sourceName,
                    weight
            );

            // Check if URL is within the current date range
            if (entry.getPublishedDate().after(ActscraperApplication.getStartDate()) &&
                    entry.getPublishedDate().before(ActscraperApplication.getEndDate())) {

                // Evaluate relevance for all articles; Google News returns many false positives
                boolean assumeRelevance = false;

                // Within date range - add to pending for processing
                pendingItems.add(new PendingItem(
                        mention,
                        text,
                        entry.getLink(),
                        true,
                        assumeRelevance
                ));
            } else {
                // Outside date range - mark as out-of-date so we don't re-fetch it repeatedly
                ActscraperApplication.getActscraperApplication().getOutOfDateUrls().addUrl(entry.getLink());
            }
        }
    }

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:138.0) Gecko/20100101 Firefox/138.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.4 Safari/605.1.15"
    );

    private String fetchArticleContent(String urlString) {
        if (urlString == null || urlString.isBlank()) return null;

        String ua = USER_AGENTS.get(ThreadLocalRandom.current().nextInt(USER_AGENTS.size()));

        try {
            Connection.Response res = Jsoup.connect(urlString)
                    .userAgent(ua)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Upgrade-Insecure-Requests", "1")
                    .followRedirects(true)
                    .timeout(10_000)
                    .ignoreHttpErrors(true)
                    .execute();

            if (res.statusCode() >= 400)
                return null;   // let caller decide retry vs skip based on code if you surface it

            String cleaned = ArticleExtractor.extract(res.body());
            if (cleaned.isBlank()) return null;

            return cleaned.length() > MAX_CHARS ? cleaned.substring(0, MAX_CHARS) : cleaned;

        } catch (SocketTimeoutException e) {
            System.out.println("[TIMEOUT] " + urlString);
            return null;   // transient; caller can retry
        } catch (IOException e) {
            System.out.println("[FETCH-ERROR] " + urlString + ": " + e.getMessage());
            return null;
        }
    }

    private static void collectValues(JsonElement element, String key, List<String> out) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (var entry : obj.entrySet()) {
                if (entry.getKey().equals(key) && entry.getValue().isJsonPrimitive()) {
                    out.add(entry.getValue().getAsString());
                } else {
                    collectValues(entry.getValue(), key, out);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectValues(child, key, out);
            }
        }
    }

    private static String syntheticId(String prefix, String... parts) {
        long hash = 0;
        for (String part : parts) {
            hash = 31 * hash + (part != null ? part.hashCode() : 0);
        }
        return prefix + Math.abs(hash);
    }
}
