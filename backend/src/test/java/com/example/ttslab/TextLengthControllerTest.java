package com.example.ttslab;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
}
