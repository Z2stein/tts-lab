package com.example.ttslab.auth;

import java.util.List;

public record CurrentUser(String id, String email, String name, List<String> roles, String authMode) {
}
