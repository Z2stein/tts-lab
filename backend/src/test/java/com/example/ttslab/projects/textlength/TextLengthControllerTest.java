package com.example.ttslab.projects.textlength;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(TextLengthController.class)
class TextLengthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TextLengthService textLengthService;

    @Test
    void legacyPostTextLengthReturnsLength() throws Exception {
        when(textLengthService.countLength("abc")).thenReturn(3);

        mockMvc.perform(post("/api/text-length")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"abc\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"length\":3}"));
    }


    @Test
    void projectTextLengthCalculateReturnsLength() throws Exception {
        when(textLengthService.countLength("abc")).thenReturn(3);

        mockMvc.perform(post("/api/projects/text-length/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"abc\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"length\":3}"));
    }

    @Test
    void projectTextLengthCalculateReturnsZeroForEmptyText() throws Exception {
        when(textLengthService.countLength("")).thenReturn(0);

        mockMvc.perform(post("/api/projects/text-length/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"length\":0}"));
    }

    @Test
    void projectTextLengthCalculateSupportsUnicodeText() throws Exception {
        when(textLengthService.countLength("Grüße 🌍")).thenReturn(8);

        mockMvc.perform(post("/api/projects/text-length/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Grüße 🌍\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"length\":8}"));
    }

    @Test
    void projectTextLengthCalculateSupportsLargeInput() throws Exception {
        String largeText = "a".repeat(10_000);
        when(textLengthService.countLength(largeText)).thenReturn(10_000);

        mockMvc.perform(post("/api/projects/text-length/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"" + largeText + "\"}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"length\":10000}"));
    }

    @Test
    void projectTextLengthCalculateReturnsZeroWhenTextFieldMissing() throws Exception {
        when(textLengthService.countLength(isNull())).thenReturn(0);

        mockMvc.perform(post("/api/projects/text-length/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"length\":0}"));
    }

    @Test
    void projectTextLengthCalculateRejectsInvalidJsonPayload() throws Exception {
        mockMvc.perform(post("/api/projects/text-length/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"abc\""))
            .andExpect(status().isBadRequest());
    }
}
