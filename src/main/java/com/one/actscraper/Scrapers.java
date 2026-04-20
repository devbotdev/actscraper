package com.one.actscraper;

import com.one.actscraper.Gemini.GeminiEntryPromptService;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Scrapers {

    private static class PendingItem {
        Mention mention;
        String textToPrompt;
        String urlToInscribe;
        
        PendingItem(Mention mention, String textToPrompt, String urlToInscribe) {
            this.mention = mention;
            this.textToPrompt = textToPrompt;
            this.urlToInscribe = urlToInscribe;
        }
    }

    private static final List<PendingItem> pendingItems = new ArrayList<>();

    public static void processPendingItems(Inscribe inscribe) {
        if (pendingItems.isEmpty()) {
            System.out.println("No new articles to process through Gemini.");
            return;
        }

        System.out.println("Processing " + pendingItems.size() + " accumulated items through Gemini in bulk...");
        List<String> texts = new ArrayList<>();
        for (PendingItem item : pendingItems) {
            texts.add(item.textToPrompt);
        }

        boolean[] flags = GeminiEntryPromptService.checkBatch(texts);

        System.out.println("\n=== Results & Saving ===");
        for (int i = 0; i < pendingItems.size(); i++) {
            PendingItem item = pendingItems.get(i);
            if (item.urlToInscribe != null && inscribe != null) {
                inscribe.addUrl(item.urlToInscribe);
            }
            item.mention.setFlagged(flags[i]);
            System.out.println(item.mention);
            FileStorage.saveMentionToFile(item.mention);
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
                    .uri(URI.create("https://www.reddit.com/r/albania/comments.json?limit=10"))
                    .header("User-Agent", "ActScraper/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String json = response.body();
                String[] items = json.split("\"body\":");

                // Collect new (unseen) comments first
                List<String> bodies = new ArrayList<>();
                List<String> fakeUrls = new ArrayList<>();

                for (int i = 1; i < items.length; i++) {
                    String part = items[i].split(",")[0].replaceAll("\"", "");
                    if (part.length() > 5) {
                        String fakeUrl = "reddit-" + part.hashCode();
                        if (inscribe.getSeenUrls().contains(fakeUrl)) continue;
                        bodies.add(part);
                        fakeUrls.add(fakeUrl);
                    }
                }

                for (int i = 0; i < bodies.size(); i++) {
                    String part = bodies.get(i);
                    String fakeUrl = fakeUrls.get(i);
                    Mention m = new Mention(
                            "Reddit Comment: " + (part.length() > 30 ? part.substring(0, 30) + "..." : part),
                            fakeUrl, new Date(), "Reddit", 0.60);
                    pendingItems.add(new PendingItem(m, part, fakeUrl));
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

            HttpRequest igRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://instagram-scraper-api2.p.rapidapi.com/v1/comments?code_or_id_or_url=one.albania"))
                    .header("X-RapidAPI-Key", rapidApiKey)
                    .header("X-RapidAPI-Host", "instagram-scraper-api2.p.rapidapi.com")
                    .GET()
                    .build();

            HttpResponse<String> igResponse = client.send(igRequest, HttpResponse.BodyHandlers.ofString());
            if (igResponse.statusCode() == 200) {
                System.out.println("Instagram RapidAPI call successful.");
                Mention m = new Mention("Instagram RapidAPI Data", "ig-sample", new Date(), "Instagram", 0.70);
                pendingItems.add(new PendingItem(m, "Instagram RapidAPI Data", null));
            } else {
                System.out.println("  [ERROR] Instagram API returned status " + igResponse.statusCode() + ": " + igResponse.body());
            }

            HttpRequest fbRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://facebook-scraper3.p.rapidapi.com/comments?post_url=https://www.facebook.com/one.albania/"))
                    .header("X-RapidAPI-Key", rapidApiKey)
                    .header("X-RapidAPI-Host", "facebook-scraper3.p.rapidapi.com")
                    .GET()
                    .build();

            HttpResponse<String> fbResponse = client.send(fbRequest, HttpResponse.BodyHandlers.ofString());
            if (fbResponse.statusCode() == 200) {
                System.out.println("Facebook RapidAPI call successful.");
                Mention m = new Mention("Facebook RapidAPI Data", "fb-sample", new Date(), "Facebook", 0.70);
                pendingItems.add(new PendingItem(m, "Facebook RapidAPI Data", null));
            } else {
                System.out.println("  [ERROR] Facebook API returned status " + fbResponse.statusCode() + ": " + fbResponse.body());
            }

        } catch (Exception e) {
            System.out.println("  [ERROR] RapidAPI: " + e.getMessage());
        }
    }

    public static void fetch(String url, String sourceName, double weight, Inscribe inscribe) {
        fetch(url, sourceName, weight, false, inscribe);
    }

    public static void fetch(String url, String sourceName, double weight, boolean allowDoctypes, Inscribe inscribe) {
        SyndFeed feed = null;
        try {
            SyndFeedInput input = new SyndFeedInput();
            if (allowDoctypes) {
                String raw = new String(new URL(url).openStream().readAllBytes());
                String cleaned = raw.replaceAll("<!DOCTYPE[^>]*>", "");
                feed = input.build(new StringReader(cleaned));
            } else {
                feed = input.build(new XmlReader(new URL(url)));
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
            if (!inscribe.getSeenUrls().contains(entry.getLink())) {
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
            
            pendingItems.add(new PendingItem(mention, text, entry.getLink()));
        }
    }
}
