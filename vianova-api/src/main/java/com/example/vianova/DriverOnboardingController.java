package com.example.vianova;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/drivers")
public class DriverOnboardingController {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final DriverOnboardingRepository driverOnboardingRepository;

    public DriverOnboardingController(DriverOnboardingRepository driverOnboardingRepository) {
        this.driverOnboardingRepository = driverOnboardingRepository;
    }

    @PostMapping(value = "/onboard", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> onboardDriver(
            @RequestParam String name,
            @RequestParam int age,
            @RequestParam String sex,
            @RequestParam("socialAddress") String socialAddress,
            @RequestParam int experience,
            @RequestParam String email,
            @RequestParam String phoneNumber,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam("drivingLicenseNumber") String drivingLicenseNumber,
            @RequestParam("licenseDocument") MultipartFile licenseDocument
    ) {
        if (isBlank(name) || isBlank(sex) || isBlank(socialAddress) || isBlank(drivingLicenseNumber)
                || isBlank(email) || isBlank(phoneNumber) || isBlank(password) || isBlank(confirmPassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("name, sex, socialAddress, drivingLicenseNumber, email, phoneNumber, password and confirmPassword are required");
        }
        if (age < 18) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Driver age must be at least 18");
        }
        if (experience < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("experience must be zero or positive");
        }
        if (licenseDocument == null || licenseDocument.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Scanned copy of license is required");
        }
        if (!password.equals(confirmPassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("password and confirmPassword must match");
        }
        if (!email.contains("@")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("email must be valid");
        }
        if (password.length() < 8) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("password must be at least 8 characters");
        }

        UUID driverId;
        try {
            DriverOnboardingRequest request = new DriverOnboardingRequest(
                    name,
                    age,
                    sex,
                    socialAddress,
                    experience,
                    email.trim().toLowerCase(),
                    phoneNumber.trim(),
                    PASSWORD_ENCODER.encode(password),
                    drivingLicenseNumber,
                    licenseDocument.getBytes(),
                    licenseDocument.getOriginalFilename(),
                    licenseDocument.getContentType()
            );
            driverId = driverOnboardingRepository.save(request);
        } catch (DuplicateKeyException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Driver already exists with the same drivingLicenseNumber or email");
        } catch (DataAccessException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Unable to save driver data");
        } catch (IOException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Unable to read the license document");
        }

        return ResponseEntity.ok(Map.of(
                "driverId", driverId,
                "driverName", name,
                "email", email.trim().toLowerCase(),
                "status", "Driver is Onboarded and clearance pending will be send via mail"
        ));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
