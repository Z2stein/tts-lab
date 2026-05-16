package com.example.ttslab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TtsLabApplication {

    public static void main(String[] args) {
        SpringApplication.run(TtsLabApplication.class, args);
    }
}
