package com.example.ttslab.prompts;

import static com.example.ttslab.contract.OpenApiContractAssertions.assertInteractionMatchesContract;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ttslab.auth.CurrentUser;
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
@WebMvcTest(PromptHistoryController.class)
class PromptHistoryControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @MockBean
    private PromptHistoryService promptHistoryService;

    private final CurrentUser user = new CurrentUser("user-1", "user1@example.com", "User One", List.of("USER"), "mock");

    @BeforeEach
    void setup() {
        when(currentUserResolver.resolve(any())).thenReturn(user);
    }

    @Test
    void returnsOnlyCurrentUsersPromptHistory() throws Exception {
        when(promptHistoryService.findForUser(user, null)).thenReturn(List.of(
            new PromptHistoryItem(1L, "user-1", "user1@example.com", ModelType.TEXT_MODEL, "mock", "Hello", PromptRequestStatus.SUCCESS, Instant.parse("2026-05-07T12:00:00Z"))
        ));

        MvcResult result = mockMvc.perform(get("/api/prompts/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].userId").value("user-1"))
            .andExpect(jsonPath("$.items[0].promptText").value("Hello"))
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void passesModelTypeFilterToService() throws Exception {
        when(promptHistoryService.findForUser(user, ModelType.SPEECH_MODEL)).thenReturn(List.of());

        MvcResult result = mockMvc.perform(get("/api/prompts/history?modelType=SPEECH_MODEL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }
}
