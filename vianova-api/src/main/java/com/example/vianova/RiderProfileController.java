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
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/riders")
public class RiderProfileController {

    private static final Map<String, List<String>> RIDER_ADDRESSES = new ConcurrentHashMap<>();
    private static final Map<String, List<PaymentOption>> RIDER_PAYMENTS = new ConcurrentHashMap<>();
    private final RiderCardRepository riderCardRepository;
    private final RideFlowRepository rideFlowRepository;

    public RiderProfileController(RiderCardRepository riderCardRepository, RideFlowRepository rideFlowRepository) {
        this.riderCardRepository = riderCardRepository;
        this.rideFlowRepository = rideFlowRepository;
    }

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

    @GetMapping("/cards")
    public ResponseEntity<?> getSavedCards(@RequestParam String riderId) {
        UUID riderUuid = parseRiderId(riderId);
        if (riderUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "riderId must be a valid UUID"));
        }

        var cards = riderCardRepository.findByRiderId(riderUuid).stream()
                .map(card -> Map.of(
                        "cardId", card.cardId().toString(),
                        "cardHolderName", card.cardHolderName(),
                        "last4", card.last4(),
                        "expiryMonth", card.expiryMonth(),
                        "expiryYear", card.expiryYear(),
                        "label", "Card ****" + card.last4() + " (" + pad2(card.expiryMonth()) + "/" + card.expiryYear() + ")"
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "riderId", riderId,
                "savedCards", cards
        ));
    }

    @GetMapping("/rides")
    public ResponseEntity<?> getRides(
            @RequestParam String riderId,
            @RequestParam(defaultValue = "5") int limit
    ) {
        UUID riderUuid = parseRiderId(riderId);
        if (riderUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "riderId must be a valid UUID"));
        }

        int safeLimit = Math.max(1, Math.min(20, limit));
        var rides = rideFlowRepository.findRiderRideHistory(riderUuid, safeLimit).stream()
                .map(ride -> Map.ofEntries(
                        Map.entry("requestId", ride.requestId().toString()),
                        Map.entry("driverId", ride.assignedDriverId() == null ? "" : ride.assignedDriverId().toString()),
                        Map.entry("driverName", ride.driverName() == null ? "" : ride.driverName()),
                        Map.entry("source", ride.source()),
                        Map.entry("destination", ride.destination()),
                        Map.entry("departureTime", ride.departureTime()),
                        Map.entry("estimatedFare", ride.estimatedFare()),
                        Map.entry("finalFare", ride.counterOffer() == null ? ride.estimatedFare() : ride.counterOffer()),
                        Map.entry("currency", ride.currency()),
                        Map.entry("status", ride.status()),
                        Map.entry("riderOffer", ride.riderOffer() == null ? "" : ride.riderOffer()),
                        Map.entry("counterOffer", ride.counterOffer() == null ? "" : ride.counterOffer()),
                        Map.entry("updatedAt", ride.updatedAt())
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "riderId", riderId,
                "count", rides.size(),
                "rides", rides
        ));
    }

    @PostMapping("/cards/save")
    public ResponseEntity<?> saveCard(@RequestBody SaveCardRequest request) {
        if (request == null || isBlank(request.riderId()) || isBlank(request.cardHolderName())
                || isBlank(request.cardNumber())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "riderId, cardHolderName and cardNumber are required"));
        }

        UUID riderUuid = parseRiderId(request.riderId());
        if (riderUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "riderId must be a valid UUID"));
        }
        if (request.expiryMonth() < 1 || request.expiryMonth() > 12) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "expiryMonth must be between 1 and 12"));
        }
        if (request.expiryYear() < 2026 || request.expiryYear() > 2099) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "expiryYear must be between 2026 and 2099"));
        }

        String digits = request.cardNumber().replaceAll("\\D", "");
        if (digits.length() < 12 || digits.length() > 19) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "cardNumber must be 12 to 19 digits"));
        }

        String last4 = digits.substring(digits.length() - 4);
        String holder = request.cardHolderName().trim();
        if (riderCardRepository.exists(riderUuid, holder, last4, request.expiryMonth(), request.expiryYear())) {
            return ResponseEntity.ok(Map.of(
                    "message", "Card already saved",
                    "last4", last4
            ));
        }

        UUID cardId = riderCardRepository.save(riderUuid, holder, last4, request.expiryMonth(), request.expiryYear());
        return ResponseEntity.ok(Map.of(
                "message", "Card saved successfully",
                "cardId", cardId,
                "last4", last4
        ));
    }

    @PostMapping("/cards/delete")
    public ResponseEntity<?> deleteCard(@RequestBody DeleteCardRequest request) {
        if (request == null || isBlank(request.riderId()) || isBlank(request.cardId())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "riderId and cardId are required"));
        }

        UUID riderUuid = parseRiderId(request.riderId());
        if (riderUuid == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "riderId must be a valid UUID"));
        }
        UUID cardUuid;
        try {
            cardUuid = UUID.fromString(request.cardId().trim());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "cardId must be a valid UUID"));
        }

        int deleted = riderCardRepository.delete(riderUuid, cardUuid);
        if (deleted == 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Card not found"));
        }

        return ResponseEntity.ok(Map.of("message", "Card deleted successfully"));
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

    private UUID parseRiderId(String riderId) {
        if (isBlank(riderId)) {
            return null;
        }
        try {
            return UUID.fromString(riderId.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String pad2(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    public record AddAddressRequest(String riderId, String address) {
    }

    public record AddPaymentRequest(String riderId, String type, String details) {
    }

    public record PaymentOption(String type, String details) {
    }

    public record SaveCardRequest(
            String riderId,
            String cardHolderName,
            String cardNumber,
            int expiryMonth,
            int expiryYear
    ) {
    }

    public record DeleteCardRequest(String riderId, String cardId) {
    }
}
