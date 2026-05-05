package com.example.ttslab.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"AUTH_MODE=mock", "ENVIRONMENT=feature", "MOCK_USER_ID=123", "MOCK_USER_EMAIL=test.user@example.com", "MOCK_USER_NAME=Test User", "MOCK_USER_ROLES=USER,ADMIN", "spring.ai.model.chat=google-genai", "spring.autoconfigure.exclude=org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration"})
@AutoConfigureMockMvc
class MeControllerMockModeTest {
    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("GIVEN mock auth WHEN requesting /api/me THEN stable response shape is returned")
    void meReturnsMockUser() throws Exception {
        mockMvc.perform(get("/api/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("123"))
            .andExpect(jsonPath("$.email").value("test.user@example.com"))
            .andExpect(jsonPath("$.name").value("Test User"))
            .andExpect(jsonPath("$.authMode").value("mock"));
    }
}
