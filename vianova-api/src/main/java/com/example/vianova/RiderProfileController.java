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
@RequestMapping("/riders")
public class RiderProfileController {

    private static final Map<String, List<String>> RIDER_ADDRESSES = new ConcurrentHashMap<>();
    private static final Map<String, List<PaymentOption>> RIDER_PAYMENTS = new ConcurrentHashMap<>();

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestParam String riderId) {
        if (isBlank(riderId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "riderId is required"));
        }

        return ResponseEntity.ok(Map.of(
                "riderId", riderId,
                "homeAddresses", getOrCreateAddresses(riderId),
                "paymentOptions", getOrCreatePayments(riderId)
        ));
    }

    @PostMapping("/profile/address")
    public ResponseEntity<?> addAddress(@RequestBody AddAddressRequest request) {
        if (request == null || isBlank(request.riderId()) || isBlank(request.address())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "riderId and address are required"));
        }

        List<String> addresses = getOrCreateAddresses(request.riderId());
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
        if (request == null || isBlank(request.riderId()) || isBlank(request.type()) || isBlank(request.details())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "riderId, type and details are required"));
        }

        List<PaymentOption> payments = getOrCreatePayments(request.riderId());
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

    private List<String> getOrCreateAddresses(String riderId) {
        return RIDER_ADDRESSES.computeIfAbsent(riderId, id -> {
            List<String> defaults = new ArrayList<>();
            defaults.add("Apartment 12, Lake View");
            return defaults;
        });
    }

    private List<PaymentOption> getOrCreatePayments(String riderId) {
        return RIDER_PAYMENTS.computeIfAbsent(riderId, id -> {
            List<PaymentOption> defaults = new ArrayList<>();
            defaults.add(new PaymentOption("Card", "**** 2291"));
            return defaults;
        });
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record AddAddressRequest(String riderId, String address) {
    }

    public record AddPaymentRequest(String riderId, String type, String details) {
    }

    public record PaymentOption(String type, String details) {
    }
}
