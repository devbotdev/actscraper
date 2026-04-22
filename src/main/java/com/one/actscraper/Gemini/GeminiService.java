package com.one.actscraper.Gemini;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

public class GeminiService {

    private final Client client;

    public GeminiService(GeminiConfig client, String api) {
        this.client = client.geminiClient(api);
    }

    protected GenerateContentResponse askGemini(String prompt) {
        assert client != null;
//        System.out.println(prompt);
        return client.models.generateContent(
//                "gemini-2.5-flash",
                "gemini-3-flash-preview",
                prompt,
                null);
    }
}
