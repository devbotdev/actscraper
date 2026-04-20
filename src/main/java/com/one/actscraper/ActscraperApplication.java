package com.one.actscraper;

import com.one.actscraper.Gemini.GeminiConfig;
import com.one.actscraper.Gemini.GeminiController;
import com.one.actscraper.Gemini.GeminiService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
public class ActscraperApplication {

    private static ActscraperApplication actscraperApplication;
    private static final Inscribe inscribe = new Inscribe();
    private final GeminiConfig geminiConfig = new GeminiConfig();
    private final GeminiService geminiService = new GeminiService(geminiConfig, getGeminiApiKey());

    public ActscraperApplication() {
        actscraperApplication = this;
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
        Scrapers.fetch("https://www.balkanweb.com/feed/",       "BalkanWeb",    0.85, inscribe);
        Scrapers.fetch("https://www.panorama.com.al/feed/",     "Panorama",     0.80, inscribe);
        Scrapers.fetch("https://www.shekulli.com.al/feed/",     "Shekulli",     0.75, inscribe);
        Scrapers.fetch("https://www.gazetashqip.com/feed/",     "Gazeta Shqip", 0.70, inscribe);
        Scrapers.fetch("https://www.report.al/feed/",           "Report TV",    0.88, inscribe);

        Scrapers.fetchSocialMediaComments();
        Scrapers.fetchRedditComments(inscribe);

        Scrapers.processPendingItems(inscribe);

        System.out.println("Cycle 1 Finished. Next cycle in 5 minutes...");
    }

    public String getGeminiResponse(String prompt) {
        return geminiController.getGeminiResponse(prompt);
    }

    public static ActscraperApplication getActscraperApplication() {
        return actscraperApplication;
    }
}
