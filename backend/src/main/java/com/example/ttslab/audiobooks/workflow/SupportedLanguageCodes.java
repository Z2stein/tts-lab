package com.example.ttslab.audiobooks.workflow;

import java.util.List;
import java.util.Locale;

public enum SupportedLanguageCodes {
    AR_EG("ar-EG", "ar-eg", "ar", "arabic", "arabic (egypt)", "egyptian arabic"),
    NL_NL("nl-NL", "nl-nl", "nl", "dutch", "dutch (netherlands)", "nederlands"),
    EN_IN("en-IN", "en-in", "english (india)", "indian english"),
    EN_US("en-US", "en-us", "en", "english", "english (us)", "english/us", "american english"),
    FR_FR("fr-FR", "fr-fr", "fr", "french", "french (france)", "français"),
    DE_DE("de-DE", "de-de", "de", "german", "german (germany)", "deutsch"),
    HI_IN("hi-IN", "hi-in", "hi", "hindi", "hindi (india)"),
    ID_ID("id-ID", "id-id", "id", "indonesian", "bahasa indonesia"),
    IT_IT("it-IT", "it-it", "it", "italian", "italiano"),
    JA_JP("ja-JP", "ja-jp", "ja", "japanese", "japanese (japan)", "日本語"),
    KO_KR("ko-KR", "ko-kr", "ko", "korean", "한국어"),
    MR_IN("mr-IN", "mr-in", "mr", "marathi"),
    PL_PL("pl-PL", "pl-pl", "pl", "polish", "polski"),
    PT_BR("pt-BR", "pt-br", "pt", "portuguese", "portuguese (brazil)", "brazilian portuguese", "português"),
    RO_RO("ro-RO", "ro-ro", "ro", "romanian", "română"),
    RU_RU("ru-RU", "ru-ru", "ru", "russian", "русский"),
    ES_ES("es-ES", "es-es", "es", "spanish", "spanish (spain)", "español"),
    TA_IN("ta-IN", "ta-in", "ta", "tamil", "தமிழ்"),
    TE_IN("te-IN", "te-in", "te", "telugu", "తెలుగు"),
    TH_TH("th-TH", "th-th", "th", "thai", "ไทย"),
    TR_TR("tr-TR", "tr-tr", "tr", "turkish", "türkçe"),
    UK_UA("uk-UA", "uk-ua", "uk", "ukrainian", "українська"),
    VI_VN("vi-VN", "vi-vn", "vi", "vietnamese", "tiếng việt");

    public static final String DEFAULT_PRODUCTION_LANGUAGE_CODE = "en-US";

    private final String code;
    private final String[] aliases;

    SupportedLanguageCodes(String code, String... aliases) {
        this.code = code;
        this.aliases = aliases;
    }

    public String getCode() {
        return code;
    }

    public static List<String> getProductionLanguageCodes() {
        return List.of(
            "ar-EG", "nl-NL", "en-IN", "en-US", "fr-FR", "de-DE", "hi-IN", "id-ID",
            "it-IT", "ja-JP", "ko-KR", "mr-IN", "pl-PL", "pt-BR", "ro-RO", "ru-RU",
            "es-ES", "ta-IN", "te-IN", "th-TH", "tr-TR", "uk-UA", "vi-VN"
        );
    }

    public static String normalizeSourceLanguageCode(String detectedLanguageCode) {
        if (detectedLanguageCode == null || detectedLanguageCode.trim().isBlank()) {
            return DEFAULT_PRODUCTION_LANGUAGE_CODE;
        }
        String normalizedKey = detectedLanguageCode.trim().toLowerCase(Locale.ROOT);
        for (SupportedLanguageCodes lang : values()) {
            if (lang.code.toLowerCase(Locale.ROOT).equals(normalizedKey)) {
                return lang.code;
            }
            for (String alias : lang.aliases) {
                if (alias.toLowerCase(Locale.ROOT).equals(normalizedKey)) {
                    return lang.code;
                }
            }
        }
        return DEFAULT_PRODUCTION_LANGUAGE_CODE;
    }

    public static String initialProductionLanguageCode(String sourceLanguageCode) {
        return getProductionLanguageCodes().contains(sourceLanguageCode)
            ? sourceLanguageCode
            : DEFAULT_PRODUCTION_LANGUAGE_CODE;
    }
}
