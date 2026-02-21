package com.example.vianova;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/drivers")
public class DriverOnboardingController {

    @PostMapping(value = "/onboard", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> onboardDriver(
            @RequestParam String name,
            @RequestParam int age,
            @RequestParam String sex,
            @RequestParam("socialAddress") String socialAddress,
            @RequestParam int experience,
            @RequestParam("drivingLicenseNumber") String drivingLicenseNumber,
            @RequestParam("licenseDocument") MultipartFile licenseDocument
    ) {
        if (isBlank(name) || isBlank(sex) || isBlank(socialAddress) || isBlank(drivingLicenseNumber)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("name, sex, socialAddress and drivingLicenseNumber are required");
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

        return ResponseEntity.ok(Map.of(
                "driverName", name,
                "status", "Driver is Onboarded and clearance pending will be send via mail"
        ));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
