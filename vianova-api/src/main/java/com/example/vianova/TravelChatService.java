package com.example.vianova;

import com.example.vianova.ChatbotController.ChatbotRequest;
import com.example.vianova.ChatbotController.ChatbotResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TravelChatService {

    private static final Logger log = LoggerFactory.getLogger(TravelChatService.class);

    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b(\\d{1,2})\\b");
    private static final Pattern TRIP_ID_PATTERN = Pattern.compile("\\b(TRIP-[A-Z0-9-]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RIDE_ESTIMATE_PATTERN = Pattern.compile("\\b(?:book|schedule|get|find|estimate|show|need|want)\\b.*?\\b(?:ride|cab|trip|fare|estimate|option|options)\\b.*?\\bfrom\\b\\s+(.+?)\\s+\\bto\\b\\s+(.+?)(?:\\s+\\b(?:at|for|on)\\b\\s+(.+))?$", Pattern.CASE_INSENSITIVE);

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

        log.info("Chatbot request received: userType={}, page={}, hasUserId={}, messageLength={}",
                safeRequest.userType(), safeRequest.page(), !safeRequest.userId().isBlank(), safeRequest.message().length());

        ChatbotReply llmReply = openAiToolCallingService.chatWithTools(safeRequest, this);
        if (llmReply != null) {
            log.info("Chatbot response selected: source={}, intent={}", llmReply.source(), llmReply.intent());
            return toResponse(llmReply);
        }
        ChatbotReply fallbackReply = deterministicReply(safeRequest);
        log.info("Chatbot response selected: source={}, intent={}", fallbackReply.source(), fallbackReply.intent());
        return toResponse(fallbackReply);
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
        if (isRideEstimateIntent(normalizedMessage)) {
            return handleRideEstimate(message);
        }
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
        return stringifyBody(executeToolPayload(toolName, arguments, request));
    }

    public Map<String, Object> executeToolPayload(String toolName, Map<String, Object> arguments, ChatbotRequest request) {
        return switch (toolName) {
            case "get_driver_rides" -> executeDriverRides(arguments, request);
            case "get_rider_rides" -> executeRiderRides(arguments, request);
            case "get_rider_saved_cards" -> executeSavedCards(request);
            case "get_rider_payment_options" -> executePayments(request);
            case "get_driver_cars" -> executeCars(request);
            case "get_driver_ratings" -> executeRatings(request);
            case "get_trip_status" -> executeTripStatus(arguments);
            case "get_ride_estimate" -> executeRideEstimate(arguments);
            default -> Map.of("status", "error", "message", "Unsupported tool: " + toolName);
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

    private Map<String, Object> executeRiderRides(Map<String, Object> arguments, ChatbotRequest request) {
        if (!"RIDER".equals(request.userType()) || !isValidUuid(request.userId())) {
            return Map.of("status", "error", "message", "Rider login is required");
        }
        int limit = arguments.get("limit") instanceof Number number ? Math.max(1, Math.min(20, number.intValue())) : 5;
        ResponseEntity<?> response = riderProfileController.getRides(request.userId(), limit);
        if (!response.getStatusCode().is2xxSuccessful()) {
            return Map.of("status", "error", "message", stringifyBody(response.getBody()));
        }
        Map<?, ?> body = bodyAsMap(response.getBody());
        return Map.of(
                "status", "ok",
                "count", body.get("count") == null ? 0 : body.get("count"),
                "rides", listValue(body.get("rides"))
        );
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

    private Map<String, Object> executeRideEstimate(Map<String, Object> arguments) {
        String source = stringValue(arguments.get("source")).trim();
        String destination = stringValue(arguments.get("destination")).trim();
        String departureTime = normalizeDepartureTime(stringValue(arguments.get("departureTime")).trim());

        if (source.isBlank() || destination.isBlank()) {
            return Map.of("status", "error", "message", "source and destination are required");
        }

        ResponseEntity<?> response = rideEstimateController.estimateRide(
                new RideEstimateController.RideEstimateRequest(source, destination, departureTime)
        );
        if (!response.getStatusCode().is2xxSuccessful()) {
            return Map.of("status", "error", "message", stringifyBody(response.getBody()));
        }
        Object body = response.getBody();
        if (!(body instanceof RideEstimateController.RideEstimateResponse estimate)) {
            return Map.of("status", "error", "message", "Unexpected ride estimate response");
        }
        return rideEstimateMap(estimate);
    }

    private ChatbotReply handleRideEstimate(String message) {
        ParsedRideEstimate parsed = parseRideEstimate(message);
        if (parsed == null || parsed.source().isBlank() || parsed.destination().isBlank()) {
            return new ChatbotReply(
                    "Tell me the pickup and drop-off in this format: 'book a ride from JFK Airport to Times Square'.",
                    "deterministic-router",
                    "ride_estimate_missing_locations",
                    List.of("book a ride from JFK Airport to Times Square", "estimate ride from Downtown to Airport")
            );
        }

        Map<String, Object> result = executeRideEstimate(Map.of(
                "source", parsed.source(),
                "destination", parsed.destination(),
                "departureTime", parsed.departureTime()
        ));
        if (!"ok".equals(result.get("status"))) {
            return new ChatbotReply(
                    "Unable to get ride estimates: " + stringValue(result.get("message")),
                    "deterministic-router",
                    "ride_estimate_error",
                    defaultSuggestions()
            );
        }
        return new ChatbotReply(
                formatRideEstimate(result),
                "deterministic-router",
                "ride_estimate",
                List.of("show my payment options", "show my saved cards"),
                Map.of("rideEstimate", result)
        );
    }

    private ChatbotReply handleRideHistory(String message, String userType, String userId) {
        if ("RIDER".equals(userType)) {
            if (!isValidUuid(userId)) {
                return loginRequired("Please log in as a rider first so I can fetch your rides.");
            }
            ResponseEntity<?> response = riderProfileController.getRides(userId, parseRequestedCount(message));
            if (!response.getStatusCode().is2xxSuccessful()) {
                return errorReply("rider_ride_history_error", response);
            }
            Map<?, ?> body = bodyAsMap(response.getBody());
            List<?> rides = listValue(body.get("rides"));
            if (rides.isEmpty()) {
                return new ChatbotReply("I found no rides for this rider account yet.", "deterministic-router", "rider_ride_history_empty", List.of("show my payment options", "show my saved cards"));
            }
            List<String> lines = new ArrayList<>();
            lines.add("Your last " + rides.size() + " rider rides:");
            for (int index = 0; index < rides.size(); index++) {
                Map<?, ?> ride = bodyAsMap(rides.get(index));
                lines.add((index + 1) + ". " + stringValue(ride.get("requestId")) + " | " + stringValue(ride.get("source")) + " -> " + stringValue(ride.get("destination")) + " | " + stringValue(ride.get("status")) + " | " + stringValue(ride.get("currency")) + " " + stringValue(ride.get("finalFare")) + " | " + stringValue(ride.get("departureTime")));
            }
            return new ChatbotReply(String.join("\n", lines), "deterministic-router", "rider_ride_history", List.of("show my saved cards", "show my payment options"));
        }
        if (!"DRIVER".equals(userType)) {
            return loginRequired("Please log in first so I can fetch your ride history.");
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
        return new ChatbotResponse(reply.response(), reply.source(), Instant.now().toString(), reply.suggestions(), reply.intent(), reply.payload());
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

    private boolean isRideEstimateIntent(String value) {
        return containsAny(value, "book", "schedule", "estimate", "fare", "ride option", "ride options", "need a ride", "want a ride")
                && value.contains("from")
                && value.contains("to");
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

    private ParsedRideEstimate parseRideEstimate(String message) {
        Matcher matcher = RIDE_ESTIMATE_PATTERN.matcher(message.trim());
        if (!matcher.find()) {
            return null;
        }

        String source = matcher.group(1) == null ? "" : matcher.group(1).trim();
        String destination = matcher.group(2) == null ? "" : matcher.group(2).trim();
        String departureTime = matcher.group(3) == null ? "" : matcher.group(3).trim();
        return new ParsedRideEstimate(source, destination, normalizeDepartureTime(departureTime));
    }

    private String normalizeDepartureTime(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return LocalDateTime.now().plusMinutes(15).withSecond(0).withNano(0).toString();
        }
        String value = rawValue.trim();
        if ("now".equalsIgnoreCase(value)) {
            return LocalDateTime.now().withSecond(0).withNano(0).toString();
        }
        if ("asap".equalsIgnoreCase(value) || "soon".equalsIgnoreCase(value)) {
            return LocalDateTime.now().plusMinutes(15).withSecond(0).withNano(0).toString();
        }
        try {
            return LocalDateTime.parse(value).withSecond(0).withNano(0).toString();
        } catch (DateTimeParseException ex) {
            return LocalDateTime.now().plusMinutes(15).withSecond(0).withNano(0).toString();
        }
    }

    private Map<String, Object> rideEstimateMap(RideEstimateController.RideEstimateResponse estimate) {
        return Map.of(
                "status", "ok",
                "source", estimate.source(),
                "destination", estimate.destination(),
                "departureTime", estimate.departureTime(),
                "estimatedTimeMinutes", estimate.estimatedTimeMinutes(),
                "estimatedFare", estimate.estimatedFare(),
                "currency", estimate.currency(),
                "modelVersion", estimate.modelVersion(),
                "fallbackUsed", estimate.fallbackUsed(),
                "availableDrivers", estimate.availableDrivers().stream()
                        .map(driver -> Map.of(
                                "driverId", driver.driverId(),
                                "name", driver.name(),
                                "vehicle", driver.vehicle(),
                                "etaMinutes", driver.etaMinutes(),
                                "rating", driver.rating()
                        ))
                        .toList()
        );
    }

    private String formatRideEstimate(Map<String, Object> result) {
        List<String> lines = new ArrayList<>();
        String currency = stringValue(result.get("currency")).isBlank() ? "USD" : stringValue(result.get("currency"));
        lines.add("Ride estimate from " + stringValue(result.get("source")) + " to " + stringValue(result.get("destination")) + ":");
        lines.add("- ETA: " + stringValue(result.get("estimatedTimeMinutes")) + " minutes");
        lines.add("- Estimated fare: " + currency + " " + stringValue(result.get("estimatedFare")));
        List<?> drivers = listValue(result.get("availableDrivers"));
        if (drivers.isEmpty()) {
            lines.add("No available drivers were returned for this estimate.");
            return String.join("\n", lines);
        }
        lines.add("Available rides:");
        for (Object driverObj : drivers) {
            Map<?, ?> driver = bodyAsMap(driverObj);
            lines.add("- " + stringValue(driver.get("name"))
                    + " | " + stringValue(driver.get("vehicle"))
                    + " | ETA " + stringValue(driver.get("etaMinutes")) + " min"
                    + " | rating " + stringValue(driver.get("rating")));
        }
        return String.join("\n", lines);
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
        return List.of("show my saved cards", "show my payment options", "track trip TRIP-1234ABCD", "show my last 5 rides");
    }

    public record ChatbotReply(String response, String source, String intent, List<String> suggestions, Map<String, Object> payload) {
        public ChatbotReply(String response, String source, String intent, List<String> suggestions) {
            this(response, source, intent, suggestions, Map.of());
        }
    }

    private record ParsedRideEstimate(String source, String destination, String departureTime) {
    }
}
