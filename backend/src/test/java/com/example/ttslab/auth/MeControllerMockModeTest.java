package com.example.ttslab.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"AUTH_MODE=mock", "ENVIRONMENT=feature", "MOCK_USER_ID=123", "MOCK_USER_EMAIL=test.user@example.com", "MOCK_USER_NAME=Test User", "MOCK_USER_ROLES=USER,ADMIN"})
@AutoConfigureMockMvc
class MeControllerMockModeTest {
    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("GIVEN mock auth WHEN requesting /api/me without login THEN request is unauthorized")
    void meRequiresLoginInMockMode() throws Exception {
        mockMvc.perform(get("/api/me"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GIVEN mock auth WHEN login creates session THEN /api/me returns stable response shape")
    void meReturnsMockUserAfterLogin() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/mock-login"))
            .andExpect(status().isOk())
            .andReturn()
            .getRequest()
            .getSession(false);

        mockMvc.perform(get("/api/me").session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("123"))
            .andExpect(jsonPath("$.email").value("test.user@example.com"))
            .andExpect(jsonPath("$.name").value("Test User"))
            .andExpect(jsonPath("$.authMode").value("mock"));
    }
}
