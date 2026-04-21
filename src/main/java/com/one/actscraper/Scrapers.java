package com.one.actscraper;

import com.one.actscraper.Error.GeminiFailure;
import com.one.actscraper.Gemini.GeminiEntryPromptService;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class Scrapers {

    // From rapid API, there's plenty more
    private static final String igAPIString = "instagram-scraper-20251.p.rapidapi.com";
    private static final String fbAPIString = "facebook-scraper-api4.p.rapidapi.com";

    private static final String subReddit = "r/albania";
    private static final String instagramUsername = "one.albania";
    private static final String facebookID = "100064865822999"; // Facebook won't work with usernames

    private static class PendingItem {
        Mention mention;
        String textToPrompt;
        String urlToInscribe;
        boolean webpageItem;
        String fullArticleContent;

        public PendingItem(Mention mention, String textToPrompt, String urlToInscribe, boolean webpageItem) {
            this.mention = mention;
            this.textToPrompt = textToPrompt;
            this.urlToInscribe = urlToInscribe;
            this.webpageItem = webpageItem;
            this.fullArticleContent = null;
        }

        String buildPromptText() {
            if (!webpageItem) {
                return textToPrompt;
            }

            if (fullArticleContent != null && !fullArticleContent.isBlank()) {
                return textToPrompt
                        + "\n\n=== FULL ARTICLE BODY ===\n"
                        + fullArticleContent
                        + "\n=== END ARTICLE ===";
            } else {
                // Fallback: just use the title and description
                return textToPrompt;
            }
        }
    }

    private static final List<PendingItem> pendingItems = new ArrayList<>();

    public static void processPendingItems(Inscribe inscribe) {
        if (pendingItems.isEmpty()) {
            System.out.println("No new articles to process through Gemini.");
            return;
        }

        System.out.println("Processing " + pendingItems.size() + " accumulated items through Gemini in bulk...");

        // Fetch full article content for webpage items before processing
        System.out.println("Fetching full article content for webpage sources...");
        for (PendingItem item : pendingItems) {
            if (item.webpageItem && item.urlToInscribe != null) {
                String content = fetchArticleContent(item.urlToInscribe);
                if (content != null && !content.isBlank()) {
                    item.fullArticleContent = content;
                    System.out.println("  [FETCHED] " + item.urlToInscribe + " (" + content.length() + " chars)");
                }
            }
        }

        List<String> texts = new ArrayList<>();
        for (PendingItem item : pendingItems) {
            texts.add(item.buildPromptText());
        }

        GeminiEntryPromptService.EvaluationResult[] evaluations;
        try {
            evaluations = GeminiEntryPromptService.checkBatchEvaluations(texts);
        } catch (GeminiFailure e) {
            System.out.println("  [ERROR] Gemini failed to evaluate pending batch. Skipping persistence for this run: " + e.getMessage());
            for (PendingItem item : pendingItems) {
                String retryKey = item.urlToInscribe != null ? item.urlToInscribe : item.mention.getUrl();
                System.out.println("  [RETRY] Not persisted due to Gemini failure: " + retryKey);
            }
            // Clear failed in-memory batch to avoid duplicate accumulation; they will be re-fetched next run.
            pendingItems.clear();
            return;
        }

        System.out.println("\n=== Results & Saving ===");

        // Load existing results from JSON to avoid overwriting
        List<AnalysisResult> analysisResults = new ArrayList<>(FileStorage.loadAnalysisResultsFromJson());

        for (int i = 0; i < pendingItems.size(); i++) {
            PendingItem item = pendingItems.get(i);
            if (item.urlToInscribe != null && inscribe != null) {
                inscribe.addUrl(item.urlToInscribe);
            }
            item.mention.setFlagged(evaluations[i].isRelevant());
            item.mention.setFeedbackTone(evaluations[i].getFeedbackTone());
            item.mention.setAppraisal(evaluations[i].getAppraisal());
            System.out.println(item.mention);
            FileStorage.saveMentionToFile(item.mention);

            // Create AnalysisResult and save to JSON
            AnalysisResult result = new AnalysisResult(item.mention, evaluations[i].getAppraisal());
            analysisResults.add(result);
        }

        // Save all results to JSON (existing + new)
        if (!analysisResults.isEmpty()) {
            FileStorage.saveAnalysisResultsToJson(analysisResults);
        }

        pendingItems.clear();
    }

    public static void fetchRedditComments(Inscribe inscribe) {
        System.out.println("Fetching Reddit comments...");
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.reddit.com/"+subReddit+"/comments.json?limit=10"))
                    .header("User-Agent", "ActScraper/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String json = response.body();
                String[] items = json.split("\"body\":");

                // Collect new (unseen) comments first
                List<String> bodies = new ArrayList<>();
                List<String> fUrls = new ArrayList<>();

                for (int i = 1; i < items.length; i++) {
                    String part = items[i].split(",")[0].replace("\"", "");
                    if (part.length() > 5) {
                        String fUrl = "reddit-" + part.hashCode();
                        if (inscribe.getSeenUrls().contains(fUrl)) continue;
                        bodies.add(part);
                        fUrls.add(fUrl);
                    }
                }

                for (int i = 0; i < bodies.size(); i++) {
                    String part = bodies.get(i);
                    String fUrl = fUrls.get(i);
                    Mention m = new Mention(
                            "Reddit Comment: " + (part.length() > 30 ? part.substring(0, 30) + "..." : part),
                            fUrl, new Date(), "Reddit", 0.60);
                    if (m.getPublishedDate().after(ActscraperApplication.getStartDate()) & m.getPublishedDate().before(ActscraperApplication.getEndDate()))
                        pendingItems.add(new PendingItem(m, part, fUrl, false));
                    else
                        ActscraperApplication.getOutOfDateUrls().addUrl(fUrl);
                }
            } else {
                System.out.println("Reddit API returned " + response.statusCode());
            }

        } catch (Exception e) {
            System.out.println("  [ERROR] Reddit: " + e.getMessage());
        }
    }

    public static void fetchSocialMediaComments() {
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
                    .uri(URI.create("https://" + fbAPIString + "/get_facebook_page_posts_details_from_id?profile_id="+facebookID+"&timezone=UTC"))
                    .header("x-rapidapi-key", rapidApiKey)
                    .header("x-rapidapi-host", fbAPIString)
                    .GET().build();

            HttpResponse<String> fbPostsResponse = client.send(fbPostsRequest, HttpResponse.BodyHandlers.ofString());
            if (fbPostsResponse.statusCode() == 200) {
                List<String> postLinks = extractJsonValues(fbPostsResponse.body(), "post_link");
                for (String link : postLinks) {
                    String encoded = java.net.URLEncoder.encode(link, StandardCharsets.UTF_8);
                    HttpRequest commentsReq = HttpRequest.newBuilder()
                            .uri(URI.create("https://"+fbAPIString+"/get_facebook_post_comments_details?link=" + encoded))
                            .header("x-rapidapi-key", rapidApiKey)
                            .header("x-rapidapi-host", fbAPIString)
                            .GET().build();

                    HttpResponse<String> commentsResp = client.send(commentsReq, HttpResponse.BodyHandlers.ofString());
                    if (commentsResp.statusCode() == 200) {
                        List<String> comments = extractJsonValues(commentsResp.body(), "comment_text");
                        for (String comment : comments) {
                            String url = "fb-" + link.hashCode() + "-" + comment.hashCode();
                            Mention m = new Mention(
                                    "FB Comment: " + (comment.length() > 30 ? comment.substring(0, 30) + "..." : comment),
                                    url, new Date(), "Facebook", 0.70);
                            if (m.getPublishedDate().after(ActscraperApplication.getStartDate()) & m.getPublishedDate().before(ActscraperApplication.getEndDate()))
                                pendingItems.add(new PendingItem(m, comment, null, false));
                            else
                                ActscraperApplication.getOutOfDateUrls().addUrl(url);
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
                    .uri(URI.create("https://"+igAPIString+"/userposts/?username_or_id=" + instagramUsername))
                    .header("x-rapidapi-key", rapidApiKey)
                    .header("x-rapidapi-host", igAPIString)
                    .GET().build();

            HttpResponse<String> igPostsResponse = client.send(igPostsRequest, HttpResponse.BodyHandlers.ofString());
            if (igPostsResponse.statusCode() == 200) {
                List<String> postCodes = extractJsonValues(igPostsResponse.body(), "code");
                for (String code : postCodes) {
                    HttpRequest commentsReq = HttpRequest.newBuilder()
                            .uri(URI.create("https://"+igAPIString+"/postcomments/?code_or_url=" + code))
                            .header("x-rapidapi-key", rapidApiKey)
                            .header("x-rapidapi-host", igAPIString)
                            .GET().build();

                    HttpResponse<String> commentsResp = client.send(commentsReq, HttpResponse.BodyHandlers.ofString());
                    if (commentsResp.statusCode() == 200) {
                        List<String> comments = extractJsonValues(commentsResp.body(), "text");
                        for (String comment : comments) {
                            String url = "ig-" + code.hashCode() + "-" + comment.hashCode();
                            Mention m = new Mention(
                                    "IG Comment: " + (comment.length() > 30 ? comment.substring(0, 30) + "..." : comment),
                                    url, new Date(), "Instagram", 0.70);
                            if (m.getPublishedDate().after(ActscraperApplication.getStartDate()) & m.getPublishedDate().before(ActscraperApplication.getEndDate()))
                                pendingItems.add(new PendingItem(m, comment, null, false));
                            else
                                ActscraperApplication.getOutOfDateUrls().addUrl(url);
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
        String search = "\"" + key + "\"";
        int idx = 0;
        while ((idx = json.indexOf(search, idx)) != -1) {
            idx = json.indexOf(":", idx) + 1;
            while (idx < json.length() && json.charAt(idx) == ' ') idx++;
            if (idx < json.length() && json.charAt(idx) == '"') {
                int end = json.indexOf("\"", idx + 1);
                if (end != -1) results.add(json.substring(idx + 1, end));
            }
            idx++;
        }
        return results;
    }

    public static void fetch(String url, String sourceName, double weight, Inscribe inscribe) {
        fetch(url, sourceName, weight, false, inscribe);
    }

    public static void fetch(String url, String sourceName, double weight, boolean allowDoctypes, Inscribe inscribe) {
        SyndFeed feed;
        try {
            SyndFeedInput input = new SyndFeedInput();
            java.net.URLConnection conn = new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

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
                !ActscraperApplication.getOutOfDateUrls().contains(entry.getLink())) {
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
            if (entry.getPublishedDate().after(ActscraperApplication.getStartDate()) &
                entry.getPublishedDate().before(ActscraperApplication.getEndDate())) {
                // Within date range - add to pending for processing
                pendingItems.add(new PendingItem(
                    mention,
                    text,
                    entry.getLink(),
                    true
                ));
            } else {
                // Outside date range - mark as out-of-date so we don't re-fetch it repeatedly
                ActscraperApplication.getOutOfDateUrls().addUrl(entry.getLink());
            }
        }
    }

    private static String fetchArticleContent(String urlString) {
        try {
            if (urlString == null || urlString.isBlank()) {
                return null;
            }

            // Fetch the HTML document with a reasonable timeout
            Document doc = Jsoup.connect(urlString)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(10000)
                    .get();


            // Remove noise elements before extracting text
            for (String selector : List.of(
                    "nav", "footer", "header", "aside",
                    "script", "style", "noscript",
                    "[class*=ad]", "[id*=ad]", "[class*=banner]",
                    "[class*=cookie]", "[class*=popup]", "[class*=sidebar]",
                    "figure", "figcaption"
            )) {
                doc.select(selector).remove();
            }

            // Extract text from common article content containers
            String text = doc.selectFirst("article") != null
                    ? Objects.requireNonNull(doc.selectFirst("article")).text()
                    : doc.selectFirst("main") != null
                    ? Objects.requireNonNull(doc.selectFirst("main")).text()
                    : doc.selectFirst("[role=main]") != null
                    ? Objects.requireNonNull(doc.selectFirst("[role=main]")).text()
                    : null;

            // Fallback: try body if no specific container found
            if (text == null || text.isBlank()) {
                text = doc.body().text();
            }

            // Clean up excessive whitespace
            text = text.replaceAll("\\s+", " ").trim();
            // Cap at 8000 characters to save tokens
            if (text.length() > 8000) {
                text = text.substring(0, 8000) + "...";
            }

            System.out.println(text);

            return text;
        } catch (Exception e) {
            System.out.println("  [WARNING] Failed to fetch article content from " + urlString + ": " + e.getMessage());
            return null;
        }
    }
}
