package com.example.ttslab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TextLengthController.class)
class TextLengthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TextLengthService textLengthService;

    @Test
    void postTextLengthReturnsLength() throws Exception {
        when(textLengthService.countLength("abc")).thenReturn(3);

        mockMvc.perform(post("/api/text-length")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"abc\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"length\":3}"));
    }

    @Test
    void postTextLengthReturnsZeroForEmptyText() throws Exception {
        when(textLengthService.countLength("")).thenReturn(0);

        mockMvc.perform(post("/api/text-length")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"length\":0}"));
    }

    @Test
    void postTextLengthSupportsUnicodeText() throws Exception {
        when(textLengthService.countLength("Grüße 🌍")).thenReturn(8);

        mockMvc.perform(post("/api/text-length")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Grüße 🌍\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"length\":8}"));
    }

    @Test
    void postTextLengthSupportsLargeInput() throws Exception {
        String largeText = "a".repeat(10_000);
        when(textLengthService.countLength(startsWith("aaaa"))).thenReturn(10_000);

        mockMvc.perform(post("/api/text-length")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + largeText + "\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"length\":10000}"));
    }

    @Test
    void postTextLengthReturnsZeroWhenTextFieldMissing() throws Exception {
        when(textLengthService.countLength(isNull())).thenReturn(0);

        mockMvc.perform(post("/api/text-length")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"length\":0}"));
    }

    @Test
    void postTextLengthRejectsInvalidJsonPayload() throws Exception {
        mockMvc.perform(post("/api/text-length")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"abc\""))
            .andExpect(status().isBadRequest());
    }
}
