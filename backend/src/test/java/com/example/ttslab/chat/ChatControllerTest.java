package com.example.ttslab.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private ChatRateLimitService chatRateLimitService;

    @MockBean
    private ChatRateLimitProperties chatRateLimitProperties;

    @MockBean
    private ChatUsageIdentityResolver chatUsageIdentityResolver;


    @BeforeEach
    void setupRateLimitDefaults() {
        when(chatRateLimitProperties.idHeader()).thenReturn("X-User-Id");
        when(chatRateLimitProperties.window()).thenReturn(java.time.Duration.ofHours(1));
        when(chatRateLimitProperties.maxRequests()).thenReturn(100);
        when(chatUsageIdentityResolver.resolve(any(), any(), any())).thenReturn("u1");
        when(chatRateLimitService.checkAndConsume("u1")).thenReturn(new ChatRateLimitResult(true, 1, 0, 1));
    }

    @Test
    void validMessageReturnsSuccess() throws Exception {
        when(chatUsageIdentityResolver.resolve(any(), any(), any())).thenReturn("u1");
        when(chatRateLimitService.checkAndConsume("u1")).thenReturn(new ChatRateLimitResult(true, 1, 0, 1));
        when(chatService.ask(any())).thenReturn(new ChatResponse("hello", "c-1"));

        when(chatRateLimitProperties.idHeader()).thenReturn("X-User-Id");
        when(chatUsageIdentityResolver.resolve(any(), any(), any())).thenReturn("u1");
        when(chatRateLimitService.checkAndConsume("u1")).thenReturn(new ChatRateLimitResult(true, 1, 0, 1));

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"answer\":\"hello\",\"conversationId\":\"c-1\"}"));
    }

    @Test
    void blankMessageReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{\"error\":\"Invalid chat request.\"}"));
    }

    @Test
    void tooLargeMessageReturnsBadRequest() throws Exception {
        String tooLarge = "a".repeat(2001);
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + tooLarge + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{\"error\":\"Invalid chat request.\"}"));
    }

    @Test
    void providerErrorReturnsControlledError() throws Exception {
        when(chatService.ask(any())).thenThrow(new ChatProviderException("x", new RuntimeException("boom"), false));

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\"}"))
            .andExpect(status().isBadGateway())
            .andExpect(content().json("{\"error\":\"Chat provider is currently unavailable.\"}"));
    }
    @Test
    void rateLimitExceededReturns429WithRetryAfter() throws Exception {
        when(chatRateLimitProperties.idHeader()).thenReturn("X-User-Id");
        when(chatRateLimitProperties.window()).thenReturn(java.time.Duration.ofHours(1));
        when(chatRateLimitProperties.maxRequests()).thenReturn(100);
        when(chatUsageIdentityResolver.resolve(any(), any(), any())).thenReturn("u1");
        when(chatRateLimitService.checkAndConsume("u1")).thenReturn(new ChatRateLimitResult(false, 101, 123, 1));

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"hi\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "123"))
            .andExpect(content().json("{\"error\":\"RATE_LIMIT_EXCEEDED\"}"));
    }

}
