package com.example.vianova;

import com.example.vianova.ChatbotController.ChatbotRequest;
import com.example.vianova.TravelChatService.ChatbotReply;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OpenAiToolCallingService.class);

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
            log.info("OpenAI integration skipped for chatbot request: openai.api.key is not configured");
            return null;
        }

        try {
            log.info("OpenAI integration enabled for chatbot request: model={}, baseUrl={}, userType={}, page={}, messageLength={}",
                    model, baseUrl, request.userType(), request.page(), request.message().length());

            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt()));
            messages.add(Map.of("role", "system", "content", sessionContextPrompt(request)));
            messages.add(Map.of("role", "user", "content", request.message()));

            log.info("OpenAI chat completion request started: phase=tool-selection, model={}", model);
            Map<String, Object> firstResponse = sendChatCompletion(messages, buildTools());
            Map<String, Object> assistantMessage = firstChoiceMessage(firstResponse);
            List<Map<String, Object>> toolCalls = toolCalls(assistantMessage);
            log.info("OpenAI chat completion response received: phase=tool-selection, toolCallCount={}", toolCalls.size());

            if (toolCalls.isEmpty()) {
                String content = stringValue(assistantMessage.get("content"));
                if (looksLikeRideEstimateRequest(request.message())) {
                    log.warn("OpenAI returned a direct response for ride estimate request; falling back to deterministic router");
                    return null;
                }
                if (!content.isBlank()) {
                    log.info("OpenAI integration completed: responseType=direct, contentLength={}", content.length());
                    return new ChatbotReply(content, "openai-tool-calling", "llm_direct_response", defaultSuggestions());
                }
                log.warn("OpenAI integration returned no tool calls and empty content; falling back to deterministic router");
                return null;
            }

            messages.add(assistantMessage);
            Map<String, Object> responsePayload = Map.of();
            for (Map<String, Object> toolCall : toolCalls) {
                Map<?, ?> function = mapValue(toolCall.get("function"));
                String toolName = stringValue(function.get("name"));
                Map<String, Object> arguments = parseArguments(stringValue(function.get("arguments")));
                log.info("OpenAI requested Vianova tool: name={}, argumentKeys={}", toolName, arguments.keySet());
                Map<String, Object> toolResult = toolExecutor.executeToolPayload(toolName, arguments, request);
                String toolOutput = String.valueOf(toolResult);
                if ("get_ride_estimate".equals(toolName) && "ok".equals(toolResult.get("status"))) {
                    responsePayload = Map.of("rideEstimate", toolResult);
                }
                log.info("Vianova tool completed for OpenAI: name={}, outputLength={}", toolName, toolOutput.length());
                messages.add(Map.of("role", "tool", "tool_call_id", stringValue(toolCall.get("id")), "content", toolOutput));
            }

            log.info("OpenAI chat completion request started: phase=final-response, model={}", model);
            Map<String, Object> secondResponse = sendChatCompletion(messages, buildTools());
            Map<String, Object> finalMessage = firstChoiceMessage(secondResponse);
            String finalContent = stringValue(finalMessage.get("content"));
            if (finalContent.isBlank()) {
                log.warn("OpenAI integration returned empty final content; falling back to deterministic router");
                return null;
            }

            log.info("OpenAI integration completed: responseType=tool_response, contentLength={}", finalContent.length());
            return new ChatbotReply(finalContent, "openai-tool-calling", "llm_tool_response", defaultSuggestions(), responsePayload);
        } catch (Exception ex) {
            log.warn("OpenAI integration failed; falling back to deterministic router: {}", ex.getMessage());
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
                functionTool("get_rider_rides", "Fetch recent ride history for the logged-in rider.", Map.of(
                        "type", "object",
                        "properties", Map.of("limit", Map.of("type", "integer", "description", "How many rider rides to return, up to 20.")),
                        "required", List.of("limit")
                )),
                functionTool("get_rider_saved_cards", "Fetch saved cards for the logged-in rider.", Map.of("type", "object", "properties", Map.of())),
                functionTool("get_rider_payment_options", "Fetch payment options for the logged-in rider.", Map.of("type", "object", "properties", Map.of())),
                functionTool("get_driver_cars", "Fetch registered cars for the logged-in driver.", Map.of("type", "object", "properties", Map.of())),
                functionTool("get_driver_ratings", "Fetch ratings and strengths for the logged-in driver.", Map.of("type", "object", "properties", Map.of())),
                functionTool("get_ride_estimate", "Get ride estimate and available ride/driver options using the existing /rides/estimate endpoint. Use this for booking-style or fare/ETA requests with source and destination; this estimates options only and does not create a ride request.", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "source", Map.of("type", "string", "description", "Pickup location selected or typed by the user."),
                                "destination", Map.of("type", "string", "description", "Drop-off location selected or typed by the user."),
                                "departureTime", Map.of("type", "string", "description", "Optional ISO local datetime like 2026-04-30T20:15. Use now/asap only if the user asks for it.")
                        ),
                        "required", List.of("source", "destination")
                )),
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
                If the current session says the required user is logged in, do not ask them to log in again; call the relevant tool.
                If the current session says the required user is not logged in, explain that login is required.
                When the user asks to book, schedule, find, estimate, or get a ride from one place to another, call get_ride_estimate. Do not say you cannot book rides; return the estimate and available ride options from the tool.
                get_ride_estimate does not finalize or create a ride request; it only returns /rides/estimate results.
                Keep answers concise and directly useful.
                Supported tool domains: ride estimates, rider ride history, driver rides, rider saved cards, rider payment options, driver cars, driver ratings, and trip status.
                """;
    }

    private String sessionContextPrompt(ChatbotRequest request) {
        boolean loggedIn = request.userId() != null && !request.userId().isBlank();
        String userType = request.userType() == null || request.userType().isBlank() ? "NONE" : request.userType();
        String page = request.page() == null || request.page().isBlank() ? "unknown" : request.page();

        return """
                Current Vianova session context:
                - page: %s
                - userType: %s
                - loggedIn: %s

                Tool guidance:
                - For "saved cards", call get_rider_saved_cards when userType is RIDER and loggedIn is true.
                - For "payment options", call get_rider_payment_options when userType is RIDER and loggedIn is true.
                - For "my rides", "last rides", or ride history, call get_rider_rides when userType is RIDER and loggedIn is true.
                - For "my cars", call get_driver_cars when userType is DRIVER and loggedIn is true.
                - For "my ratings", call get_driver_ratings when userType is DRIVER and loggedIn is true.
                - For "my rides" or ride history, call get_driver_rides when userType is DRIVER and loggedIn is true.
                - For trip tracking, call get_trip_status when a trip ID is provided.
                - For booking-style messages such as "book a ride from JFK Airport to Times Square" or "estimate fare from X to Y", call get_ride_estimate. This tool does not require login because it only returns estimates and ride options.
                """.formatted(page, userType, loggedIn);
    }

    private List<String> defaultSuggestions() {
        return List.of("book a ride from JFK Airport to Times Square", "show my saved cards", "show my payment options", "track trip TRIP-1234ABCD");
    }

    private boolean looksLikeRideEstimateRequest(String value) {
        String text = value == null ? "" : value.toLowerCase();
        return (text.contains("book") || text.contains("schedule") || text.contains("estimate") || text.contains("fare") || text.contains("ride"))
                && text.contains("from")
                && text.contains("to");
    }
}
