package com.example.ttslab.ratelimit;

import static com.example.ttslab.contract.OpenApiContractAssertions.assertInteractionMatchesContract;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.prompts.CurrentUserResolver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(RequestRateLimitController.class)
class RequestRateLimitControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @MockBean
    private RequestRateLimitService requestRateLimitService;

    private final CurrentUser user = new CurrentUser("user-1", "user1@example.com", "User One", List.of("USER"), "mock");

    @BeforeEach
    void setup() {
        when(currentUserResolver.resolve(any())).thenReturn(user);
    }

    @Test
    void returnsCurrentUserRequestLimits() throws Exception {
        when(requestRateLimitService.summary(user)).thenReturn(new RequestRateLimitSummaryResponse(
            Instant.parse("2026-05-09T12:00:00Z"),
            43200,
            List.of(
                new RequestRateLimitSummaryItem(com.example.ttslab.prompts.ModelType.TEXT_MODEL, 10, 1800, 1790, RequestRateLimitUnit.WORDS),
                new RequestRateLimitSummaryItem(com.example.ttslab.prompts.ModelType.SPEECH_MODEL, 0, 600, 600, RequestRateLimitUnit.WORDS)
            )
        ));

        MvcResult result = mockMvc.perform(get("/api/request-limits/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limits[0].modelType").value("TEXT_MODEL"))
            .andExpect(jsonPath("$.limits[0].remaining").value(1790))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }
}
