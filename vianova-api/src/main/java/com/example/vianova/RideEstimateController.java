package com.example.vianova;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
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

    private static final Logger log = LoggerFactory.getLogger(RideEstimateController.class);
    private static final Map<String, TripTracking> TRIPS = new ConcurrentHashMap<>();
    private static final Map<String, String> TRIP_PINS = new ConcurrentHashMap<>();
    private static final Map<String, TripMeta> TRIP_META = new ConcurrentHashMap<>();

    private final RideFlowRepository rideFlowRepository;
    private final RideMlEstimatorService rideMlEstimatorService;

    public RideEstimateController(RideFlowRepository rideFlowRepository, RideMlEstimatorService rideMlEstimatorService) {
        this.rideFlowRepository = rideFlowRepository;
        this.rideMlEstimatorService = rideMlEstimatorService;
    }

    @PostMapping("/estimate")
    public ResponseEntity<?> estimateRide(@RequestBody RideEstimateRequest request) {
        ResponseEntity<?> validation = validateRequest(request);
        if (validation != null) {
            return validation;
        }
        log.info(
                "Received /rides/estimate request source='{}' destination='{}' departureTime='{}'",
                request.source(),
                request.destination(),
                request.departureTime()
        );

        LocalDateTime departure = LocalDateTime.parse(request.departureTime());
        RideMlEstimatorService.EstimateResult estimate = rideMlEstimatorService.estimate(
                request.source(),
                request.destination(),
                departure
        );

        List<AvailableDriver> drivers = rideFlowRepository.findAvailableDrivers().stream()
                .limit(8)
                .map(driver -> new AvailableDriver(
                        driver.driverId().toString(),
                        driver.name(),
                        driver.vehicle(),
                        Math.max(2, estimate.estimatedTimeMinutes() - 2),
                        driver.rating().doubleValue()
                ))
                .toList();

        RideEstimateResponse response = new RideEstimateResponse(
                request.source(),
                request.destination(),
                request.departureTime(),
                estimate.estimatedTimeMinutes(),
                estimate.estimatedFare(),
                estimate.currency(),
                drivers,
                estimate.modelVersion(),
                estimate.fallbackUsed()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/accuracy-check")
    public ResponseEntity<?> accuracyCheck(@RequestBody AccuracyCheckRequest request) {
        if (request == null
                || request.tripDistance() < 0
                || isBlank(request.pickupLocationId())
                || isBlank(request.dropoffLocationId())
                || isBlank(request.pickupDateTime())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("tripDistance, pickupLocationId, dropoffLocationId and pickupDateTime are required");
        }

        LocalDateTime pickupDateTime;
        try {
            pickupDateTime = LocalDateTime.parse(request.pickupDateTime());
        } catch (DateTimeParseException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("pickupDateTime must be ISO local date-time, for example 2025-10-01T00:15:32");
        }

        RideMlEstimatorService.FeatureEstimateResult prediction = rideMlEstimatorService.estimateFeatures(
                request.tripDistance(),
                request.pickupLocationId(),
                request.dropoffLocationId(),
                pickupDateTime
        );

        double etaErrorSeconds = prediction.etaSeconds() - request.actualDurationSeconds();
        double fareError = prediction.fare() - request.actualFare();
        double totalError = prediction.totalNoTip() - request.actualTotalNoTip();

        return ResponseEntity.ok(new AccuracyCheckResponse(
                request.sampleId(),
                request.sourceLabel(),
                request.destinationLabel(),
                request.pickupDateTime(),
                request.tripDistance(),
                request.pickupLocationId(),
                request.dropoffLocationId(),
                round2(request.actualDurationSeconds() / 60.0),
                round2(prediction.etaSeconds() / 60.0),
                round2(etaErrorSeconds / 60.0),
                round2(request.actualFare()),
                round2(prediction.fare()),
                round2(fareError),
                round2(request.actualTolls()),
                round2(prediction.tolls()),
                round2(request.actualTotalNoTip()),
                round2(prediction.totalNoTip()),
                round2(totalError),
                round2(Math.abs(totalError)),
                prediction.currency(),
                prediction.modelVersion(),
                prediction.fallbackUsed()
        ));
    }

    @PostMapping("/options")
    public ResponseEntity<?> rideOptions(@RequestBody RideEstimateRequest request) {
        ResponseEntity<?> validation = validateRequest(request);
        if (validation != null) {
            return validation;
        }

        LocalDateTime departure = LocalDateTime.parse(request.departureTime());
        RideMlEstimatorService.EstimateResult estimate = rideMlEstimatorService.estimate(
                request.source(),
                request.destination(),
                departure
        );
        double baseFare = estimate.estimatedFare();

        List<Map<String, Object>> providers = List.of(
                Map.of(
                        "provider", "Uber",
                        "car", "UberX - Toyota Corolla",
                        "driver", "Daniel K.",
                        "etaMinutes", Math.max(2, estimate.estimatedTimeMinutes() - 2),
                        "fare", round2(baseFare + 1.8)
                ),
                Map.of(
                        "provider", "Uber",
                        "car", "Uber Comfort - Honda Accord",
                        "driver", "Mia T.",
                        "etaMinutes", Math.max(3, estimate.estimatedTimeMinutes() - 1),
                        "fare", round2(baseFare + 3.2)
                ),
                Map.of(
                        "provider", "Lyft",
                        "car", "Lyft Standard - Nissan Altima",
                        "driver", "Sophia M.",
                        "etaMinutes", Math.max(2, estimate.estimatedTimeMinutes() - 2),
                        "fare", round2(baseFare + 1.2)
                ),
                Map.of(
                        "provider", "Lyft",
                        "car", "Lyft XL - Toyota Highlander",
                        "driver", "Chris P.",
                        "etaMinutes", Math.max(4, estimate.estimatedTimeMinutes()),
                        "fare", round2(baseFare + 4.1)
                )
        );

        return ResponseEntity.ok(Map.of(
                "source", request.source(),
                "destination", request.destination(),
                "departureTime", request.departureTime(),
                "currency", estimate.currency(),
                "modelVersion", estimate.modelVersion(),
                "fallbackUsed", estimate.fallbackUsed(),
                "options", providers
        ));
    }

    @PostMapping("/requests")
    public ResponseEntity<?> createRideRequest(@RequestBody CreateRideRequest request) {
        if (request == null || isBlank(request.riderId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("riderId is required");
        }

        ResponseEntity<?> validation = validateRequest(new RideEstimateRequest(request.source(), request.destination(), request.departureTime()));
        if (validation != null) {
            return validation;
        }

        UUID riderId;
        try {
            riderId = UUID.fromString(request.riderId().trim());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("riderId must be a valid UUID");
        }
        log.info(
                "Received /rides/requests request riderId='{}' source='{}' destination='{}' departureTime='{}'",
                request.riderId(),
                request.source(),
                request.destination(),
                request.departureTime()
        );

        LocalDateTime departureTime = LocalDateTime.parse(request.departureTime());
        double estimatedFare;
        String currency;
        if (request.estimatedFare() > 0 && !isBlank(request.currency())) {
            estimatedFare = request.estimatedFare();
            currency = request.currency().trim().toUpperCase();
            log.info(
                    "Using client-provided estimate for /rides/requests riderId='{}' estimatedFare={} currency='{}'",
                    request.riderId(),
                    estimatedFare,
                    currency
            );
        } else {
            RideMlEstimatorService.EstimateResult estimate = rideMlEstimatorService.estimate(
                    request.source(),
                    request.destination(),
                    departureTime
            );
            estimatedFare = request.estimatedFare() > 0 ? request.estimatedFare() : estimate.estimatedFare();
            currency = isBlank(request.currency()) ? estimate.currency() : request.currency().trim().toUpperCase();
        }
        UUID requestId = rideFlowRepository.createRideRequest(
                riderId,
                request.source().trim(),
                request.destination().trim(),
                departureTime,
                estimatedFare,
                currency
        );

        return ResponseEntity.ok(Map.of(
                "requestId", requestId,
                "status", "PENDING_DRIVER",
                "message", "Ride request shared with nearby drivers"
        ));
    }

    @org.springframework.web.bind.annotation.GetMapping("/requests/status")
    public ResponseEntity<?> rideRequestStatus(
            @org.springframework.web.bind.annotation.RequestParam String requestId,
            @org.springframework.web.bind.annotation.RequestParam String riderId
    ) {
        if (isBlank(requestId) || isBlank(riderId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("requestId and riderId are required");
        }

        UUID requestUuid;
        UUID riderUuid;
        try {
            requestUuid = UUID.fromString(requestId.trim());
            riderUuid = UUID.fromString(riderId.trim());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("requestId and riderId must be valid UUIDs");
        }

        if (rideFlowRepository.expireOpenRideRequest(requestUuid, riderUuid)) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                    "requestId", requestUuid,
                    "riderId", riderUuid,
                    "status", "EXPIRED",
                    "message", "No drivers are available right now. Please try requesting the ride again."
            ));
        }

        RideFlowRepository.RideRequestRow rideRequest = rideFlowRepository.findRideRequestById(requestUuid).orElse(null);
        if (rideRequest == null) {
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                    "requestId", requestUuid,
                    "riderId", riderUuid,
                    "status", "EXPIRED",
                    "message", "This ride request is no longer active. Please request the ride again."
            ));
        }
        if (!riderUuid.equals(rideRequest.riderId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ride request does not belong to this rider");
        }

        String driverName = "";
        String assignedDriverId = "";
        if (rideRequest.assignedDriverId() != null) {
            assignedDriverId = rideRequest.assignedDriverId().toString();
            driverName = rideFlowRepository.findDriverById(rideRequest.assignedDriverId())
                    .map(RideFlowRepository.DriverIdentityRow::name)
                    .orElse("");
        }

        return ResponseEntity.ok(Map.ofEntries(
                Map.entry("requestId", rideRequest.requestId()),
                Map.entry("riderId", rideRequest.riderId()),
                Map.entry("status", rideRequest.status()),
                Map.entry("source", rideRequest.source()),
                Map.entry("destination", rideRequest.destination()),
                Map.entry("departureTime", rideRequest.departureTime()),
                Map.entry("estimatedFare", rideRequest.estimatedFare()),
                Map.entry("currency", rideRequest.currency()),
                Map.entry("riderOffer", rideRequest.riderOffer() == null ? "" : rideRequest.riderOffer()),
                Map.entry("counterOffer", rideRequest.counterOffer() == null ? "" : rideRequest.counterOffer()),
                Map.entry("riderFinalAcceptance", rideRequest.riderFinalAcceptance()),
                Map.entry("driverFinalAcceptance", rideRequest.driverFinalAcceptance()),
                Map.entry("assignedDriverId", assignedDriverId),
                Map.entry("driverName", driverName),
                Map.entry("updatedAt", rideRequest.updatedAt())
        ));
    }

    @PostMapping("/negotiate")
    public ResponseEntity<?> negotiateFare(@RequestBody NegotiateFareRequest request) {
        if (request == null || isBlank(request.source()) || isBlank(request.destination()) || isBlank(request.departureTime())
                || request.proposedFare() <= 0 || (isBlank(request.driverId()) && isBlank(request.driverName()))
                || isBlank(request.requestId()) || isBlank(request.riderId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("requestId, riderId, source, destination, departureTime, driverId/driverName and proposedFare are required");
        }

        ResponseEntity<?> validation = validateRequest(new RideEstimateRequest(request.source(), request.destination(), request.departureTime()));
        if (validation != null) {
            return validation;
        }
        UUID requestUuid;
        UUID riderUuid;
        UUID driverUuid;
        try {
            requestUuid = UUID.fromString(request.requestId().trim());
            riderUuid = UUID.fromString(request.riderId().trim());
            driverUuid = UUID.fromString(request.driverId().trim());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("requestId, riderId and driverId must be valid UUIDs");
        }

        RideFlowRepository.RideRequestRow rideRequest = rideFlowRepository.findRideRequestById(requestUuid).orElse(null);
        if (rideRequest == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ride request not found");
        }
        if (!riderUuid.equals(rideRequest.riderId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ride request does not belong to this rider");
        }
        if (rideRequest.assignedDriverId() == null || !driverUuid.equals(rideRequest.assignedDriverId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Driver has not accepted this ride request yet");
        }

        return rideFlowRepository.submitRiderOffer(requestUuid, riderUuid, driverUuid, request.proposedFare())
                .<ResponseEntity<?>>map(updated -> ResponseEntity.ok(Map.ofEntries(
                        Map.entry("accepted", false),
                        Map.entry("riderOffer", updated.riderOffer() == null ? request.proposedFare() : updated.riderOffer()),
                        Map.entry("counterOffer", updated.counterOffer() == null ? "" : updated.counterOffer()),
                        Map.entry("message", "Offer sent to driver. Waiting for driver response."),
                        Map.entry("status", updated.status())
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("Unable to send offer. Ask the driver to accept the ride request first."));
    }

    @PostMapping("/requests/accept-final")
    public ResponseEntity<?> acceptFinalFare(@RequestBody AcceptFinalFareRequest request) {
        if (request == null || isBlank(request.requestId()) || isBlank(request.riderId()) || isBlank(request.driverId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("requestId, riderId and driverId are required");
        }

        UUID requestUuid;
        UUID riderUuid;
        UUID driverUuid;
        try {
            requestUuid = UUID.fromString(request.requestId().trim());
            riderUuid = UUID.fromString(request.riderId().trim());
            driverUuid = UUID.fromString(request.driverId().trim());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("requestId, riderId and driverId must be valid UUIDs");
        }

        return rideFlowRepository.acceptFinalFareAsRider(requestUuid, riderUuid, driverUuid)
                .<ResponseEntity<?>>map(updated -> ResponseEntity.ok(Map.ofEntries(
                        Map.entry("message", "Rider accepted the final fare."),
                        Map.entry("requestId", updated.requestId()),
                        Map.entry("riderOffer", updated.riderOffer() == null ? "" : updated.riderOffer()),
                        Map.entry("counterOffer", updated.counterOffer() == null ? "" : updated.counterOffer()),
                        Map.entry("riderFinalAcceptance", updated.riderFinalAcceptance()),
                        Map.entry("driverFinalAcceptance", updated.driverFinalAcceptance())
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("No driver counter offer is available to accept yet"));
    }

    @PostMapping("/tracking-preview")
    public ResponseEntity<?> trackingPreview(@RequestBody TrackingPreviewRequest request) {
        if (request == null || isBlank(request.source()) || isBlank(request.destination())
                || (isBlank(request.driverId()) && isBlank(request.driverName()))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("source, destination and driverId/driverName are required");
        }

        DriverRef driverRef = resolveDriver(request.driverId(), request.driverName());
        if (driverRef == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Selected driver was not found");
        }

        Coordinates rider = new Coordinates(37.7749, -122.4194);
        Coordinates driver = new Coordinates(37.7842, -122.4094);
        return ResponseEntity.ok(Map.of(
                "source", request.source(),
                "destination", request.destination(),
                "driverName", driverRef.name(),
                "riderLocation", rider,
                "driverLocation", driver
        ));
    }

    @PostMapping("/finalize")
    public ResponseEntity<?> finalizeTrip(@RequestBody FinalizeTripRequest request) {
        if (request == null || isBlank(request.source()) || isBlank(request.destination()) || isBlank(request.departureTime())
                || (isBlank(request.driverId()) && isBlank(request.driverName()))
                || isBlank(request.paymentOption()) || request.finalFare() <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("source, destination, departureTime, driverId/driverName, paymentOption and finalFare are required");
        }

        ResponseEntity<?> validation = validateRequest(new RideEstimateRequest(request.source(), request.destination(), request.departureTime()));
        if (validation != null) {
            return validation;
        }
        if (isBlank(request.requestId()) || isBlank(request.riderId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("requestId and riderId are required");
        }
        DriverRef driverRef = resolveDriver(request.driverId(), request.driverName());
        if (driverRef == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Selected driver was not found");
        }
        UUID requestUuid;
        UUID riderUuid;
        try {
            requestUuid = UUID.fromString(request.requestId().trim());
            riderUuid = UUID.fromString(request.riderId().trim());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("requestId and riderId must be valid UUIDs");
        }
        RideFlowRepository.RideRequestRow rideRequest = rideFlowRepository.findRideRequestById(requestUuid).orElse(null);
        if (rideRequest == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ride request not found");
        }
        if (!riderUuid.equals(rideRequest.riderId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Ride request does not belong to this rider");
        }
        if (rideRequest.assignedDriverId() == null || !driverRef.driverId().equals(rideRequest.assignedDriverId())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Driver has not accepted this ride request");
        }
        if (!rideRequest.riderFinalAcceptance() || !rideRequest.driverFinalAcceptance()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Both rider and driver must accept the final fare before trip finalization");
        }
        LocalDate rideDate = LocalDateTime.parse(request.departureTime()).toLocalDate();

        String tripId = "TRIP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String tripPin = String.format("%04d", new Random().nextInt(10000));
        TripTracking tracking = new TripTracking(
                tripId,
                driverRef.name(),
                request.source(),
                request.destination(),
                new Coordinates(37.7749, -122.4194),
                new Coordinates(37.7842, -122.4094),
                6,
                "DRIVER_EN_ROUTE"
        );
        TRIPS.put(tripId, tracking);
        TRIP_PINS.put(tripId, tripPin);
        TRIP_META.put(tripId, new TripMeta(driverRef.driverId(), request.finalFare(), rideDate));
        rideFlowRepository.updateRideRequestStatus(requestUuid, "DRIVER_EN_ROUTE");

        return ResponseEntity.ok(Map.of(
                "tripId", tripId,
                "tripPin", tripPin,
                "driverId", driverRef.driverId(),
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
        TripMeta tripMeta = TRIP_META.get(request.tripId());
        if (current == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip not found");
        }
        if (tripMeta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip metadata not found");
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
        rideFlowRepository.upsertCompletedRide(
                request.tripId(),
                tripMeta.driverId(),
                current.source(),
                current.destination(),
                tripMeta.finalFare(),
                tripMeta.rideDate()
        );
        if (!isBlank(request.requestId())) {
            try {
                rideFlowRepository.updateRideRequestStatus(UUID.fromString(request.requestId().trim()), "COMPLETED");
            } catch (IllegalArgumentException ignored) {
            }
        }

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
        TripMeta tripMeta = TRIP_META.get(request.tripId());
        if (tripMeta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Trip metadata not found");
        }

        rideFlowRepository.upsertTripFeedback(
                request.tripId(),
                tripMeta.driverId(),
                request.rating(),
                request.tipAmount(),
                request.comment()
        );

        RideFlowRepository.RatingAggregate aggregate = rideFlowRepository.aggregateRatings(tripMeta.driverId());
        BigDecimal avgRating = clampRating(aggregate.averageRating());
        rideFlowRepository.upsertRatingSummary(tripMeta.driverId(), avgRating, aggregate.totalTripsRated());
        rideFlowRepository.upsertRatingStrength(tripMeta.driverId(), "Clean Car", clampRating(avgRating.add(BigDecimal.valueOf(0.10))));
        rideFlowRepository.upsertRatingStrength(tripMeta.driverId(), "Safe Driving", avgRating);
        rideFlowRepository.upsertRatingStrength(tripMeta.driverId(), "On-time Pickup and Dropoff", clampRating(avgRating.subtract(BigDecimal.valueOf(0.10))));

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

    private DriverRef resolveDriver(String driverId, String driverName) {
        if (!isBlank(driverId)) {
            try {
                UUID driverUuid = UUID.fromString(driverId.trim());
                return rideFlowRepository.findDriverById(driverUuid)
                        .map(driver -> new DriverRef(driver.driverId(), driver.name()))
                        .orElse(null);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        if (isBlank(driverName)) {
            return null;
        }
        return rideFlowRepository.findDriverByName(driverName.trim())
                .map(driver -> new DriverRef(driver.driverId(), driver.name()))
                .orElse(null);
    }

    private BigDecimal clampRating(BigDecimal rating) {
        if (rating == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal rounded = rating.setScale(2, java.math.RoundingMode.HALF_UP);
        if (rounded.compareTo(BigDecimal.ONE) < 0) {
            return BigDecimal.ONE;
        }
        if (rounded.compareTo(BigDecimal.valueOf(5)) > 0) {
            return BigDecimal.valueOf(5);
        }
        return rounded;
    }

    public record RideEstimateRequest(String source, String destination, String departureTime) {
    }

    public record AccuracyCheckRequest(
            int sampleId,
            String sourceLabel,
            String destinationLabel,
            String pickupDateTime,
            double tripDistance,
            String pickupLocationId,
            String dropoffLocationId,
            double actualDurationSeconds,
            double actualFare,
            double actualTolls,
            double actualTotalNoTip
    ) {
    }

    public record AccuracyCheckResponse(
            int sampleId,
            String sourceLabel,
            String destinationLabel,
            String pickupDateTime,
            double tripDistance,
            String pickupLocationId,
            String dropoffLocationId,
            double actualEtaMinutes,
            double predictedEtaMinutes,
            double etaDifferenceMinutes,
            double actualFare,
            double predictedFare,
            double fareDifference,
            double actualTolls,
            double predictedTolls,
            double actualTotalNoTip,
            double predictedTotalNoTip,
            double totalDifference,
            double absoluteTotalDifference,
            String currency,
            String modelVersion,
            boolean fallbackUsed
    ) {
    }

    public record CreateRideRequest(
            String riderId,
            String source,
            String destination,
            String departureTime,
            double estimatedFare,
            String currency
    ) {
    }

    public record RideEstimateResponse(
            String source,
            String destination,
            String departureTime,
            int estimatedTimeMinutes,
            double estimatedFare,
            String currency,
            List<AvailableDriver> availableDrivers,
            String modelVersion,
            boolean fallbackUsed
    ) {
    }

    public record AvailableDriver(
            String driverId,
            String name,
            String vehicle,
            int etaMinutes,
            double rating
    ) {
    }

    public record NegotiateFareRequest(
            String requestId,
            String riderId,
            String source,
            String destination,
            String departureTime,
            String driverId,
            String driverName,
            double proposedFare
    ) {
    }

    public record TrackingPreviewRequest(
            String source,
            String destination,
            String driverId,
            String driverName
    ) {
    }

    public record FinalizeTripRequest(
            String requestId,
            String riderId,
            String source,
            String destination,
            String departureTime,
            String driverId,
            String driverName,
            double finalFare,
            String paymentOption
    ) {
    }

    public record AcceptFinalFareRequest(String requestId, String riderId, String driverId) {
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

    public record CompleteTripRequest(String tripId, String tripPin, String requestId) {
    }

    public record FeedbackRequest(String tripId, double tipAmount, int rating, String comment) {
    }

    private record DriverRef(UUID driverId, String name) {
    }

    private record TripMeta(UUID driverId, double finalFare, LocalDate rideDate) {
    }
}
