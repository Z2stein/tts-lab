package com.example.ttslab.audiobooks.workflow;

public enum SpeakerVoice {
    ZEPHYR("Zephyr", "Bright"),
    PUCK("Puck", "Upbeat"),
    CHARON("Charon", "Informative"),
    KORE("Kore", "Firm"),
    FENRIR("Fenrir", "Excitable"),
    LEDA("Leda", "Youthful"),
    ORUS("Orus", "Firm"),
    AOEDE("Aoede", "Breezy"),
    CALLIRRHOE("Callirrhoe", "Easy-going"),
    AUTONOE("Autonoe", "Bright"),
    ENCELADUS("Enceladus", "Breathy"),
    IAPETUS("Iapetus", "Clear"),
    UMBRIEL("Umbriel", "Easy-going"),
    ALGIEBA("Algieba", "Smooth"),
    DESPINA("Despina", "Smooth"),
    ERINOME("Erinome", "Clear"),
    ALGENIB("Algenib", "Gravelly"),
    RASALGETHI("Rasalgethi", "Informative"),
    LAOMEDEIA("Laomedeia", "Upbeat"),
    ACHERNAR("Achernar", "Soft"),
    ALNILAM("Alnilam", "Firm"),
    SCHEDAR("Schedar", "Even"),
    GACRUX("Gacrux", "Mature"),
    PULCHERRIMA("Pulcherrima", "Forward"),
    ACHIRD("Achird", "Friendly"),
    ZUBENELGENUBI("Zubenelgenubi", "Casual"),
    VINDEMIATRIX("Vindemiatrix", "Gentle"),
    SADACHBIA("Sadachbia", "Lively"),
    SADALTAGER("Sadaltager", "Knowledgeable"),
    SULAFAT("Sulafat", "Warm");

    private final String key;
    private final String style;

    SpeakerVoice(String voice, String style) {
        this.key = voice;
        this.style = style;
    }

    public String getKey() {
        return key;
    }

    public String getStyle() {
        return style;
    }
}
