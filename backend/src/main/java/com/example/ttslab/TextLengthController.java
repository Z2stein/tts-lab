package com.example.ttslab;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TextLengthController {

    private final TextLengthService textLengthService;

    public TextLengthController(TextLengthService textLengthService) {
        this.textLengthService = textLengthService;
    }

    @PostMapping("/text-length")
    public TextLengthResponse getTextLength(@RequestBody TextLengthRequest request) {
        return new TextLengthResponse(textLengthService.countLength(request.text()));
    }
}
