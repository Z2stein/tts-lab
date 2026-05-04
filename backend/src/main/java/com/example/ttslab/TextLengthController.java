package com.example.ttslab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TextLengthController {

    private static final Logger log = LoggerFactory.getLogger(TextLengthController.class);
    private final TextLengthService textLengthService;

    public TextLengthController(TextLengthService textLengthService) {
        this.textLengthService = textLengthService;
    }

    @PostMapping("/text-length")
    public TextLengthResponse getTextLength(@RequestBody TextLengthRequest request) {
        int inputLength = request.text() == null ? 0 : request.text().length();
        log.info("POST /api/text-length called (inputLength={})", inputLength);

        int length = textLengthService.countLength(request.text());
        log.info("POST /api/text-length succeeded (resultLength={})", length);
        return new TextLengthResponse(length);
    }
}
