package com.example.ttslab.chat;

import static com.example.ttslab.contract.OpenApiContractAssertions.assertInteractionMatchesContract;
import static com.example.ttslab.contract.OpenApiContractAssertions.assertResponseMatchesContract;
import static com.example.ttslab.contract.TestContracts.readText;
import com.example.ttslab.auth.CurrentUser;
import com.example.ttslab.config.ChatbotProperties;
import com.example.ttslab.prompts.CurrentUserResolver;
import com.example.ttslab.prompts.PromptHistoryService;
import com.example.ttslab.ratelimit.RequestRateLimitResult;
import com.example.ttslab.ratelimit.RequestRateLimitService;
import com.example.ttslab.ratelimit.RequestRateLimitUnit;
import com.example.ttslab.ratelimit.RequestUsageMeasurer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private CurrentUserResolver currentUserResolver;

    @MockBean
    private PromptHistoryService promptHistoryService;

    @MockBean
    private RequestRateLimitService requestRateLimitService;

    @MockBean
    private RequestUsageMeasurer requestUsageMeasurer;

    @MockBean
    private ChatbotProperties chatbotProperties;


    @BeforeEach
    void setupRateLimitDefaults() {
        when(currentUserResolver.resolve(any())).thenReturn(new CurrentUser("u1", "u1@example.com", "User One", java.util.List.of("USER"), "mock"));
        when(chatbotProperties.provider()).thenReturn("mock");
        when(requestRateLimitService.unit()).thenReturn(RequestRateLimitUnit.WORDS);
        when(requestUsageMeasurer.measure(any(), eq(RequestRateLimitUnit.WORDS))).thenReturn(1L);
        when(requestRateLimitService.checkAndConsume(any(), eq(com.example.ttslab.prompts.ModelType.TEXT_MODEL), eq(1L)))
            .thenReturn(new RequestRateLimitResult(com.example.ttslab.prompts.ModelType.TEXT_MODEL, true, 1, 100, 99, 1, 0, 1, RequestRateLimitUnit.WORDS));
    }

    @Test
    void validMessageReturnsSuccess() throws Exception {
        when(chatService.ask(any())).thenReturn(new ChatResponse("hello", "c-1"));

        MvcResult result = mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("chat/success/request.json")))
            .andExpect(status().isOk())
            .andExpect(content().json(readText("chat/success/response.json")))
            .andReturn();

        verify(promptHistoryService).record(any(), eq(com.example.ttslab.prompts.ModelType.TEXT_MODEL), eq("mock"), eq("hi"), eq(com.example.ttslab.prompts.PromptRequestStatus.SUCCESS));
        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void blankMessageReturnsBadRequest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("chat/validation-failed/request.json")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").value("The request is invalid. Please check your input and try again."))
            .andExpect(jsonPath("$.requestId").exists())
            .andReturn();

        assertResponseMatchesContract("/api/chat", com.atlassian.oai.validator.model.Request.Method.POST, result.getResponse());
    }

    @Test
    void tooLargeMessageReturnsBadRequest() throws Exception {
        String tooLarge = "a".repeat(2001);
        MvcResult result = mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + tooLarge + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.message").value("The request is invalid. Please check your input and try again."))
            .andExpect(jsonPath("$.requestId").exists())
            .andReturn();

        assertResponseMatchesContract("/api/chat", com.atlassian.oai.validator.model.Request.Method.POST, result.getResponse());
    }

    @Test
    void providerErrorReturnsControlledError() throws Exception {
        when(chatService.ask(any())).thenThrow(new ChatProviderException("x", new RuntimeException("boom"), false));

        MvcResult result = mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("chat/provider-unavailable/request.json")))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.status").value(502))
            .andExpect(jsonPath("$.code").value("CHAT_PROVIDER_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("Chat provider is currently unavailable. Please try again later."))
            .andExpect(jsonPath("$.requestId").exists())
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }
    @Test
    void rateLimitExceededReturns429WithRetryAfter() throws Exception {
        when(requestRateLimitService.checkAndConsume(any(), eq(com.example.ttslab.prompts.ModelType.TEXT_MODEL), eq(1L)))
            .thenReturn(new RequestRateLimitResult(com.example.ttslab.prompts.ModelType.TEXT_MODEL, false, 100, 100, 0, 1, 123, 1, RequestRateLimitUnit.WORDS));

        MvcResult result = mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("chat/rate-limited/request.json")))
            .andExpect(status().isTooManyRequests())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "123"))
            .andExpect(jsonPath("$.status").value(429))
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.message").value("Usage limit exceeded. Please try again later."))
            .andExpect(jsonPath("$.requestId").exists())
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

    @Test
    void unexpectedErrorReturnsStructuredErrorResponse() throws Exception {
        when(chatService.ask(any())).thenThrow(new IllegalStateException("boom"));

        MvcResult result = mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(readText("chat/internal-error/request.json")))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("An unexpected server error occurred. Please try again later."))
            .andExpect(jsonPath("$.requestId").exists())
            .andReturn();

        assertInteractionMatchesContract(result.getRequest(), result.getResponse());
    }

}
