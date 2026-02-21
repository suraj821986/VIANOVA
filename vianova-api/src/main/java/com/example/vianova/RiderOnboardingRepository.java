package com.example.vianova;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class RiderOnboardingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RiderOnboardingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UUID save(RiderOnboardingRequest request) {
        UUID riderId = UUID.randomUUID();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("riderId", riderId)
                .addValue("firstName", request.firstName())
                .addValue("lastName", request.lastName())
                .addValue("email", request.email())
                .addValue("phoneNumber", request.phoneNumber())
                .addValue("passwordHash", request.passwordHash());

        jdbcTemplate.update("""
                INSERT INTO dbo.rider_onboarding (
                    rider_id,
                    first_name,
                    last_name,
                    email,
                    phone_number,
                    password_hash
                )
                VALUES (
                    :riderId,
                    :firstName,
                    :lastName,
                    :email,
                    :phoneNumber,
                    :passwordHash
                )
                """, params);

        return riderId;
    }

    public Optional<RiderLoginRow> findByEmail(String email) {
        return jdbcTemplate.query("""
                        SELECT rider_id, email, password_hash
                        FROM dbo.rider_onboarding
                        WHERE email = :email
                        """,
                new MapSqlParameterSource().addValue("email", email),
                (rs, rowNum) -> new RiderLoginRow(
                        UUID.fromString(rs.getString("rider_id")),
                        rs.getString("email"),
                        rs.getString("password_hash")
                )
        ).stream().findFirst();
    }

    public record RiderLoginRow(UUID riderId, String email, String passwordHash) {
    }
}
