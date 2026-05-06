package com.example.ttslab.projects.ttsworkbench;

import com.example.ttslab.chat.ChatRequest;
import com.example.ttslab.chat.ChatResponse;
import com.example.ttslab.chat.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TtsWorkbenchServiceTest {

    @Test
    void mockProviderReturnsDeterministicSpeakersWithoutCallingChatProvider() {
        ChatService chatService = mock(ChatService.class);
        TtsWorkbenchService service = new TtsWorkbenchService(chatService, new ObjectMapper(), "mock");

        SpeakerVoiceAnalysisResponse response = service.analyze("Alice: Hello\nBob: Hi");

        assertEquals(2, response.speakers().size());
        assertEquals("Alice", response.speakers().get(0).speakerName());
        assertEquals("Bob", response.speakers().get(1).speakerName());
        verify(chatService, never()).ask(any(ChatRequest.class));
    }

    @Test
    void geminiProviderUsesChatServiceAndParsesJson() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.ask(any(ChatRequest.class))).thenReturn(new ChatResponse("""
            {"speakers":[{"speakerName":"Narrator","roleDescription":"Guides the scene","voiceSuggestion":"Warm voice"}]}
            """, "c-1"));
        TtsWorkbenchService service = new TtsWorkbenchService(chatService, new ObjectMapper(), "gemini");

        SpeakerVoiceAnalysisResponse response = service.analyze("Once upon a time");

        assertEquals(1, response.speakers().size());
        assertEquals("Narrator", response.speakers().getFirst().speakerName());
        assertEquals("Warm voice", response.speakers().getFirst().voiceSuggestion());
    }

    @Test
    void geminiProviderFallsBackToMockAnalysisForInvalidModelOutput() {
        ChatService chatService = mock(ChatService.class);
        when(chatService.ask(any(ChatRequest.class))).thenReturn(new ChatResponse("not-json", "c-1"));
        TtsWorkbenchService service = new TtsWorkbenchService(chatService, new ObjectMapper(), "gemini");

        SpeakerVoiceAnalysisResponse response = service.analyze("Alice: Hello");

        assertEquals(1, response.speakers().size());
        assertEquals("Alice", response.speakers().getFirst().speakerName());
        assertEquals("Detected dialogue speaker", response.speakers().getFirst().roleDescription());
    }

    @Test
    void blankDialogueReturnsNoSpeakers() {
        ChatService chatService = mock(ChatService.class);
        TtsWorkbenchService service = new TtsWorkbenchService(chatService, new ObjectMapper(), "mock");

        SpeakerVoiceAnalysisResponse response = service.analyze("   ");

        assertEquals(0, response.speakers().size());
        verify(chatService, never()).ask(any(ChatRequest.class));
    }
}
