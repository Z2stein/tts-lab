package com.example.ttslab.audiobooks.workflow;

public enum SpeakerVoice {
    ZEPHYR(
            "Zephyr",
            "Bright",
            "Bright, airy, youthful, and optimistic. Works well for fresh, energetic narration, uplifting audiobook passages, friendly introductions, and scenes with a light emotional tone."
    ),
    PUCK(
            "Puck",
            "Upbeat",
            "Playful, quick, clever, and approachable. Suitable for casual explanations, humorous dialogue, lively characters, and narration that should feel friendly without becoming too serious."
    ),
    CHARON(
            "Charon",
            "Informative",
            "Calm, steady, mature, and guiding. Works well for serious narration, explanations, reflective passages, documentaries, and audiobook sections that need trust and clarity."
    ),
    KORE(
            "Kore",
            "Firm",
            "Direct, focused, confident, and energetic. Suitable for assertive narration, decisive characters, instructional content, and passages that need clear emphasis."
    ),
    FENRIR(
            "Fenrir",
            "Excitable",
            "Dynamic, intense, adventurous, and expressive. Works well for action scenes, dramatic moments, fantasy characters, suspenseful narration, and emotionally charged passages."
    ),
    LEDA(
            "Leda",
            "Youthful",
            "Young, light, gentle, and expressive. Suitable for youthful characters, soft narration, coming-of-age stories, intimate dialogue, and emotionally delicate scenes."
    ),
    ORUS(
            "Orus",
            "Firm",
            "Grounded, authoritative, strong, and composed. Works well for mentor figures, leaders, serious narration, historical passages, and moments requiring weight and certainty."
    ),
    AOEDE(
            "Aoede",
            "Breezy",
            "Smooth, artistic, conversational, and relaxed. Suitable for storytelling, poetic narration, reflective audiobook passages, creative essays, and warm long-form listening."
    ),
    CALLIRRHOE(
            "Callirrhoe",
            "Easy-going",
            "Relaxed, clear, calm, and approachable. Works well for friendly narration, everyday dialogue, lifestyle content, and scenes that should feel natural and effortless."
    ),
    AUTONOE(
            "Autonoe",
            "Bright",
            "Clear, intelligent, alert, and polished. Suitable for curious characters, educational narration, explanatory content, and passages that should feel smart and engaged."
    ),
    ENCELADUS(
            "Enceladus",
            "Breathy",
            "Soft, mysterious, intimate, and slightly ethereal. Works well for suspense, quiet dramatic moments, dreamy narration, secrets, and scenes with hidden tension."
    ),
    IAPETUS(
            "Iapetus",
            "Clear",
            "Precise, neutral, balanced, and easy to understand. Suitable for straightforward narration, practical explanations, audiobook exposition, and content where clarity matters most."
    ),
    UMBRIEL(
            "Umbriel",
            "Easy-going",
            "Smooth, calm, slightly dark, and reflective. Works well for evening narration, thoughtful passages, quiet mystery, calm dialogue, and understated emotional scenes."
    ),
    ALGIEBA(
            "Algieba",
            "Smooth",
            "Polished, elegant, controlled, and warm. Suitable for refined narration, professional storytelling, calm confidence, and audiobook passages that should feel smooth and composed."
    ),
    DESPINA(
            "Despina",
            "Smooth",
            "Warm, welcoming, gentle, and graceful. Works well for comforting narration, hospitality-like introductions, emotional dialogue, and scenes with a soft human touch."
    ),
    ERINOME(
            "Erinome",
            "Clear",
            "Articulate, professional, organized, and focused. Suitable for educational sections, precise narration, formal explanations, and audiobook passages that need structure and clarity."
    ),
    ALGENIB(
            "Algenib",
            "Gravelly",
            "Textured, mature, grounded, and characterful. Works well for seasoned narrators, dramatic storytelling, noir-like passages, rugged characters, and emotionally worn voices."
    ),
    RASALGETHI(
            "Rasalgethi",
            "Informative",
            "Curious, thoughtful, explanatory, and slightly distinctive. Suitable for investigative narration, documentary-style content, reflective questions, and intellectually engaging passages."
    ),
    LAOMEDEIA(
            "Laomedeia",
            "Upbeat",
            "Friendly, animated, clear, and positive. Works well for lively narration, cheerful characters, learning content, and scenes that need accessible energy."
    ),
    ACHERNAR(
            "Achernar",
            "Soft",
            "Gentle, calm, delicate, and soothing. Suitable for bedtime-style narration, meditation-like passages, emotional reflection, quiet dialogue, and peaceful scenes."
    ),
    ALNILAM(
            "Alnilam",
            "Firm",
            "Commanding, energetic, confident, and forward-moving. Works well for motivational narration, leaders, announcements, decisive scenes, and passages with strong momentum."
    ),
    SCHEDAR(
            "Schedar",
            "Even",
            "Balanced, steady, neutral, and reliable. Suitable for long audiobook narration, calm explanations, grounded characters, and content that should not feel too dramatic."
    ),
    GACRUX(
            "Gacrux",
            "Mature",
            "Experienced, calm, serious, and trustworthy. Works well for documentaries, historical narration, older characters, reflective nonfiction, and scenes with life experience."
    ),
    PULCHERRIMA(
            "Pulcherrima",
            "Forward",
            "Expressive, present, bright, and charismatic. Suitable for bold characters, energetic narration, promotional moments, and scenes that should immediately catch attention."
    ),
    ACHIRD(
            "Achird",
            "Friendly",
            "Warm, approachable, helpful, and relaxed. Works well for assistant-like narration, friendly characters, simple explanations, and comforting conversational passages."
    ),
    ZUBENELGENUBI(
            "Zubenelgenubi",
            "Casual",
            "Informal, conversational, relaxed, and down-to-earth. Suitable for modern dialogue, casual narration, humorous scenes, and audiobook passages that should feel natural."
    ),
    VINDEMIATRIX(
            "Vindemiatrix",
            "Gentle",
            "Soft, mature, reassuring, and peaceful. Works well for reflective narration, emotional support, calm characters, healing scenes, and slow intimate passages."
    ),
    SADACHBIA(
            "Sadachbia",
            "Lively",
            "Animated, distinctive, theatrical, and expressive. Suitable for dramatic storytelling, colorful characters, energetic scenes, and narration with a strong personality."
    ),
    SADALTAGER(
            "Sadaltager",
            "Knowledgeable",
            "Wise, patient, explanatory, and composed. Works well for teacher-like narration, expert characters, thoughtful exposition, and passages that need calm authority."
    ),
    SULAFAT(
            "Sulafat",
            "Warm",
            "Warm, confident, supportive, and persuasive. Suitable for mentoring voices, emotional narration, encouraging passages, and content that should feel human and reassuring."
    );

    private final String key;
    private final String style;
    private final String styleDescription;

    SpeakerVoice(String voice, String style, String styleDescription) {
        this.key = voice;
        this.style = style;
        this.styleDescription = styleDescription;
    }

    public String getKey() {
        return key;
    }

    public String getStyle() {
        return style;
    }

    public String getStyleDescription() {
        return styleDescription;
    }

    @Override
    public String toString() {
        return "SpeakerVoice{" +
                "key='" + key + '\'' +
                ", style='" + style + '\'' +
                ", styleDescription='" + styleDescription + '\'' +
                '}';
    }
}