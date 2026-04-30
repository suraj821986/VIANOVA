package com.example.vianova;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatbotController {

    private final TravelChatService travelChatService;

    public ChatbotController(TravelChatService travelChatService) {
        this.travelChatService = travelChatService;
    }

    @PostMapping("/chatbot/message")
    public ResponseEntity<ChatbotResponse> sendMessage(@RequestBody ChatbotRequest request) {
        return ResponseEntity.ok(travelChatService.chat(request));
    }

    public record ChatbotRequest(String message, String userId, String userType, String page) {
    }

    public record ChatbotResponse(String response, String source, String timestamp, java.util.List<String> suggestions, String intent, java.util.Map<String, Object> payload) {
    }
}
