package com.one.actscraper;

import com.one.actscraper.Gemini.GeminiConfig;
import com.one.actscraper.Gemini.GeminiController;
import com.one.actscraper.Gemini.GeminiService;
import com.one.actscraper.Item.Item;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;

@SpringBootApplication
@EnableScheduling
public class ActscraperApplication {

    private static ActscraperApplication actscraperApplication;
    private static final Inscribe inscribe = new Inscribe();
    private final GeminiConfig geminiConfig = new GeminiConfig();
    private final GeminiService geminiService = new GeminiService(geminiConfig, getGeminiApiKey());
    private final ArrayList<Item> map = new ArrayList<>();
    private final String eval = "if so, append each item with a positive / negative impression tag.";
    private final String systemPrompt =
            "Determine if each item below is related to One Albania / Albanian Telecommunications current affairs, " + eval;

    public ActscraperApplication() {
        actscraperApplication = this;

        map.add(new Item("https://www.balkanweb.com/feed/",       "BalkanWeb",    0.85));
        map.add(new Item("https://www.panorama.com.al/feed/",     "Panorama",     0.80));
        map.add(new Item("https://www.shekulli.com.al/feed/",     "Shekulli",     0.75));
        map.add(new Item("https://www.gazetashqip.com/feed/",     "Gazeta Shqip", 0.70));
        map.add(new Item("https://www.report.al/feed/",           "Report TV",    0.88));
    }

    private String getGeminiApiKey() {
        return FileStorage.readApiKeyFromFile("gemini", "gemini_api_key.txt");
    }

    private final GeminiController geminiController = new GeminiController(geminiService);

    public static void main(String[] args) {
        new ActscraperApplication();
        SpringApplication.run(ActscraperApplication.class, args);
    }

    @Scheduled(fixedRate = 300000) // Runs every 5 minutes
    public void fetchAllSources() {
        System.out.println("Running scheduled fetch...");

        for (Item item : map) {
            Scrapers.fetch(item.url(), item.name(), item.weight(), inscribe);
        }

        // Usage Limit - Uncomment when fixed
        //Scrapers.fetchSocialMediaComments();

        Scrapers.fetchRedditComments(inscribe);
        Scrapers.processPendingItems(inscribe);

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
}
