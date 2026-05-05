package com.example.ttslab;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.ai.model.chat=google-genai", "spring.autoconfigure.exclude=org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration"})
class TtsLabApplicationTests {

    @Test
    void contextLoads() {
    }
}
