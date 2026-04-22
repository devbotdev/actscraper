package com.one.actscraper;

import com.one.actscraper.Gemini.GeminiConfig;
import com.one.actscraper.Gemini.GeminiController;
import com.one.actscraper.Gemini.GeminiService;
import com.one.actscraper.Item.Item;
import com.one.actscraper.Item.Keywords;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;

@SpringBootApplication
@EnableScheduling
public class ActscraperApplication {

    // Enable if the article fetching is being rate limited
    private static final boolean ratelimiting = false;

    private final String eval = ", if so, append each item with a positive / negative impression tag.";
    private static ActscraperApplication actscraperApplication;
    private final Inscribe inscribe;
    private final OutOfDateUrls outOfDateUrls;
    private final GeminiConfig geminiConfig;
    private final GeminiService geminiService;
    private final ArrayList<Item> map;
    private final Keywords keywords;
    private final Scrapers scrapers;
    private final GeminiController geminiController;

    private final String topic = "One Albania's current affairs";

    private final String systemPrompt = topic + eval;

    private static Date startDate;
    private static Date endDate;

    // Should the full text be sent or the URL? URL only seems to be more token efficient
    private final boolean fulltextEnabled = false;
    public boolean isFulltextEnabled() {
        return fulltextEnabled;
    }

    public ActscraperApplication() {
        actscraperApplication = this;

        startDate = Date.from(
                LocalDate.now().minusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        endDate = Date.from(
                LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        map = new ArrayList<>();
        map.add(new Item("https://www.balkanweb.com/feed/",       "BalkanWeb",    0.85));
//        map.add(new Item("https://www.panorama.com.al/feed/",     "Panorama",     0.80));
//        map.add(new Item("https://www.shekulli.com.al/feed/",     "Shekulli",     0.75));
//        map.add(new Item("https://www.gazetashqip.com/feed/",     "Gazeta Shqip", 0.70));
//        map.add(new Item("https://www.report.al/feed/",           "Report TV",    0.88));

        keywords = new Keywords();

        for (int i = 0; i < new Keywords().size(); i++)
            map.add(new Item("https://news.google.com/rss/search?q=" + keywords.get(i),
                    MessageFormat.format("Google News {0}", keywords.get(i).replace("+", " ")),    0.88));

        inscribe = new Inscribe();
        outOfDateUrls = new OutOfDateUrls();
        geminiConfig = new GeminiConfig();
        geminiService = new GeminiService(geminiConfig, getGeminiApiKey());
        geminiController = new GeminiController(geminiService);
        scrapers = new Scrapers();
    }

    private String getGeminiApiKey() {
        return FileStorage.readApiKeyFromFile("gemini", "gemini_api_key.txt");
    }

    static void main(String[] args) {
        new ActscraperApplication();
        SpringApplication.run(ActscraperApplication.class, args);
    }

    @Scheduled(fixedRate = 300000) // Runs every 5 minutes
    public void fetchAllSources() {
        System.out.println("Running scheduled fetch...");

        for (Item item : map) {
            scrapers.fetch(item.url(), item.name(), item.weight(), inscribe);
        }

        // Usage Limit - Uncomment when fixed
        scrapers.fetchSocialMediaComments();

        scrapers.fetchRedditComments(inscribe);

        // Run the article body text
        scrapers.processPendingItems(inscribe);

        System.out.println("Cycle Finished. Next cycle in 5 minutes...");
    }

    public String getGeminiResponse(String prompt) {
        return geminiController.getGeminiResponse(prompt);
    }

    public static ActscraperApplication getActscraperApplication() {
        return actscraperApplication;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getTopic() {
        return topic;
    }

    public static Date getStartDate() {
        return ActscraperApplication.startDate;
    }

    public static void setStartDate(Date startDate) {
        ActscraperApplication.startDate = startDate;
    }

    public static Date getEndDate() {
        return ActscraperApplication.endDate;
    }

    public static void setEndDate(Date endDate) {
        ActscraperApplication.endDate = endDate;
    }

    public OutOfDateUrls getOutOfDateUrls() {
        return outOfDateUrls;
    }

    public static boolean getRateLimiting() {return ratelimiting;}
}

