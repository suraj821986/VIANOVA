package com.example.vianova;

import com.example.vianova.ChatbotController.ChatbotRequest;
import com.example.vianova.TravelChatService.ChatbotReply;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiToolCallingService {

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${openai.chat.model:gpt-4.1-mini}")
    private String model;

    public ChatbotReply chatWithTools(ChatbotRequest request, TravelChatService toolExecutor) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt()));
            messages.add(Map.of("role", "user", "content", request.message()));

            Map<String, Object> firstResponse = sendChatCompletion(messages, buildTools());
            Map<String, Object> assistantMessage = firstChoiceMessage(firstResponse);
            List<Map<String, Object>> toolCalls = toolCalls(assistantMessage);

            if (toolCalls.isEmpty()) {
                String content = stringValue(assistantMessage.get("content"));
                if (!content.isBlank()) {
                    return new ChatbotReply(content, "openai-tool-calling", "llm_direct_response", defaultSuggestions());
                }
                return null;
            }

            messages.add(assistantMessage);
            for (Map<String, Object> toolCall : toolCalls) {
                Map<?, ?> function = mapValue(toolCall.get("function"));
                String toolName = stringValue(function.get("name"));
                Map<String, Object> arguments = parseArguments(stringValue(function.get("arguments")));
                String toolOutput = toolExecutor.executeTool(toolName, arguments, request);
                messages.add(Map.of("role", "tool", "tool_call_id", stringValue(toolCall.get("id")), "content", toolOutput));
            }

            Map<String, Object> secondResponse = sendChatCompletion(messages, buildTools());
            Map<String, Object> finalMessage = firstChoiceMessage(secondResponse);
            String finalContent = stringValue(finalMessage.get("content"));
            if (finalContent.isBlank()) {
                return null;
            }

            return new ChatbotReply(finalContent, "openai-tool-calling", "llm_tool_response", defaultSuggestions());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> sendChatCompletion(List<Map<String, Object>> messages, List<Map<String, Object>> tools) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("tools", tools);
        payload.put("tool_choice", "auto");

        HttpRequest request = HttpRequest.newBuilder(URI.create(trimTrailingSlash(baseUrl) + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI API returned " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readValue(response.body(), new TypeReference<>() {
        });
    }

    private List<Map<String, Object>> buildTools() {
        return List.of(
                functionTool("get_driver_rides", "Fetch previous rides for the logged-in driver.", Map.of(
                        "type", "object",
                        "properties", Map.of("limit", Map.of("type", "integer", "description", "How many rides to return, up to 20.")),
                        "required", List.of("limit")
                )),
                functionTool("get_rider_saved_cards", "Fetch saved cards for the logged-in rider.", Map.of("type", "object", "properties", Map.of())),
                functionTool("get_rider_payment_options", "Fetch payment options for the logged-in rider.", Map.of("type", "object", "properties", Map.of())),
                functionTool("get_driver_cars", "Fetch registered cars for the logged-in driver.", Map.of("type", "object", "properties", Map.of())),
                functionTool("get_driver_ratings", "Fetch ratings and strengths for the logged-in driver.", Map.of("type", "object", "properties", Map.of())),
                functionTool("get_trip_status", "Fetch live trip status by trip ID.", Map.of(
                        "type", "object",
                        "properties", Map.of("tripId", Map.of("type", "string", "description", "Trip ID like TRIP-1234ABCD")),
                        "required", List.of("tripId")
                ))
        );
    }

    private Map<String, Object> functionTool(String name, String description, Map<String, Object> parameters) {
        return Map.of("type", "function", "function", Map.of("name", name, "description", description, "parameters", parameters));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstChoiceMessage(Map<String, Object> response) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            return Map.of();
        }
        return mapValue(choices.get(0).get("message"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toolCalls(Map<String, Object> message) {
        Object raw = message.get("tool_calls");
        return raw instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private Map<String, Object> parseArguments(String arguments) {
        try {
            if (arguments == null || arguments.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(arguments, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String systemPrompt() {
        return """
                You are a travel app assistant for Vianova.
                Use tools whenever the user asks for account-specific or trip-specific data.
                Prefer tools over guessing.
                If the required user context is missing, explain that login is required.
                Keep answers concise and directly useful.
                Supported tool domains: driver rides, rider saved cards, rider payment options, driver cars, driver ratings, and trip status.
                """;
    }

    private List<String> defaultSuggestions() {
        return List.of("show my last 10 rides", "show my saved cards", "show my payment options", "show my ratings");
    }
}
