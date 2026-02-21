package com.example.vianova;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/riders")
public class RiderOnboardingController {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final RiderOnboardingRepository riderOnboardingRepository;

    public RiderOnboardingController(RiderOnboardingRepository riderOnboardingRepository) {
        this.riderOnboardingRepository = riderOnboardingRepository;
    }

    @PostMapping("/onboard")
    public ResponseEntity<?> onboard(@RequestBody RiderOnboardingPayload payload) {
        if (payload == null || isBlank(payload.firstName()) || isBlank(payload.lastName())
                || isBlank(payload.email()) || isBlank(payload.phoneNumber())
                || isBlank(payload.password()) || isBlank(payload.confirmPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("firstName, lastName, email, phoneNumber, password and confirmPassword are required");
        }
        if (!payload.password().equals(payload.confirmPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("password and confirmPassword must match");
        }
        if (payload.password().length() < 8) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("password must be at least 8 characters");
        }
        if (!payload.email().contains("@")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("email must be valid");
        }

        String email = payload.email().trim().toLowerCase();
        UUID riderId;
        try {
            RiderOnboardingRequest request = new RiderOnboardingRequest(
                    payload.firstName().trim(),
                    payload.lastName().trim(),
                    email,
                    payload.phoneNumber().trim(),
                    PASSWORD_ENCODER.encode(payload.password())
            );
            riderId = riderOnboardingRepository.save(request);
        } catch (DuplicateKeyException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Rider already exists with the same email or phoneNumber");
        } catch (DataAccessException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to save rider data");
        }

        return ResponseEntity.ok(Map.of(
                "riderId", riderId,
                "firstName", payload.firstName().trim(),
                "status", "Rider onboarding completed"
        ));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record RiderOnboardingPayload(
            String firstName,
            String lastName,
            String email,
            String phoneNumber,
            String password,
            String confirmPassword
    ) {
    }
}
