package com.example.vianova;

public record RiderOnboardingRequest(
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String passwordHash
) {
}
