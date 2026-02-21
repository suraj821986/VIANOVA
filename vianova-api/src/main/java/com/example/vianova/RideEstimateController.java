package com.example.vianova;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/rides")
public class RideEstimateController {

    private static final Map<String, TripTracking> TRIPS = new ConcurrentHashMap<>();
    private static final Map<String, String> TRIP_PINS = new ConcurrentHashMap<>();

    @PostMapping("/estimate")
    public ResponseEntity<?> estimateRide(@RequestBody RideEstimateRequest request) {
        if (isBlank(request.source()) || isBlank(request.destination()) || isBlank(request.departureTime())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("source, destination and departureTime are required");
        }

        int routeScore = Math.abs((request.source() + ":" + request.destination()).hashCode() % 30) + 5;
        int timeScore = Math.abs(request.departureTime().hashCode() % 12);
        int etaMinutes = routeScore + timeScore;

        LocalDateTime departure;
        try {
            departure = LocalDateTime.parse(request.departureTime());
        } catch (DateTimeParseException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("departureTime must be ISO local date-time, for example 2026-02-21T18:30");
        }
        double surgeMultiplier = isPeakHour(departure.toLocalTime()) ? 1.35 : 1.0;
        double estimatedFare = round2((3.2 + (routeScore * 1.45)) * surgeMultiplier);

        List<AvailableDriver> drivers = List.of(
                new AvailableDriver("Aarav S.", "Toyota Prius", Math.max(2, etaMinutes - 6), 4.9),
                new AvailableDriver("Neha R.", "Hyundai Ioniq", Math.max(3, etaMinutes - 4), 4.8),
                new AvailableDriver("Vikram P.", "Honda City", Math.max(4, etaMinutes - 2), 4.7)
        );

        RideEstimateResponse response = new RideEstimateResponse(
                request.source(),
                request.destination(),
                request.departureTime(),
                etaMinutes,
                estimatedFare,
                "USD",
                drivers
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/options")
    public ResponseEntity<?> rideOptions(@RequestBody RideEstimateRequest request) {
        ResponseEntity<?> validation = validateRequest(request);
        if (validation != null) {
            return validation;
        }

        int routeScore = Math.abs((request.source() + ":" + request.destination()).hashCode() % 30) + 5;
        int timeScore = Math.abs(request.departureTime().hashCode() % 12);
        int etaMinutes = routeScore + timeScore;

        LocalDateTime departure = LocalDateTime.parse(request.departureTime());
        double surgeMultiplier = isPeakHour(departure.toLocalTime()) ? 1.35 : 1.0;
        double baseFare = round2((3.2 + (routeScore * 1.45)) * surgeMultiplier);

        List<Map<String, Object>> providers = List.of(
                Map.of(
                        "provider", "Uber",
                        "car", "UberX - Toyota Corolla",
                        "driver", "Daniel K.",
                        "etaMinutes", Math.max(2, etaMinutes - 2),
                        "fare", round2(baseFare + 1.8)
                ),
                Map.of(
                        "provider", "Uber",
                        "car", "Uber Comfort - Honda Accord",
                        "driver", "Mia T.",
                        "etaMinutes", Math.max(3, etaMinutes - 1),
                        "fare", round2(baseFare + 3.2)
                ),
                Map.of(
                        "provider", "Lyft",
                        "car", "Lyft Standard - Nissan Altima",
                        "driver", "Sophia M.",
                        "etaMinutes", Math.max(2, etaMinutes - 2),
                        "fare", round2(baseFare + 1.2)
                ),
                Map.of(
                        "provider", "Lyft",
                        "car", "Lyft XL - Toyota Highlander",
                        "driver", "Chris P.",
                        "etaMinutes", Math.max(4, etaMinutes),
                        "fare", round2(baseFare + 4.1)
                )
        );

        return ResponseEntity.ok(Map.of(
                "source", request.source(),
                "destination", request.destination(),
                "departureTime", request.departureTime(),
                "currency", "USD",
                "options", providers
        ));
    }

    @PostMapping("/negotiate")
    public ResponseEntity<?> negotiateFare(@RequestBody NegotiateFareRequest request) {
        if (request == null || isBlank(request.source()) || isBlank(request.destination()) || isBlank(request.departureTime())
                || isBlank(request.driverName()) || request.proposedFare() <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("source, destination, departureTime, driverName and proposedFare are required");
        }

        ResponseEntity<?> validation = validateRequest(new RideEstimateRequest(request.source(), request.destination(), request.departureTime()));
        if (validation != null) {
            return validation;
        }

        int routeScore = Math.abs((request.source() + ":" + request.destination()).hashCode() % 30) + 5;
        LocalDateTime departure = LocalDateTime.parse(request.departureTime());
        double surgeMultiplier = isPeakHour(departure.toLocalTime()) ? 1.35 : 1.0;
        double driverBaseFare = round2((3.2 + (routeScore * 1.45)) * surgeMultiplier);

        double minAcceptable = round2(driverBaseFare * 0.9);
        if (request.proposedFare() >= driverBaseFare) {
            return ResponseEntity.ok(Map.of(
                    "accepted", true,
                    "counterOffer", request.proposedFare(),
                    "message", "Deal accepted. Driver is on the way."
            ));
        }
        if (request.proposedFare() >= minAcceptable) {
            return ResponseEntity.ok(Map.of(
                    "accepted", true,
                    "counterOffer", driverBaseFare,
                    "message", "Deal accepted at adjusted fare."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "accepted", false,
                "counterOffer", minAcceptable,
                "message", "Driver offered a counter fare."
        ));
    }

    @PostMapping("/tracking-preview")
    public ResponseEntity<?> trackingPreview(@RequestBody TrackingPreviewRequest request) {
        if (request == null || isBlank(request.source()) || isBlank(request.destination()) || isBlank(request.driverName())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("source, destination and driverName are required");
        }

        Coordinates rider = new Coordinates(37.7749, -122.4194);
        Coordinates driver = new Coordinates(37.7842, -122.4094);
        return ResponseEntity.ok(Map.of(
                "source", request.source(),
                "destination", request.destination(),
                "driverName", request.driverName(),
                "riderLocation", rider,
                "driverLocation", driver
        ));
    }

    @PostMapping("/finalize")
    public ResponseEntity<?> finalizeTrip(@RequestBody FinalizeTripRequest request) {
        if (request == null || isBlank(request.source()) || isBlank(request.destination()) || isBlank(request.departureTime())
                || isBlank(request.driverName()) || isBlank(request.paymentOption()) || request.finalFare() <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("source, destination, departureTime, driverName, paymentOption and finalFare are required");
        }

        ResponseEntity<?> validation = validateRequest(new RideEstimateRequest(request.source(), request.destination(), request.departureTime()));
        if (validation != null) {
            return validation;
        }

        String tripId = "TRIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String tripPin = String.format("%04d", new Random().nextInt(10000));
        TripTracking tracking = new TripTracking(
                tripId,
                request.driverName(),
                request.source(),
                request.destination(),
                new Coordinates(37.7749, -122.4194),
                new Coordinates(37.7842, -122.4094),
                6,
                "DRIVER_EN_ROUTE"
        );
        TRIPS.put(tripId, tracking);
        TRIP_PINS.put(tripId, tripPin);

        return ResponseEntity.ok(Map.of(
                "tripId", tripId,
                "tripPin", tripPin,
                "message", "Trip finalized successfully",
                "status", "DRIVER_EN_ROUTE"
        ));
    }

    @PostMapping("/complete")
    public ResponseEntity<?> completeTrip(@RequestBody CompleteTripRequest request) {
        if (request == null || isBlank(request.tripId()) || isBlank(request.tripPin())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("tripId and tripPin are required");
        }

        TripTracking current = TRIPS.get(request.tripId());
        if (current == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip not found");
        }
        String storedPin = TRIP_PINS.get(request.tripId());
        if (storedPin == null || !storedPin.equals(request.tripPin())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid trip PIN");
        }

        TripTracking completed = new TripTracking(
                current.tripId(),
                current.driverName(),
                current.source(),
                current.destination(),
                current.riderLocation(),
                current.riderLocation(),
                0,
                "COMPLETED"
        );
        TRIPS.put(request.tripId(), completed);

        return ResponseEntity.ok(Map.of(
                "tripId", request.tripId(),
                "status", "COMPLETED",
                "message", "Trip completed successfully",
                "allowFeedback", true
        ));
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> submitFeedback(@RequestBody FeedbackRequest request) {
        if (request == null || isBlank(request.tripId()) || request.rating() < 1 || request.rating() > 5 || request.tipAmount() < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("tripId, rating (1-5) and non-negative tipAmount are required");
        }

        TripTracking trip = TRIPS.get(request.tripId());
        if (trip == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip not found");
        }
        if (!"COMPLETED".equalsIgnoreCase(trip.status())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Trip must be completed before feedback");
        }

        return ResponseEntity.ok(Map.of(
                "tripId", request.tripId(),
                "message", "Thanks for the feedback",
                "tipAmount", request.tipAmount(),
                "rating", request.rating()
        ));
    }

    @org.springframework.web.bind.annotation.GetMapping("/tracking")
    public ResponseEntity<?> tracking(@org.springframework.web.bind.annotation.RequestParam String tripId) {
        if (isBlank(tripId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("tripId is required");
        }

        TripTracking tracking = TRIPS.get(tripId);
        if (tracking == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip not found");
        }

        return ResponseEntity.ok(tracking);
    }

    private ResponseEntity<?> validateRequest(RideEstimateRequest request) {
        if (isBlank(request.source()) || isBlank(request.destination()) || isBlank(request.departureTime())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("source, destination and departureTime are required");
        }

        try {
            LocalDateTime.parse(request.departureTime());
        } catch (DateTimeParseException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("departureTime must be ISO local date-time, for example 2026-02-21T18:30");
        }
        return null;
    }

    private boolean isPeakHour(LocalTime time) {
        return (time.getHour() >= 7 && time.getHour() <= 10) || (time.getHour() >= 17 && time.getHour() <= 20);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record RideEstimateRequest(String source, String destination, String departureTime) {
    }

    public record RideEstimateResponse(
            String source,
            String destination,
            String departureTime,
            int estimatedTimeMinutes,
            double estimatedFare,
            String currency,
            List<AvailableDriver> availableDrivers
    ) {
    }

    public record AvailableDriver(
            String name,
            String vehicle,
            int etaMinutes,
            double rating
    ) {
    }

    public record NegotiateFareRequest(
            String source,
            String destination,
            String departureTime,
            String driverName,
            double proposedFare
    ) {
    }

    public record TrackingPreviewRequest(
            String source,
            String destination,
            String driverName
    ) {
    }

    public record FinalizeTripRequest(
            String source,
            String destination,
            String departureTime,
            String driverName,
            double finalFare,
            String paymentOption
    ) {
    }

    public record Coordinates(double lat, double lng) {
    }

    public record TripTracking(
            String tripId,
            String driverName,
            String source,
            String destination,
            Coordinates riderLocation,
            Coordinates driverLocation,
            int etaMinutes,
            String status
    ) {
    }

    public record CompleteTripRequest(String tripId, String tripPin) {
    }

    public record FeedbackRequest(String tripId, double tipAmount, int rating, String comment) {
    }
}
