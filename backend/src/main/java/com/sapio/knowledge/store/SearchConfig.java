package com.sapio.knowledge.store;

import java.util.Map;

/** Maps a document language (ISO 639-1) to the PostgreSQL full-text configuration (stemming). */
public final class SearchConfig {

    private static final Map<String, String> CONFIGS = Map.of(
            "en", "english", "es", "spanish", "fr", "french", "de", "german", "pt", "portuguese", "it", "italian");

    private SearchConfig() {
    }

    public static String forLanguage(String language) {
        return language == null ? "simple" : CONFIGS.getOrDefault(language.toLowerCase(), "simple");
    }
}
