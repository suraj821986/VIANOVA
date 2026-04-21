package com.example.vianova;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DriverDashboardRepository {

    private static final int REQUEST_TTL_MINUTES = 5;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DriverDashboardRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RideRow> findPreviousRides(UUID driverId) {
        return jdbcTemplate.query("""
                        SELECT ride_id, source, destination, fare, ride_date
                        FROM dbo.driver_previous_rides
                        WHERE driver_id = :driverId
                        ORDER BY ride_date DESC, created_at DESC
                        """,
                new MapSqlParameterSource("driverId", driverId),
                (rs, rowNum) -> new RideRow(
                        rs.getString("ride_id"),
                        rs.getString("source"),
                        rs.getString("destination"),
                        rs.getBigDecimal("fare"),
                        rs.getDate("ride_date").toLocalDate().toString()
                ));
    }

    public Optional<RatingSummaryRow> findRatingSummary(UUID driverId) {
        return jdbcTemplate.query("""
                        SELECT overall_rating, total_trips_rated
                        FROM dbo.driver_rating_summary
                        WHERE driver_id = :driverId
                        """,
                new MapSqlParameterSource("driverId", driverId),
                (rs, rowNum) -> new RatingSummaryRow(
                        rs.getBigDecimal("overall_rating"),
                        rs.getInt("total_trips_rated")
                )).stream().findFirst();
    }

    public List<RatingStrengthRow> findRatingStrengths(UUID driverId) {
        return jdbcTemplate.query("""
                        SELECT strength_name, score
                        FROM dbo.driver_rating_strength
                        WHERE driver_id = :driverId
                        ORDER BY strength_name
                        """,
                new MapSqlParameterSource("driverId", driverId),
                (rs, rowNum) -> new RatingStrengthRow(
                        rs.getString("strength_name"),
                        rs.getBigDecimal("score")
                ));
    }

    public List<CarRow> findCars(UUID driverId) {
        return jdbcTemplate.query("""
                        SELECT car_id, model, plate_number, color
                        FROM dbo.driver_cars
                        WHERE driver_id = :driverId
                        ORDER BY created_at DESC
                        """,
                new MapSqlParameterSource("driverId", driverId),
                (rs, rowNum) -> new CarRow(
                        rs.getLong("car_id"),
                        rs.getString("model"),
                        rs.getString("plate_number"),
                        rs.getString("color")
                ));
    }

    public boolean existsCar(UUID driverId, String plateNumber) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1)
                        FROM dbo.driver_cars
                        WHERE driver_id = :driverId AND plate_number = :plateNumber
                        """,
                new MapSqlParameterSource()
                        .addValue("driverId", driverId)
                        .addValue("plateNumber", plateNumber),
                Integer.class);
        return count != null && count > 0;
    }

    public void addCar(UUID driverId, String model, String plateNumber, String color) {
        jdbcTemplate.update("""
                        INSERT INTO dbo.driver_cars (driver_id, model, plate_number, color)
                        VALUES (:driverId, :model, :plateNumber, :color)
                        """,
                new MapSqlParameterSource()
                        .addValue("driverId", driverId)
                        .addValue("model", model)
                        .addValue("plateNumber", plateNumber)
                        .addValue("color", color));
    }

    public int deleteCar(UUID driverId, long carId) {
        return jdbcTemplate.update("""
                        DELETE FROM dbo.driver_cars
                        WHERE driver_id = :driverId
                          AND car_id = :carId
                        """,
                new MapSqlParameterSource()
                        .addValue("driverId", driverId)
                        .addValue("carId", carId));
    }

    public void upsertContact(UUID driverId, String phone, String email, String address) {
        jdbcTemplate.update("""
                        MERGE dbo.driver_contact_info AS target
                        USING (
                            SELECT :driverId AS driver_id,
                                   :phone AS phone,
                                   :email AS email,
                                   :address AS address
                        ) AS source
                        ON target.driver_id = source.driver_id
                        WHEN MATCHED THEN
                            UPDATE SET
                                phone = source.phone,
                                email = source.email,
                                address = source.address,
                                updated_at = SYSUTCDATETIME()
                        WHEN NOT MATCHED THEN
                            INSERT (driver_id, phone, email, address, updated_at)
                            VALUES (source.driver_id, source.phone, source.email, source.address, SYSUTCDATETIME());
                        """,
                new MapSqlParameterSource()
                        .addValue("driverId", driverId)
                        .addValue("phone", phone)
                        .addValue("email", email)
                        .addValue("address", address));
    }

    public List<RideRequestRow> findRideRequests(UUID driverId) {
        expireOpenRideRequests();
        return jdbcTemplate.query("""
                        SELECT
                            rr.request_id,
                            rr.rider_id,
                            rr.assigned_driver_id,
                            rr.source,
                            rr.destination,
                            rr.departure_time,
                            rr.estimated_fare,
                            rr.currency,
                            rr.status,
                            rr.rider_offer,
                            rr.counter_offer,
                            rr.rider_final_acceptance,
                            rr.driver_final_acceptance,
                            ro.first_name,
                            ro.last_name
                        FROM dbo.ride_requests rr
                        INNER JOIN dbo.rider_onboarding ro ON ro.rider_id = rr.rider_id
                        WHERE ((rr.status = 'PENDING_DRIVER'
                               AND rr.assigned_driver_id IS NULL
                               AND rr.created_at >= DATEADD(MINUTE, -:ttlMinutes, SYSUTCDATETIME()))
                           OR rr.assigned_driver_id = :driverId)
                          AND rr.status <> 'VOID'
                        ORDER BY
                            CASE WHEN rr.status = 'PENDING_DRIVER' THEN 0 ELSE 1 END,
                            rr.updated_at DESC,
                            rr.created_at DESC
                        """,
                new MapSqlParameterSource()
                        .addValue("driverId", driverId)
                        .addValue("ttlMinutes", REQUEST_TTL_MINUTES),
                (rs, rowNum) -> new RideRequestRow(
                        UUID.fromString(rs.getString("request_id")),
                        UUID.fromString(rs.getString("rider_id")),
                        uuidOrNull(rs.getString("assigned_driver_id")),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getString("source"),
                        rs.getString("destination"),
                        rs.getTimestamp("departure_time").toLocalDateTime(),
                        rs.getBigDecimal("estimated_fare"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        rs.getBigDecimal("rider_offer"),
                        rs.getBigDecimal("counter_offer"),
                        rs.getBoolean("rider_final_acceptance"),
                        rs.getBoolean("driver_final_acceptance")
                ));
    }

    public int expireOpenRideRequests() {
        return jdbcTemplate.update("""
                        UPDATE dbo.ride_requests
                        SET status = 'VOID',
                            updated_at = SYSUTCDATETIME()
                        WHERE status IN ('PENDING_DRIVER', 'ACCEPTED_BY_DRIVER', 'NEGOTIATING')
                          AND created_at < DATEADD(MINUTE, -:ttlMinutes, SYSUTCDATETIME())
                        """,
                new MapSqlParameterSource("ttlMinutes", REQUEST_TTL_MINUTES));
    }

    public Optional<RideRequestRow> acceptRideRequest(UUID requestId, UUID driverId) {
        expireOpenRideRequests();
        int updated = jdbcTemplate.update("""
                        UPDATE dbo.ride_requests
                        SET assigned_driver_id = :driverId,
                            status = 'ACCEPTED_BY_DRIVER',
                            accepted_at = COALESCE(accepted_at, SYSUTCDATETIME()),
                            updated_at = SYSUTCDATETIME()
                        WHERE request_id = :requestId
                          AND (
                                (
                                    status = 'PENDING_DRIVER'
                                    AND assigned_driver_id IS NULL
                                    AND created_at >= DATEADD(MINUTE, -:ttlMinutes, SYSUTCDATETIME())
                                )
                                OR (assigned_driver_id = :driverId AND status IN ('ACCEPTED_BY_DRIVER', 'NEGOTIATING'))
                          )
                        """,
                new MapSqlParameterSource()
                        .addValue("requestId", requestId)
                        .addValue("driverId", driverId)
                        .addValue("ttlMinutes", REQUEST_TTL_MINUTES));
        if (updated == 0) {
            return Optional.empty();
        }
        return findRideRequestById(requestId);
    }

    public Optional<RideRequestRow> submitCounterOffer(UUID requestId, UUID driverId, double counterOffer) {
        expireOpenRideRequests();
        int updated = jdbcTemplate.update("""
                        UPDATE dbo.ride_requests
                        SET assigned_driver_id = :driverId,
                            status = 'NEGOTIATING',
                            counter_offer = :counterOffer,
                            rider_final_acceptance = 0,
                            driver_final_acceptance = 0,
                            accepted_at = COALESCE(accepted_at, SYSUTCDATETIME()),
                            updated_at = SYSUTCDATETIME()
                        WHERE request_id = :requestId
                          AND assigned_driver_id = :driverId
                          AND status IN ('ACCEPTED_BY_DRIVER', 'NEGOTIATING')
                        """,
                new MapSqlParameterSource()
                        .addValue("requestId", requestId)
                        .addValue("driverId", driverId)
                        .addValue("counterOffer", BigDecimal.valueOf(counterOffer)));
        if (updated == 0) {
            return Optional.empty();
        }
        return findRideRequestById(requestId);
    }

    public Optional<RideRequestRow> acceptFinalFare(UUID requestId, UUID driverId) {
        expireOpenRideRequests();
        int updated = jdbcTemplate.update("""
                        UPDATE dbo.ride_requests
                        SET driver_final_acceptance = 1,
                            updated_at = SYSUTCDATETIME()
                        WHERE request_id = :requestId
                          AND assigned_driver_id = :driverId
                          AND counter_offer IS NOT NULL
                          AND rider_final_acceptance = 1
                          AND status IN ('NEGOTIATING', 'ACCEPTED_BY_DRIVER')
                        """,
                new MapSqlParameterSource()
                        .addValue("requestId", requestId)
                        .addValue("driverId", driverId));
        if (updated == 0) {
            return Optional.empty();
        }
        return findRideRequestById(requestId);
    }

    private Optional<RideRequestRow> findRideRequestById(UUID requestId) {
        expireOpenRideRequests();
        return jdbcTemplate.query("""
                        SELECT
                            rr.request_id,
                            rr.rider_id,
                            rr.assigned_driver_id,
                            rr.source,
                            rr.destination,
                            rr.departure_time,
                            rr.estimated_fare,
                            rr.currency,
                            rr.status,
                            rr.rider_offer,
                            rr.counter_offer,
                            rr.rider_final_acceptance,
                            rr.driver_final_acceptance,
                            ro.first_name,
                            ro.last_name
                        FROM dbo.ride_requests rr
                        INNER JOIN dbo.rider_onboarding ro ON ro.rider_id = rr.rider_id
                        WHERE rr.request_id = :requestId
                          AND rr.status <> 'VOID'
                        """,
                new MapSqlParameterSource("requestId", requestId),
                (rs, rowNum) -> new RideRequestRow(
                        UUID.fromString(rs.getString("request_id")),
                        UUID.fromString(rs.getString("rider_id")),
                        uuidOrNull(rs.getString("assigned_driver_id")),
                        rs.getString("first_name") + " " + rs.getString("last_name"),
                        rs.getString("source"),
                        rs.getString("destination"),
                        rs.getTimestamp("departure_time").toLocalDateTime(),
                        rs.getBigDecimal("estimated_fare"),
                        rs.getString("currency"),
                        rs.getString("status"),
                        rs.getBigDecimal("rider_offer"),
                        rs.getBigDecimal("counter_offer"),
                        rs.getBoolean("rider_final_acceptance"),
                        rs.getBoolean("driver_final_acceptance")
                )).stream().findFirst();
    }

    private UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    public record RideRow(String rideId, String source, String destination, BigDecimal fare, String date) {
    }

    public record RatingSummaryRow(BigDecimal overallRating, int totalTripsRated) {
    }

    public record RatingStrengthRow(String name, BigDecimal score) {
    }

    public record CarRow(long carId, String model, String plateNumber, String color) {
    }

    public record RideRequestRow(
            UUID requestId,
            UUID riderId,
            UUID assignedDriverId,
            String riderName,
            String source,
            String destination,
            LocalDateTime departureTime,
            BigDecimal estimatedFare,
            String currency,
            String status,
            BigDecimal riderOffer,
            BigDecimal counterOffer,
            boolean riderFinalAcceptance,
            boolean driverFinalAcceptance
    ) {
    }
}
