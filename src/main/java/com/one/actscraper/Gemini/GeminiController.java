package com.one.actscraper.Gemini;

public class GeminiController {

    private final GeminiService geminiService;

    public GeminiController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    public String getGeminiResponse(String prompt) {
        return geminiService.askGemini(prompt).text();
    }
}
