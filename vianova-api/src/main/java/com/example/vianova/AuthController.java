package com.example.vianova;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping("/rider/login")
    public ResponseEntity<?> riderLogin(@RequestBody LoginRequest request) {
        if (invalid(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "emailOrId and password are required"));
        }

        return ResponseEntity.ok(Map.of(
                "userType", "RIDER",
                "message", "Rider login successful",
                "token", "rider-" + UUID.randomUUID()
        ));
    }

    @PostMapping("/driver/login")
    public ResponseEntity<?> driverLogin(@RequestBody LoginRequest request) {
        if (invalid(request)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "emailOrId and password are required"));
        }

        boolean onboardingRequired = request.emailOrId().toLowerCase().contains("new");
        return ResponseEntity.ok(Map.of(
                "userType", "DRIVER",
                "message", "Driver login successful",
                "token", "driver-" + UUID.randomUUID(),
                "onboardingRequired", onboardingRequired
        ));
    }

    @PostMapping("/rider/logout")
    public ResponseEntity<?> riderLogout(@RequestBody LogoutRequest request) {
        if (request == null || request.userId() == null || request.userId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "userId is required"));
        }
        return ResponseEntity.ok(Map.of("message", "Rider logged out successfully"));
    }

    @PostMapping("/driver/logout")
    public ResponseEntity<?> driverLogout(@RequestBody LogoutRequest request) {
        if (request == null || request.userId() == null || request.userId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "userId is required"));
        }
        return ResponseEntity.ok(Map.of("message", "Driver logged out successfully"));
    }

    private boolean invalid(LoginRequest request) {
        return request == null
                || request.emailOrId() == null || request.emailOrId().isBlank()
                || request.password() == null || request.password().isBlank();
    }

    public record LoginRequest(String emailOrId, String password) {
    }

    public record LogoutRequest(String userId) {
    }
}
