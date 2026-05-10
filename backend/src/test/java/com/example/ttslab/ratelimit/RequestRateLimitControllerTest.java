package com.example.ttslab.ratelimit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.prompts.ModelType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RequestRateLimitController.class)
@DisplayName("RequestRateLimitController")
class RequestRateLimitControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RequestRateLimitService requestRateLimitService;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @Test
    @WithMockUser
    @DisplayName("GET /api/request-limits/me returns summary for current user")
    void meReturnsCurrentUserSummary() throws Exception {
        CurrentUser user = new CurrentUser("user-123", "user@example.com", "Test User", List.of("USER"), "mock");
        when(currentUserResolver.resolve(any())).thenReturn(user);

        RequestRateLimitSummaryItem speechItem = new RequestRateLimitSummaryItem(
            ModelType.SPEECH_MODEL,
            100L,
            600L,
            500L,
            RequestRateLimitUnit.WORDS
        );
        RequestRateLimitSummaryItem textItem = new RequestRateLimitSummaryItem(
            ModelType.TEXT_MODEL,
            50L,
            1200L,
            1150L,
            RequestRateLimitUnit.WORDS
        );
        RequestRateLimitSummaryResponse response = new RequestRateLimitSummaryResponse(
            Instant.parse("2026-05-08T00:00:00Z"),
            12 * 60 * 60,
            List.of(speechItem, textItem)
        );

        when(requestRateLimitService.summary(user)).thenReturn(response);

        mockMvc.perform(get("/api/request-limits/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowResetAt").isNotEmpty())
            .andExpect(jsonPath("$.windowSeconds").value(12 * 60 * 60))
            .andExpect(jsonPath("$.limits", org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$.limits[0].modelType").value("SPEECH_MODEL"))
            .andExpect(jsonPath("$.limits[0].used").value(100))
            .andExpect(jsonPath("$.limits[0].limit").value(600))
            .andExpect(jsonPath("$.limits[0].remaining").value(500))
            .andExpect(jsonPath("$.limits[1].modelType").value("TEXT_MODEL"))
            .andExpect(jsonPath("$.limits[1].used").value(50))
            .andExpect(jsonPath("$.limits[1].limit").value(1200))
            .andExpect(jsonPath("$.limits[1].remaining").value(1150));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/request-limits/me includes unit in response")
    void meIncludesUnitInResponse() throws Exception {
        CurrentUser user = new CurrentUser("user-123", "user@example.com", "Test User", List.of("USER"), "mock");
        when(currentUserResolver.resolve(any())).thenReturn(user);

        List<RequestRateLimitSummaryItem> limits = List.of(
            new RequestRateLimitSummaryItem(
                ModelType.SPEECH_MODEL,
                0L,
                600L,
                600L,
                RequestRateLimitUnit.TOKENS
            ),
            new RequestRateLimitSummaryItem(
                ModelType.TEXT_MODEL,
                0L,
                1200L,
                1200L,
                RequestRateLimitUnit.TOKENS
            )
        );
        RequestRateLimitSummaryResponse response = new RequestRateLimitSummaryResponse(
            Instant.parse("2026-05-08T00:00:00Z"),
            12 * 60 * 60,
            limits
        );

        when(requestRateLimitService.summary(user)).thenReturn(response);

        mockMvc.perform(get("/api/request-limits/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.windowResetAt").isNotEmpty())
            .andExpect(jsonPath("$.limits[0].unit").value("TOKENS"))
            .andExpect(jsonPath("$.limits[1].unit").value("TOKENS"));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/request-limits/me handles empty limits list")
    void meHandlesEmptyLimitsList() throws Exception {
        CurrentUser user = new CurrentUser("user-123", "user@example.com", "Test User", List.of("USER"), "mock");
        when(currentUserResolver.resolve(any())).thenReturn(user);

        RequestRateLimitSummaryResponse response = new RequestRateLimitSummaryResponse(
            Instant.parse("2026-05-08T00:00:00Z"),
            12 * 60 * 60,
            List.of()
        );

        when(requestRateLimitService.summary(user)).thenReturn(response);

        mockMvc.perform(get("/api/request-limits/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limits", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/request-limits/me with zero usage")
    void meWithZeroUsage() throws Exception {
        CurrentUser user = new CurrentUser("user-456", "user456@example.com", "Another User", List.of("USER"), "mock");
        when(currentUserResolver.resolve(any())).thenReturn(user);

        List<RequestRateLimitSummaryItem> limits = List.of(
            new RequestRateLimitSummaryItem(
                ModelType.SPEECH_MODEL,
                0L,
                600L,
                600L,
                RequestRateLimitUnit.WORDS
            )
        );
        RequestRateLimitSummaryResponse response = new RequestRateLimitSummaryResponse(
            Instant.parse("2026-05-08T00:00:00Z"),
            12 * 60 * 60,
            limits
        );

        when(requestRateLimitService.summary(user)).thenReturn(response);

        mockMvc.perform(get("/api/request-limits/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limits[0].used").value(0))
            .andExpect(jsonPath("$.limits[0].remaining").value(600));
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/request-limits/me with full usage")
    void meWithFullUsage() throws Exception {
        CurrentUser user = new CurrentUser("user-789", "user789@example.com", "Third User", List.of("USER"), "mock");
        when(currentUserResolver.resolve(any())).thenReturn(user);

        List<RequestRateLimitSummaryItem> limits = List.of(
            new RequestRateLimitSummaryItem(
                ModelType.SPEECH_MODEL,
                600L,
                600L,
                0L,
                RequestRateLimitUnit.WORDS
            )
        );
        RequestRateLimitSummaryResponse response = new RequestRateLimitSummaryResponse(
            Instant.parse("2026-05-08T00:00:00Z"),
            12 * 60 * 60,
            limits
        );

        when(requestRateLimitService.summary(user)).thenReturn(response);

        mockMvc.perform(get("/api/request-limits/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limits[0].used").value(600))
            .andExpect(jsonPath("$.limits[0].remaining").value(0));
    }
}
