package com.example.ttslab.projects.textlength;

import org.springframework.stereotype.Service;

@Service
public class TextLengthService {

    public int countLength(String text) {
        return text == null ? 0 : text.length();
    }
}
