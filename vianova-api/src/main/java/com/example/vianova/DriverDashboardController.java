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
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/drivers")
public class DriverDashboardController {

    private static final Map<String, List<CarInfo>> DRIVER_CARS = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> DRIVER_ADDRESSES = new ConcurrentHashMap<>();
    private static final Map<String, List<PaymentOption>> DRIVER_PAYMENTS = new ConcurrentHashMap<>();

    @GetMapping("/rides")
    public ResponseEntity<?> previousRides(@RequestParam String driverId) {
        if (driverId == null || driverId.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("driverId is required");
        }

        List<Map<String, Object>> rides = List.of(
                Map.of("rideId", "RIDE-1042", "source", "Downtown", "destination", "Airport", "fare", 26.40, "date", "2026-02-18"),
                Map.of("rideId", "RIDE-1043", "source", "Main Street", "destination", "Tech Park", "fare", 14.90, "date", "2026-02-19"),
                Map.of("rideId", "RIDE-1044", "source", "City Mall", "destination", "Central Station", "fare", 18.75, "date", "2026-02-20")
        );
        return ResponseEntity.ok(Map.of("driverId", driverId, "rides", rides));
    }

    @PostMapping("/contact/update")
    public ResponseEntity<?> updateContact(@RequestBody ContactUpdateRequest request) {
        if (request == null || isBlank(request.driverId()) || isBlank(request.phone()) || isBlank(request.email()) || isBlank(request.address())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "driverId, phone, email and address are required"));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Contact information updated successfully",
                "driverId", request.driverId()
        ));
    }

    @GetMapping("/ratings")
    public ResponseEntity<?> getRatings(@RequestParam String driverId) {
        if (isBlank(driverId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "driverId is required"));
        }

        return ResponseEntity.ok(Map.of(
                "driverId", driverId,
                "overallRating", 4.8,
                "totalTripsRated", 246,
                "strengths", List.of(
                        Map.of("name", "Clean Car", "score", 4.9),
                        Map.of("name", "Safe Driving", "score", 4.8),
                        Map.of("name", "On-time Pickup and Dropoff", "score", 4.7)
                )
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
        if (isBlank(driverId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "driverId is required"));
        }

        List<CarInfo> cars = getOrCreateCars(driverId);
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

        List<CarInfo> cars = getOrCreateCars(request.driverId());
        boolean exists = cars.stream().anyMatch(car -> car.plateNumber().equalsIgnoreCase(request.plateNumber()));
        if (exists) {
            return ResponseEntity.ok(Map.of(
                    "message", "Car already exists",
                    "driverId", request.driverId(),
                    "cars", cars
            ));
        }

        cars.add(new CarInfo(request.model(), request.plateNumber(), request.color()));
        return ResponseEntity.ok(Map.of(
                "message", "Car added successfully",
                "driverId", request.driverId(),
                "cars", cars
        ));
    }

    private List<CarInfo> getOrCreateCars(String driverId) {
        return DRIVER_CARS.computeIfAbsent(driverId, id -> {
            List<CarInfo> defaults = new ArrayList<>();
            defaults.add(new CarInfo("Toyota Prius", "MOCK-1001", "White"));
            return defaults;
        });
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

    public record ContactUpdateRequest(String driverId, String phone, String email, String address) {
    }

    public record AddCarRequest(String driverId, String model, String plateNumber, String color) {
    }

    public record CarInfo(String model, String plateNumber, String color) {
    }

    public record AddAddressRequest(String driverId, String address) {
    }

    public record AddPaymentRequest(String driverId, String type, String details) {
    }

    public record PaymentOption(String type, String details) {
    }
}
