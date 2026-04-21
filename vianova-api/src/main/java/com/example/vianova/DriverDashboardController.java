package com.example.vianova;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/drivers")
public class DriverDashboardController {

    private static final Map<String, List<String>> DRIVER_ADDRESSES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, List<PaymentOption>> DRIVER_PAYMENTS = new java.util.concurrent.ConcurrentHashMap<>();

    private final DriverDashboardRepository driverDashboardRepository;

    public DriverDashboardController(DriverDashboardRepository driverDashboardRepository) {
        this.driverDashboardRepository = driverDashboardRepository;
    }

    @GetMapping("/rides")
    public ResponseEntity<?> previousRides(@RequestParam String driverId) {
        UUID driverUuid = parseDriverId(driverId);
        if (driverUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("driverId is required");
        }

        var rides = driverDashboardRepository.findPreviousRides(driverUuid).stream()
                .map(ride -> Map.of(
                        "rideId", ride.rideId(),
                        "source", ride.source(),
                        "destination", ride.destination(),
                        "fare", ride.fare(),
                        "date", ride.date()
                ))
                .toList();
        return ResponseEntity.ok(Map.of("driverId", driverId, "rides", rides));
    }

    @GetMapping("/ride-requests")
    public ResponseEntity<?> rideRequests(@RequestParam String driverId) {
        UUID driverUuid = parseDriverId(driverId);
        if (driverUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "driverId is required"));
        }

        var requests = driverDashboardRepository.findRideRequests(driverUuid).stream()
                .map(request -> Map.ofEntries(
                        Map.entry("requestId", request.requestId()),
                        Map.entry("riderId", request.riderId()),
                        Map.entry("riderName", request.riderName()),
                        Map.entry("source", request.source()),
                        Map.entry("destination", request.destination()),
                        Map.entry("departureTime", request.departureTime()),
                        Map.entry("estimatedFare", request.estimatedFare()),
                        Map.entry("currency", request.currency()),
                        Map.entry("status", request.status()),
                        Map.entry("riderOffer", request.riderOffer() == null ? "" : request.riderOffer()),
                        Map.entry("counterOffer", request.counterOffer() == null ? "" : request.counterOffer()),
                        Map.entry("riderFinalAcceptance", request.riderFinalAcceptance()),
                        Map.entry("driverFinalAcceptance", request.driverFinalAcceptance()),
                        Map.entry("assignedDriverId", request.assignedDriverId() == null ? "" : request.assignedDriverId())
                ))
                .toList();
        return ResponseEntity.ok(Map.of(
                "driverId", driverId,
                "requests", requests
        ));
    }

    @PostMapping("/ride-requests/accept")
    public ResponseEntity<?> acceptRideRequest(@RequestBody AcceptRideRequest request) {
        if (request == null || isBlank(request.driverId()) || isBlank(request.requestId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId and requestId are required"));
        }
        UUID driverUuid = parseDriverId(request.driverId());
        UUID requestUuid = parseUuid(request.requestId());
        if (driverUuid == null || requestUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId and requestId must be valid UUIDs"));
        }

        return driverDashboardRepository.acceptRideRequest(requestUuid, driverUuid)
                .<ResponseEntity<?>>map(accepted -> ResponseEntity.ok(Map.ofEntries(
                        Map.entry("message", "Ride request accepted. You can now start negotiating with the rider."),
                        Map.entry("requestId", accepted.requestId()),
                        Map.entry("status", accepted.status()),
                        Map.entry("source", accepted.source()),
                        Map.entry("destination", accepted.destination()),
                        Map.entry("departureTime", accepted.departureTime()),
                        Map.entry("estimatedFare", accepted.estimatedFare()),
                        Map.entry("currency", accepted.currency()),
                        Map.entry("riderName", accepted.riderName()),
                        Map.entry("riderOffer", accepted.riderOffer() == null ? "" : accepted.riderOffer()),
                        Map.entry("counterOffer", accepted.counterOffer() == null ? "" : accepted.counterOffer()),
                        Map.entry("riderFinalAcceptance", accepted.riderFinalAcceptance()),
                        Map.entry("driverFinalAcceptance", accepted.driverFinalAcceptance())
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Ride request is no longer available")));
    }

    @PostMapping("/ride-requests/negotiate")
    public ResponseEntity<?> negotiateRideRequest(@RequestBody NegotiateRideRequest request) {
        if (request == null || isBlank(request.driverId()) || isBlank(request.requestId()) || request.counterOffer() <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId, requestId and positive counterOffer are required"));
        }
        UUID driverUuid = parseDriverId(request.driverId());
        UUID requestUuid = parseUuid(request.requestId());
        if (driverUuid == null || requestUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId and requestId must be valid UUIDs"));
        }

        return driverDashboardRepository.submitCounterOffer(requestUuid, driverUuid, request.counterOffer())
                .<ResponseEntity<?>>map(updated -> ResponseEntity.ok(Map.of(
                        "message", "Counter offer saved. Rider can continue the negotiation from this request.",
                        "requestId", updated.requestId(),
                        "status", updated.status(),
                        "riderOffer", updated.riderOffer() == null ? "" : updated.riderOffer(),
                        "counterOffer", updated.counterOffer(),
                        "riderFinalAcceptance", updated.riderFinalAcceptance(),
                        "driverFinalAcceptance", updated.driverFinalAcceptance(),
                        "currency", updated.currency(),
                        "riderName", updated.riderName()
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Accept the ride request before sending a counter offer")));
    }

    @PostMapping("/ride-requests/accept-final")
    public ResponseEntity<?> acceptFinalFare(@RequestBody AcceptRideRequest request) {
        if (request == null || isBlank(request.driverId()) || isBlank(request.requestId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId and requestId are required"));
        }
        UUID driverUuid = parseDriverId(request.driverId());
        UUID requestUuid = parseUuid(request.requestId());
        if (driverUuid == null || requestUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId and requestId must be valid UUIDs"));
        }

        return driverDashboardRepository.acceptFinalFare(requestUuid, driverUuid)
                .<ResponseEntity<?>>map(updated -> ResponseEntity.ok(Map.ofEntries(
                        Map.entry("message", "Driver accepted the final fare."),
                        Map.entry("requestId", updated.requestId()),
                        Map.entry("riderOffer", updated.riderOffer() == null ? "" : updated.riderOffer()),
                        Map.entry("counterOffer", updated.counterOffer() == null ? "" : updated.counterOffer()),
                        Map.entry("riderFinalAcceptance", updated.riderFinalAcceptance()),
                        Map.entry("driverFinalAcceptance", updated.driverFinalAcceptance())
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "No rider offer is available to accept yet")));
    }

    @PostMapping("/contact/update")
    public ResponseEntity<?> updateContact(@RequestBody ContactUpdateRequest request) {
        if (request == null || isBlank(request.driverId()) || isBlank(request.phone()) || isBlank(request.email()) || isBlank(request.address())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId, phone, email and address are required"));
        }

        UUID driverUuid = parseDriverId(request.driverId());
        if (driverUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId must be a valid UUID"));
        }

        driverDashboardRepository.upsertContact(
                driverUuid,
                request.phone().trim(),
                request.email().trim().toLowerCase(),
                request.address().trim()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Contact information updated successfully",
                "driverId", request.driverId()
        ));
    }

    @GetMapping("/ratings")
    public ResponseEntity<?> getRatings(@RequestParam String driverId) {
        UUID driverUuid = parseDriverId(driverId);
        if (driverUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "driverId is required"));
        }

        DriverDashboardRepository.RatingSummaryRow summary = driverDashboardRepository.findRatingSummary(driverUuid)
                .orElse(new DriverDashboardRepository.RatingSummaryRow(java.math.BigDecimal.ZERO, 0));
        var strengths = driverDashboardRepository.findRatingStrengths(driverUuid).stream()
                .map(item -> Map.of(
                        "name", item.name(),
                        "score", item.score()
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "driverId", driverId,
                "overallRating", summary.overallRating(),
                "totalTripsRated", summary.totalTripsRated(),
                "strengths", strengths
        ));
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestParam String driverId) {
        if (isBlank(driverId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "driverId is required"));
        }

        List<String> addresses = getOrCreateAddresses(driverId);
        List<PaymentOption> payments = getOrCreatePayments(driverId);
        return ResponseEntity.ok(Map.of(
                "driverId", driverId,
                "homeAddresses", addresses,
                "paymentOptions", payments
        ));
    }

    @PostMapping("/profile/address")
    public ResponseEntity<?> addAddress(@RequestBody AddAddressRequest request) {
        if (request == null || isBlank(request.driverId()) || isBlank(request.address())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId and address are required"));
        }

        List<String> addresses = getOrCreateAddresses(request.driverId());
        boolean exists = addresses.stream().anyMatch(item -> item.equalsIgnoreCase(request.address()));
        if (exists) {
            return ResponseEntity.ok(Map.of(
                    "message", "Address already exists",
                    "homeAddresses", addresses
            ));
        }
        addresses.add(request.address());
        return ResponseEntity.ok(Map.of(
                "message", "Address added successfully",
                "homeAddresses", addresses
        ));
    }

    @PostMapping("/profile/payment")
    public ResponseEntity<?> addPayment(@RequestBody AddPaymentRequest request) {
        if (request == null || isBlank(request.driverId()) || isBlank(request.type()) || isBlank(request.details())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId, type and details are required"));
        }

        List<PaymentOption> payments = getOrCreatePayments(request.driverId());
        boolean exists = payments.stream()
                .anyMatch(option -> option.type().equalsIgnoreCase(request.type()) && option.details().equalsIgnoreCase(request.details()));
        if (exists) {
            return ResponseEntity.ok(Map.of(
                    "message", "Payment option already exists",
                    "paymentOptions", payments
            ));
        }

        payments.add(new PaymentOption(request.type(), request.details()));
        return ResponseEntity.ok(Map.of(
                "message", "Payment option added successfully",
                "paymentOptions", payments
        ));
    }

    @GetMapping("/cars")
    public ResponseEntity<?> getCars(@RequestParam String driverId) {
        UUID driverUuid = parseDriverId(driverId);
        if (driverUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "driverId is required"));
        }

        List<CarInfo> cars = driverDashboardRepository.findCars(driverUuid).stream()
                .map(car -> new CarInfo(car.carId(), car.model(), car.plateNumber(), car.color()))
                .toList();
        return ResponseEntity.ok(Map.of(
                "driverId", driverId,
                "cars", cars
        ));
    }

    @PostMapping("/cars")
    public ResponseEntity<?> addCar(@RequestBody AddCarRequest request) {
        if (request == null || isBlank(request.driverId()) || isBlank(request.model()) || isBlank(request.plateNumber()) || isBlank(request.color())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId, model, plateNumber and color are required"));
        }

        UUID driverUuid = parseDriverId(request.driverId());
        if (driverUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId must be a valid UUID"));
        }

        String plateNumber = request.plateNumber().trim();
        boolean exists = driverDashboardRepository.existsCar(driverUuid, plateNumber);
        List<CarInfo> cars = driverDashboardRepository.findCars(driverUuid).stream()
                .map(car -> new CarInfo(car.carId(), car.model(), car.plateNumber(), car.color()))
                .toList();
        if (exists) {
            return ResponseEntity.ok(Map.of(
                    "message", "Car already exists",
                    "driverId", request.driverId(),
                    "cars", cars
            ));
        }

        driverDashboardRepository.addCar(
                driverUuid,
                request.model().trim(),
                plateNumber,
                request.color().trim()
        );
        cars = driverDashboardRepository.findCars(driverUuid).stream()
                .map(car -> new CarInfo(car.carId(), car.model(), car.plateNumber(), car.color()))
                .toList();
        return ResponseEntity.ok(Map.of(
                "message", "Car added successfully",
                "driverId", request.driverId(),
                "cars", cars
        ));
    }

    @PostMapping("/cars/delete")
    public ResponseEntity<?> deleteCar(@RequestBody DeleteCarRequest request) {
        if (request == null || isBlank(request.driverId()) || request.carId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId and carId are required"));
        }
        UUID driverUuid = parseDriverId(request.driverId());
        if (driverUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId must be a valid UUID"));
        }

        int deleted = driverDashboardRepository.deleteCar(driverUuid, request.carId());
        if (deleted == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Car not found"));
        }

        List<CarInfo> cars = driverDashboardRepository.findCars(driverUuid).stream()
                .map(car -> new CarInfo(car.carId(), car.model(), car.plateNumber(), car.color()))
                .toList();
        return ResponseEntity.ok(Map.of(
                "message", "Car deleted successfully",
                "driverId", request.driverId(),
                "cars", cars
        ));
    }

    private List<String> getOrCreateAddresses(String driverId) {
        return DRIVER_ADDRESSES.computeIfAbsent(driverId, id -> {
            List<String> defaults = new ArrayList<>();
            defaults.add("742 Evergreen Terrace");
            return defaults;
        });
    }

    private List<PaymentOption> getOrCreatePayments(String driverId) {
        return DRIVER_PAYMENTS.computeIfAbsent(driverId, id -> {
            List<PaymentOption> defaults = new ArrayList<>();
            defaults.add(new PaymentOption("UPI", "driver@upi"));
            return defaults;
        });
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private UUID parseDriverId(String driverId) {
        if (isBlank(driverId)) {
            return null;
        }
        try {
            return UUID.fromString(driverId.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private UUID parseUuid(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public record ContactUpdateRequest(String driverId, String phone, String email, String address) {
    }

    public record AddCarRequest(String driverId, String model, String plateNumber, String color) {
    }

    public record AcceptRideRequest(String driverId, String requestId) {
    }

    public record NegotiateRideRequest(String driverId, String requestId, double counterOffer) {
    }

    public record CarInfo(Long carId, String model, String plateNumber, String color) {
    }

    public record DeleteCarRequest(String driverId, Long carId) {
    }

    public record AddAddressRequest(String driverId, String address) {
    }

    public record AddPaymentRequest(String driverId, String type, String details) {
    }

    public record PaymentOption(String type, String details) {
    }
}
