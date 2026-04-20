package com.one.actscraper.Gemini;


import com.google.genai.Client;
import org.springframework.context.annotation.Bean;

public class GeminiConfig {

    @Bean
    public Client geminiClient(String api) {
        return Client.builder().apiKey(api).build();
    }
}
