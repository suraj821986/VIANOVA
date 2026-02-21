package com.example.vianova;

public record DriverOnboardingRequest(
        String name,
        int age,
        String sex,
        String socialAddress,
        int experience,
        String email,
        String phoneNumber,
        String passwordHash,
        String drivingLicenseNumber,
        byte[] licenseDocument,
        String licenseDocumentName,
        String licenseDocumentContentType
) {
}
