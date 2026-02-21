package com.example.vianova;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class DriverOnboardingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DriverOnboardingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID save(DriverOnboardingRequest request) {
        UUID driverId = UUID.randomUUID();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("driverId", driverId)
                .addValue("name", request.name())
                .addValue("age", request.age())
                .addValue("sex", request.sex())
                .addValue("socialAddress", request.socialAddress())
                .addValue("experience", request.experience())
                .addValue("email", request.email())
                .addValue("phoneNumber", request.phoneNumber())
                .addValue("passwordHash", request.passwordHash())
                .addValue("drivingLicenseNumber", request.drivingLicenseNumber())
                .addValue("licenseDocument", request.licenseDocument())
                .addValue("licenseDocumentName", request.licenseDocumentName())
                .addValue("licenseDocumentContentType", request.licenseDocumentContentType());

        jdbcTemplate.update("""
                INSERT INTO dbo.driver_onboarding (
                    driver_id,
                    name,
                    age,
                    sex,
                    social_address,
                    experience_years,
                    email,
                    phone_number,
                    password_hash,
                    driving_license_number,
                    license_document,
                    license_document_name,
                    license_document_content_type
                )
                VALUES (
                    :driverId,
                    :name,
                    :age,
                    :sex,
                    :socialAddress,
                    :experience,
                    :email,
                    :phoneNumber,
                    :passwordHash,
                    :drivingLicenseNumber,
                    :licenseDocument,
                    :licenseDocumentName,
                    :licenseDocumentContentType
                )
                """, params);

        return driverId;
    }

    public Optional<DriverLoginRow> findByEmail(String email) {
        return jdbcTemplate.query("""
                        SELECT driver_id, email, password_hash
                        FROM dbo.driver_onboarding
                        WHERE email = :email
                        """,
                new MapSqlParameterSource().addValue("email", email),
                (rs, rowNum) -> new DriverLoginRow(
                        UUID.fromString(rs.getString("driver_id")),
                        rs.getString("email"),
                        rs.getString("password_hash")
                )
        ).stream().findFirst();
    }

    public record DriverLoginRow(UUID driverId, String email, String passwordHash) {
    }
}
