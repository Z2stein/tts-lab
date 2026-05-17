package com.example.ttslab.audiobooks.workflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SupportedLanguageCodes {
    public static final String DEFAULT_PRODUCTION_LANGUAGE_CODE = "en-US";

    public static final List<String> PRODUCTION_LANGUAGE_CODES = List.of(
        "ar-EG",
        "nl-NL",
        "en-IN",
        "en-US",
        "fr-FR",
        "de-DE",
        "hi-IN",
        "id-ID",
        "it-IT",
        "ja-JP",
        "ko-KR",
        "mr-IN",
        "pl-PL",
        "pt-BR",
        "ro-RO",
        "ru-RU",
        "es-ES",
        "ta-IN",
        "te-IN",
        "th-TH",
        "tr-TR",
        "uk-UA",
        "vi-VN"
    );

    private static final Map<String, String> SOURCE_LANGUAGE_ALIASES = buildSourceLanguageAliases();

    private SupportedLanguageCodes() {}

    public static String normalizeSourceLanguageCode(String detectedLanguageCode) {
        if (detectedLanguageCode == null) {
            return DEFAULT_PRODUCTION_LANGUAGE_CODE;
        }
        String normalizedKey = detectedLanguageCode.trim().toLowerCase(Locale.ROOT);
        if (normalizedKey.isBlank()) {
            return DEFAULT_PRODUCTION_LANGUAGE_CODE;
        }
        return SOURCE_LANGUAGE_ALIASES.getOrDefault(normalizedKey, DEFAULT_PRODUCTION_LANGUAGE_CODE);
    }

    public static String initialProductionLanguageCode(String sourceLanguageCode) {
        return PRODUCTION_LANGUAGE_CODES.contains(sourceLanguageCode)
            ? sourceLanguageCode
            : DEFAULT_PRODUCTION_LANGUAGE_CODE;
    }

    private static Map<String, String> buildSourceLanguageAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        addAliases(aliases, "ar-EG", "ar-eg", "ar", "arabic", "arabic (egypt)", "egyptian arabic");
        addAliases(aliases, "nl-NL", "nl-nl", "nl", "dutch", "dutch (netherlands)", "nederlands");
        addAliases(aliases, "en-IN", "en-in", "english (india)", "indian english");
        addAliases(aliases, "en-US", "en-us", "en", "english", "english (us)", "english/us", "american english");
        addAliases(aliases, "fr-FR", "fr-fr", "fr", "french", "french (france)", "français");
        addAliases(aliases, "de-DE", "de-de", "de", "german", "german (germany)", "deutsch");
        addAliases(aliases, "hi-IN", "hi-in", "hi", "hindi", "hindi (india)");
        addAliases(aliases, "id-ID", "id-id", "id", "indonesian", "bahasa indonesia");
        addAliases(aliases, "it-IT", "it-it", "it", "italian", "italiano");
        addAliases(aliases, "ja-JP", "ja-jp", "ja", "japanese", "japanese (japan)", "日本語");
        addAliases(aliases, "ko-KR", "ko-kr", "ko", "korean", "한국어");
        addAliases(aliases, "mr-IN", "mr-in", "mr", "marathi");
        addAliases(aliases, "pl-PL", "pl-pl", "pl", "polish", "polski");
        addAliases(aliases, "pt-BR", "pt-br", "pt", "portuguese", "portuguese (brazil)", "brazilian portuguese", "português");
        addAliases(aliases, "ro-RO", "ro-ro", "ro", "romanian", "română");
        addAliases(aliases, "ru-RU", "ru-ru", "ru", "russian", "русский");
        addAliases(aliases, "es-ES", "es-es", "es", "spanish", "spanish (spain)", "español");
        addAliases(aliases, "ta-IN", "ta-in", "ta", "tamil", "தமிழ்");
        addAliases(aliases, "te-IN", "te-in", "te", "telugu", "తెలుగు");
        addAliases(aliases, "th-TH", "th-th", "th", "thai", "ไทย");
        addAliases(aliases, "tr-TR", "tr-tr", "tr", "turkish", "türkçe");
        addAliases(aliases, "uk-UA", "uk-ua", "uk", "ukrainian", "українська");
        addAliases(aliases, "vi-VN", "vi-vn", "vi", "vietnamese", "tiếng việt");
        return Map.copyOf(aliases);
    }

    private static void addAliases(Map<String, String> aliases, String canonicalCode, String... values) {
        aliases.put(canonicalCode.toLowerCase(Locale.ROOT), canonicalCode);
        for (String value : values) {
            aliases.put(value.toLowerCase(Locale.ROOT), canonicalCode);
        }
    }
}
