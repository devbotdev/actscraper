package com.one.actscraper.Error;

public class GeminiFailure extends RuntimeException {

    public GeminiFailure(String message) {
        super(message);
    }

    public GeminiFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
