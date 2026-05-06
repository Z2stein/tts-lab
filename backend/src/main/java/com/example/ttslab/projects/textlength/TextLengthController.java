package com.example.ttslab.projects.textlength;

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

    @PostMapping("/projects/text-length/calculate")
    public TextLengthResponse calculateTextLength(@RequestBody TextLengthRequest request) {
        int inputLength = request.text() == null ? 0 : request.text().length();
        log.info("POST /api/projects/text-length/calculate called (inputLength={})", inputLength);

        int length = textLengthService.countLength(request.text());
        log.info("POST /api/projects/text-length/calculate succeeded (resultLength={})", length);
        return new TextLengthResponse(length);
    }

    /**
     * @deprecated Use {@code POST /api/projects/text-length/calculate} instead.
     */
    @Deprecated(forRemoval = false)
    @PostMapping("/text-length")
    public TextLengthResponse getTextLength(@RequestBody TextLengthRequest request) {
        log.info("Deprecated POST /api/text-length called; forwarding to project endpoint handler");
        return calculateTextLength(request);
    }
}
