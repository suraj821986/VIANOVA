package com.example.vianova;

import com.example.vianova.ChatbotController.ChatbotRequest;
import com.example.vianova.ChatbotController.ChatbotResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TravelChatService {

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b(\\d{1,2})\\b");
    private static final Pattern TRIP_ID_PATTERN = Pattern.compile("\\b(TRIP-[A-Z0-9-]+)\\b", Pattern.CASE_INSENSITIVE);

    private final DriverDashboardController driverDashboardController;
    private final RiderProfileController riderProfileController;
    private final RideEstimateController rideEstimateController;
    private final OpenAiToolCallingService openAiToolCallingService;

    public TravelChatService(DriverDashboardController driverDashboardController,
                             RiderProfileController riderProfileController,
                             RideEstimateController rideEstimateController,
                             OpenAiToolCallingService openAiToolCallingService) {
        this.driverDashboardController = driverDashboardController;
        this.riderProfileController = riderProfileController;
        this.rideEstimateController = rideEstimateController;
        this.openAiToolCallingService = openAiToolCallingService;
    }

    public ChatbotResponse chat(ChatbotRequest request) {
        ChatbotRequest safeRequest = new ChatbotRequest(
                request == null || request.message() == null ? "" : request.message().trim(),
                request == null || request.userId() == null ? "" : request.userId().trim(),
                request == null || request.userType() == null ? "" : request.userType().trim().toUpperCase(),
                request == null || request.page() == null ? "" : request.page().trim().toLowerCase()
        );

        ChatbotReply llmReply = openAiToolCallingService.chatWithTools(safeRequest, this);
        if (llmReply != null) {
            return toResponse(llmReply);
        }
        return toResponse(deterministicReply(safeRequest));
    }

    public ChatbotReply deterministicReply(ChatbotRequest request) {
        String message = request.message();
        if (message.isBlank()) {
            return new ChatbotReply(
                    "Try a travel command like 'show my saved cards', 'show my ratings', 'track trip TRIP-1234', or 'give me last 10 rides'.",
                    "deterministic-router",
                    "help",
                    defaultSuggestions()
            );
        }

        String normalizedMessage = message.toLowerCase();
        if (isTripTrackingIntent(normalizedMessage)) {
            return handleTripTracking(message);
        }
        if (isRideHistoryIntent(normalizedMessage)) {
            return handleRideHistory(message, request.userType(), request.userId());
        }
        if (isSavedCardsIntent(normalizedMessage)) {
            return handleSavedCards(request.userType(), request.userId());
        }
        if (isPaymentIntent(normalizedMessage)) {
            return handlePayments(request.userType(), request.userId());
        }
        if (isCarsIntent(normalizedMessage)) {
            return handleCars(request.userType(), request.userId());
        }
        if (isRatingsIntent(normalizedMessage)) {
            return handleRatings(request.userType(), request.userId());
        }

        return new ChatbotReply(
                "I could not match that request to a linked travel command yet. Supported commands currently cover ride history, trip tracking, saved cards, payment options, driver cars, and driver ratings.",
                "deterministic-router",
                "fallback",
                defaultSuggestions()
        );
    }

    public String executeTool(String toolName, Map<String, Object> arguments, ChatbotRequest request) {
        return switch (toolName) {
            case "get_driver_rides" -> stringifyBody(executeDriverRides(arguments, request));
            case "get_rider_saved_cards" -> stringifyBody(executeSavedCards(request));
            case "get_rider_payment_options" -> stringifyBody(executePayments(request));
            case "get_driver_cars" -> stringifyBody(executeCars(request));
            case "get_driver_ratings" -> stringifyBody(executeRatings(request));
            case "get_trip_status" -> stringifyBody(executeTripStatus(arguments));
            default -> stringifyBody(Map.of("status", "error", "message", "Unsupported tool: " + toolName));
        };
    }

    private Map<String, Object> executeDriverRides(Map<String, Object> arguments, ChatbotRequest request) {
        if (!"DRIVER".equals(request.userType()) || !isValidUuid(request.userId())) {
            return Map.of("status", "error", "message", "Driver login is required");
        }
        ResponseEntity<?> response = driverDashboardController.previousRides(request.userId());
        if (!response.getStatusCode().is2xxSuccessful()) {
            return Map.of("status", "error", "message", stringifyBody(response.getBody()));
        }
        int limit = arguments.get("limit") instanceof Number number ? Math.max(1, Math.min(20, number.intValue())) : 5;
        Map<?, ?> body = bodyAsMap(response.getBody());
        List<?> rides = listValue(body.get("rides"));
        List<?> sliced = rides.subList(0, Math.min(limit, rides.size()));
        return Map.of("status", "ok", "count", sliced.size(), "rides", sliced);
    }

    private Map<String, Object> executeSavedCards(ChatbotRequest request) {
        if (!"RIDER".equals(request.userType()) || !isValidUuid(request.userId())) {
            return Map.of("status", "error", "message", "Rider login is required");
        }
        ResponseEntity<?> response = riderProfileController.getSavedCards(request.userId());
        if (!response.getStatusCode().is2xxSuccessful()) {
            return Map.of("status", "error", "message", stringifyBody(response.getBody()));
        }
        return Map.of("status", "ok", "savedCards", listValue(bodyAsMap(response.getBody()).get("savedCards")));
    }

    private Map<String, Object> executePayments(ChatbotRequest request) {
        if (!"RIDER".equals(request.userType()) || request.userId().isBlank()) {
            return Map.of("status", "error", "message", "Rider login is required");
        }
        ResponseEntity<?> response = riderProfileController.getProfile(request.userId());
        if (!response.getStatusCode().is2xxSuccessful()) {
            return Map.of("status", "error", "message", stringifyBody(response.getBody()));
        }
        return Map.of("status", "ok", "paymentOptions", listValue(bodyAsMap(response.getBody()).get("paymentOptions")));
    }

    private Map<String, Object> executeCars(ChatbotRequest request) {
        if (!"DRIVER".equals(request.userType()) || !isValidUuid(request.userId())) {
            return Map.of("status", "error", "message", "Driver login is required");
        }
        ResponseEntity<?> response = driverDashboardController.getCars(request.userId());
        if (!response.getStatusCode().is2xxSuccessful()) {
            return Map.of("status", "error", "message", stringifyBody(response.getBody()));
        }
        return Map.of("status", "ok", "cars", listValue(bodyAsMap(response.getBody()).get("cars")));
    }

    private Map<String, Object> executeRatings(ChatbotRequest request) {
        if (!"DRIVER".equals(request.userType()) || !isValidUuid(request.userId())) {
            return Map.of("status", "error", "message", "Driver login is required");
        }
        ResponseEntity<?> response = driverDashboardController.getRatings(request.userId());
        if (!response.getStatusCode().is2xxSuccessful()) {
            return Map.of("status", "error", "message", stringifyBody(response.getBody()));
        }
        return Map.of("status", "ok", "ratings", bodyAsMap(response.getBody()));
    }

    private Map<String, Object> executeTripStatus(Map<String, Object> arguments) {
        String tripId = arguments.get("tripId") == null ? "" : String.valueOf(arguments.get("tripId")).trim();
        if (tripId.isBlank()) {
            return Map.of("status", "error", "message", "tripId is required");
        }
        ResponseEntity<?> response = rideEstimateController.tracking(tripId);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return Map.of("status", "error", "message", stringifyBody(response.getBody()));
        }
        return Map.of("status", "ok", "trip", bodyAsMap(response.getBody()));
    }

    private ChatbotReply handleRideHistory(String message, String userType, String userId) {
        if (!"DRIVER".equals(userType)) {
            return new ChatbotReply("Ride history is currently linked for logged-in drivers. The current backend does not expose rider ride history yet.", "deterministic-router", "ride_history_unavailable", List.of("Log in as a driver and ask 'show my last 10 rides'.", "Ask for trip tracking with a trip ID."));
        }
        if (!isValidUuid(userId)) {
            return loginRequired("Please log in as a driver first so I can fetch your rides.");
        }
        ResponseEntity<?> response = driverDashboardController.previousRides(userId);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return errorReply("ride_history_error", response);
        }
        Map<?, ?> body = bodyAsMap(response.getBody());
        List<?> rides = listValue(body.get("rides"));
        int limit = Math.min(parseRequestedCount(message), rides.size());
        if (limit == 0) {
            return new ChatbotReply("I found no previous rides for this driver account.", "deterministic-router", "ride_history_empty", List.of("Ask for your driver ratings.", "Ask for your registered cars."));
        }
        List<String> lines = new ArrayList<>();
        lines.add("Your last " + limit + " rides:");
        for (int index = 0; index < limit; index++) {
            Map<?, ?> ride = bodyAsMap(rides.get(index));
            lines.add((index + 1) + ". " + stringValue(ride.get("rideId")) + " | " + stringValue(ride.get("source")) + " -> " + stringValue(ride.get("destination")) + " | USD " + stringValue(ride.get("fare")) + " | " + stringValue(ride.get("date")));
        }
        return new ChatbotReply(String.join("\n", lines), "deterministic-router", "ride_history", List.of("Ask 'show my ratings'.", "Ask 'show my cars'."));
    }

    private ChatbotReply handleSavedCards(String userType, String userId) {
        if (!"RIDER".equals(userType)) {
            return new ChatbotReply("Saved cards are linked for logged-in riders.", "deterministic-router", "saved_cards_unavailable", List.of("Log in as a rider and ask 'show my saved cards'.", "Ask for rider payment options instead."));
        }
        if (!isValidUuid(userId)) {
            return loginRequired("Please log in as a rider first so I can fetch your saved cards.");
        }
        ResponseEntity<?> response = riderProfileController.getSavedCards(userId);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return errorReply("saved_cards_error", response);
        }
        Map<?, ?> body = bodyAsMap(response.getBody());
        List<?> cards = listValue(body.get("savedCards"));
        if (cards.isEmpty()) {
            return new ChatbotReply("No saved cards were found for this rider account.", "deterministic-router", "saved_cards_empty", List.of("Ask 'show my payment options'."));
        }
        List<String> lines = new ArrayList<>();
        lines.add("Your saved cards:");
        for (Object cardObj : cards) {
            Map<?, ?> card = bodyAsMap(cardObj);
            lines.add("- " + stringValue(card.get("cardHolderName")) + " | ****" + stringValue(card.get("last4")) + " | expires " + pad2(card.get("expiryMonth")) + "/" + stringValue(card.get("expiryYear")));
        }
        return new ChatbotReply(String.join("\n", lines), "deterministic-router", "saved_cards", List.of("Ask 'show my payment options'."));
    }

    private ChatbotReply handlePayments(String userType, String userId) {
        if (!"RIDER".equals(userType)) {
            return new ChatbotReply("Payment options are currently linked for logged-in riders.", "deterministic-router", "payments_unavailable", List.of("Log in as a rider and ask 'show my payment options'."));
        }
        if (userId.isBlank()) {
            return loginRequired("Please log in as a rider first so I can fetch your payment options.");
        }
        ResponseEntity<?> response = riderProfileController.getProfile(userId);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return errorReply("payments_error", response);
        }
        Map<?, ?> body = bodyAsMap(response.getBody());
        List<?> payments = listValue(body.get("paymentOptions"));
        if (payments.isEmpty()) {
            return new ChatbotReply("No rider payment options were found.", "deterministic-router", "payments_empty", List.of("Ask 'show my saved cards'."));
        }
        List<String> lines = new ArrayList<>();
        lines.add("Your rider payment options:");
        for (Object optionObj : payments) {
            Map<?, ?> option = bodyAsMap(optionObj);
            lines.add("- " + stringValue(option.get("type")) + ": " + stringValue(option.get("details")));
        }
        return new ChatbotReply(String.join("\n", lines), "deterministic-router", "payments", List.of("Ask 'show my saved cards'."));
    }

    private ChatbotReply handleCars(String userType, String userId) {
        if (!"DRIVER".equals(userType)) {
            return new ChatbotReply("Vehicle lookup is currently linked for logged-in drivers.", "deterministic-router", "cars_unavailable", List.of("Log in as a driver and ask 'show my cars'."));
        }
        if (!isValidUuid(userId)) {
            return loginRequired("Please log in as a driver first so I can fetch your cars.");
        }
        ResponseEntity<?> response = driverDashboardController.getCars(userId);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return errorReply("cars_error", response);
        }
        Map<?, ?> body = bodyAsMap(response.getBody());
        List<?> cars = listValue(body.get("cars"));
        if (cars.isEmpty()) {
            return new ChatbotReply("No cars were found for this driver account.", "deterministic-router", "cars_empty", List.of("Use the driver module to add a car.", "Ask 'show my ratings'."));
        }
        List<String> lines = new ArrayList<>();
        lines.add("Your registered cars:");
        for (Object carObj : cars) {
            Map<?, ?> car = bodyAsMap(carObj);
            lines.add("- " + stringValue(car.get("model")) + " | plate " + stringValue(car.get("plateNumber")) + " | color " + stringValue(car.get("color")));
        }
        return new ChatbotReply(String.join("\n", lines), "deterministic-router", "cars", List.of("Ask 'show my ratings'.", "Ask 'show my last 5 rides'."));
    }

    private ChatbotReply handleRatings(String userType, String userId) {
        if (!"DRIVER".equals(userType)) {
            return new ChatbotReply("Ratings are currently linked for logged-in drivers.", "deterministic-router", "ratings_unavailable", List.of("Log in as a driver and ask 'show my ratings'."));
        }
        if (!isValidUuid(userId)) {
            return loginRequired("Please log in as a driver first so I can fetch your ratings.");
        }
        ResponseEntity<?> response = driverDashboardController.getRatings(userId);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return errorReply("ratings_error", response);
        }
        Map<?, ?> body = bodyAsMap(response.getBody());
        List<?> strengths = listValue(body.get("strengths"));
        List<String> lines = new ArrayList<>();
        lines.add("Driver ratings summary:");
        lines.add("- Overall rating: " + stringValue(body.get("overallRating")));
        lines.add("- Total rated trips: " + stringValue(body.get("totalTripsRated")));
        if (!strengths.isEmpty()) {
            lines.add("Strengths:");
            for (Object strengthObj : strengths) {
                Map<?, ?> strength = bodyAsMap(strengthObj);
                lines.add("- " + stringValue(strength.get("name")) + ": " + stringValue(strength.get("score")));
            }
        }
        return new ChatbotReply(String.join("\n", lines), "deterministic-router", "ratings", List.of("Ask 'show my last 10 rides'.", "Ask 'show my cars'."));
    }

    private ChatbotReply handleTripTracking(String message) {
        Matcher matcher = TRIP_ID_PATTERN.matcher(message.toUpperCase());
        if (!matcher.find()) {
            return new ChatbotReply("Please include a trip ID such as 'track trip TRIP-1234ABCD'.", "deterministic-router", "trip_tracking_missing_id", List.of("Ask 'track trip TRIP-XXXXXXXX'."));
        }
        String tripId = matcher.group(1).toUpperCase();
        ResponseEntity<?> response = rideEstimateController.tracking(tripId);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return errorReply("trip_tracking_error", response);
        }
        Map<?, ?> body = bodyAsMap(response.getBody());
        return new ChatbotReply("Trip " + stringValue(body.get("tripId")) + " is " + stringValue(body.get("status")) + " with driver " + stringValue(body.get("driverName")) + ". ETA: " + stringValue(body.get("etaMinutes")) + " minutes.", "deterministic-router", "trip_tracking", List.of("Ask for another trip status.", "Ask 'show my payment options'."));
    }

    private ChatbotReply loginRequired(String response) {
        return new ChatbotReply(response, "deterministic-router", "login_required", defaultSuggestions());
    }

    private ChatbotReply errorReply(String intent, ResponseEntity<?> response) {
        return new ChatbotReply("The linked API returned " + response.getStatusCode().value() + ": " + stringifyBody(response.getBody()), "deterministic-router", intent, defaultSuggestions());
    }

    private ChatbotResponse toResponse(ChatbotReply reply) {
        return new ChatbotResponse(reply.response(), reply.source(), Instant.now().toString(), reply.suggestions(), reply.intent());
    }

    private int parseRequestedCount(String message) {
        Matcher matcher = NUMBER_PATTERN.matcher(message);
        if (matcher.find()) {
            return Math.max(1, Math.min(20, Integer.parseInt(matcher.group(1))));
        }
        return 5;
    }

    private boolean containsAny(String value, String... options) {
        for (String option : options) {
            if (value.contains(option)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRideHistoryIntent(String value) {
        boolean mentionsRide = containsAny(value, "ride", "rides", "trip", "trips", "history", "past rides", "recent rides", "ride history", "trip history");
        boolean asksForList = containsAny(value, "last", "previous", "recent", "show", "give", "list", "fetch", "get");
        return mentionsRide && asksForList;
    }

    private boolean isSavedCardsIntent(String value) {
        return containsAny(value,
                "saved card", "saved cards", "my cards", "cards on file", "what cards", "card list", "credit cards", "debit cards");
    }

    private boolean isPaymentIntent(String value) {
        return containsAny(value,
                "payment", "payments", "payment option", "payment options", "payment methods", "how can i pay", "ways to pay");
    }

    private boolean isCarsIntent(String value) {
        return containsAny(value,
                "car", "cars", "vehicle", "vehicles", "my car", "my cars", "registered cars", "registered vehicles");
    }

    private boolean isRatingsIntent(String value) {
        return containsAny(value,
                "rating", "ratings", "my rating", "my ratings", "feedback score", "driver score", "review score");
    }

    private boolean isTripTrackingIntent(String value) {
        return containsAny(value,
                "track", "tracking", "trip status", "ride status", "where is trip", "where is my ride", "status of trip", "status of ride");
    }

    private boolean isValidUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> bodyAsMap(Object body) {
        return body instanceof Map<?, ?> map ? map : Map.of("value", body);
    }

    private List<?> listValue(Object body) {
        return body instanceof List<?> list ? list : List.of();
    }

    private String stringifyBody(Object body) {
        if (body == null) {
            return "No response body";
        }
        return body.toString();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String pad2(Object value) {
        String raw = stringValue(value);
        return raw.length() == 1 ? "0" + raw : raw;
    }

    private List<String> defaultSuggestions() {
        return List.of("show my last 10 rides", "show my saved cards", "show my payment options", "show my ratings");
    }

    public record ChatbotReply(String response, String source, String intent, List<String> suggestions) {
    }
}
