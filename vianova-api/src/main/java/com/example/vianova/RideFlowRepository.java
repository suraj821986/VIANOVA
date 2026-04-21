package com.example.vianova;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RideFlowRepository {

    private static final int REQUEST_TTL_MINUTES = 5;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RideFlowRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DriverAvailabilityRow> findAvailableDrivers() {
        return jdbcTemplate.query("""
                        SELECT
                            d.driver_id,
                            d.name,
                            latest_car.model AS vehicle,
                            COALESCE(rs.overall_rating, 4.5) AS rating
                        FROM dbo.driver_onboarding d
                        CROSS APPLY (
                            SELECT TOP 1 c.model
                            FROM dbo.driver_cars c
                            WHERE c.driver_id = d.driver_id
                            ORDER BY c.created_at DESC
                        ) latest_car
                        LEFT JOIN dbo.driver_rating_summary rs ON rs.driver_id = d.driver_id
                        ORDER BY d.created_at DESC
                        """,
                new MapSqlParameterSource(),
                (rs, rowNum) -> new DriverAvailabilityRow(
                        UUID.fromString(rs.getString("driver_id")),
                        rs.getString("name"),
                        rs.getString("vehicle"),
                        rs.getBigDecimal("rating")
                ));
    }

    public Optional<DriverIdentityRow> findDriverById(UUID driverId) {
        return jdbcTemplate.query("""
                        SELECT driver_id, name
                        FROM dbo.driver_onboarding
                        WHERE driver_id = :driverId
                        """,
                new MapSqlParameterSource("driverId", driverId),
                (rs, rowNum) -> new DriverIdentityRow(
                        UUID.fromString(rs.getString("driver_id")),
                        rs.getString("name")
                )).stream().findFirst();
    }

    public Optional<DriverIdentityRow> findDriverByName(String driverName) {
        return jdbcTemplate.query("""
                        SELECT TOP 1 driver_id, name
                        FROM dbo.driver_onboarding
                        WHERE name = :name
                        ORDER BY created_at DESC
                        """,
                new MapSqlParameterSource("name", driverName),
                (rs, rowNum) -> new DriverIdentityRow(
                        UUID.fromString(rs.getString("driver_id")),
                        rs.getString("name")
                )).stream().findFirst();
    }

    public void upsertCompletedRide(String tripId, UUID driverId, String source, String destination, double fare, LocalDate rideDate) {
        jdbcTemplate.update("""
                        MERGE dbo.driver_previous_rides AS target
                        USING (
                            SELECT
                                :tripId AS ride_id,
                                :driverId AS driver_id,
                                :source AS source,
                                :destination AS destination,
                                :fare AS fare,
                                :rideDate AS ride_date
                        ) AS source_data
                        ON target.ride_id = source_data.ride_id
                        WHEN MATCHED THEN
                            UPDATE SET
                                driver_id = source_data.driver_id,
                                source = source_data.source,
                                destination = source_data.destination,
                                fare = source_data.fare,
                                ride_date = source_data.ride_date
                        WHEN NOT MATCHED THEN
                            INSERT (ride_id, driver_id, source, destination, fare, ride_date)
                            VALUES (source_data.ride_id, source_data.driver_id, source_data.source, source_data.destination, source_data.fare, source_data.ride_date);
                        """,
                new MapSqlParameterSource()
                        .addValue("tripId", tripId)
                        .addValue("driverId", driverId)
                        .addValue("source", source)
                        .addValue("destination", destination)
                        .addValue("fare", BigDecimal.valueOf(fare))
                        .addValue("rideDate", rideDate));
    }

    public void updateRideRequestStatus(UUID requestId, String status) {
        jdbcTemplate.update("""
                        UPDATE dbo.ride_requests
                        SET status = :status,
                            updated_at = SYSUTCDATETIME()
                        WHERE request_id = :requestId
                        """,
                new MapSqlParameterSource()
                        .addValue("requestId", requestId)
                        .addValue("status", status));
    }

    public void upsertTripFeedback(String tripId, UUID driverId, int rating, double tipAmount, String comment) {
        jdbcTemplate.update("""
                        MERGE dbo.driver_trip_feedback AS target
                        USING (
                            SELECT
                                :tripId AS trip_id,
                                :driverId AS driver_id,
                                :rating AS rating,
                                :tipAmount AS tip_amount,
                                :comment AS comment
                        ) AS source_data
                        ON target.trip_id = source_data.trip_id
                        WHEN MATCHED THEN
                            UPDATE SET
                                rating = source_data.rating,
                                tip_amount = source_data.tip_amount,
                                comment = source_data.comment,
                                updated_at = SYSUTCDATETIME()
                        WHEN NOT MATCHED THEN
                            INSERT (trip_id, driver_id, rating, tip_amount, comment, updated_at)
                            VALUES (source_data.trip_id, source_data.driver_id, source_data.rating, source_data.tip_amount, source_data.comment, SYSUTCDATETIME());
                        """,
                new MapSqlParameterSource()
                        .addValue("tripId", tripId)
                        .addValue("driverId", driverId)
                        .addValue("rating", rating)
                        .addValue("tipAmount", BigDecimal.valueOf(tipAmount))
                        .addValue("comment", comment == null ? "" : comment));
    }

    public RatingAggregate aggregateRatings(UUID driverId) {
        return jdbcTemplate.queryForObject("""
                        SELECT
                            COALESCE(AVG(CAST(rating AS DECIMAL(10,2))), 0) AS avg_rating,
                            COUNT(1) AS total_rated
                        FROM dbo.driver_trip_feedback
                        WHERE driver_id = :driverId
                        """,
                new MapSqlParameterSource("driverId", driverId),
                (rs, rowNum) -> new RatingAggregate(
                        rs.getBigDecimal("avg_rating"),
                        rs.getInt("total_rated")
                ));
    }

    public void upsertRatingSummary(UUID driverId, BigDecimal avgRating, int totalTripsRated) {
        jdbcTemplate.update("""
                        MERGE dbo.driver_rating_summary AS target
                        USING (
                            SELECT
                                :driverId AS driver_id,
                                :overallRating AS overall_rating,
                                :totalTripsRated AS total_trips_rated
                        ) AS source_data
                        ON target.driver_id = source_data.driver_id
                        WHEN MATCHED THEN
                            UPDATE SET
                                overall_rating = source_data.overall_rating,
                                total_trips_rated = source_data.total_trips_rated,
                                updated_at = SYSUTCDATETIME()
                        WHEN NOT MATCHED THEN
                            INSERT (driver_id, overall_rating, total_trips_rated, updated_at)
                            VALUES (source_data.driver_id, source_data.overall_rating, source_data.total_trips_rated, SYSUTCDATETIME());
                        """,
                new MapSqlParameterSource()
                        .addValue("driverId", driverId)
                        .addValue("overallRating", avgRating)
                        .addValue("totalTripsRated", totalTripsRated));
    }

    public void upsertRatingStrength(UUID driverId, String strengthName, BigDecimal score) {
        jdbcTemplate.update("""
                        MERGE dbo.driver_rating_strength AS target
                        USING (
                            SELECT
                                :driverId AS driver_id,
                                :strengthName AS strength_name,
                                :score AS score
                        ) AS source_data
                        ON target.driver_id = source_data.driver_id
                           AND target.strength_name = source_data.strength_name
                        WHEN MATCHED THEN
                            UPDATE SET
                                score = source_data.score
                        WHEN NOT MATCHED THEN
                            INSERT (driver_id, strength_name, score)
                            VALUES (source_data.driver_id, source_data.strength_name, source_data.score);
                        """,
                new MapSqlParameterSource()
                        .addValue("driverId", driverId)
                        .addValue("strengthName", strengthName)
                        .addValue("score", score));
    }

    public UUID createRideRequest(UUID riderId, String source, String destination, LocalDateTime departureTime, double estimatedFare, String currency) {
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO dbo.ride_requests (
                            request_id,
                            rider_id,
                            source,
                            destination,
                            departure_time,
                            estimated_fare,
                            currency,
                            status,
                            updated_at
                        )
                        VALUES (
                            :requestId,
                            :riderId,
                            :source,
                            :destination,
                            :departureTime,
                            :estimatedFare,
                            :currency,
                            'PENDING_DRIVER',
                            SYSUTCDATETIME()
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("requestId", requestId)
                        .addValue("riderId", riderId)
                        .addValue("source", source)
                        .addValue("destination", destination)
                        .addValue("departureTime", departureTime)
                        .addValue("estimatedFare", BigDecimal.valueOf(estimatedFare))
                        .addValue("currency", currency));
        return requestId;
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

    public boolean expireOpenRideRequest(UUID requestId, UUID riderId) {
        int updated = jdbcTemplate.update("""
                        UPDATE dbo.ride_requests
                        SET status = 'VOID',
                            updated_at = SYSUTCDATETIME()
                        WHERE request_id = :requestId
                          AND rider_id = :riderId
                          AND status IN ('PENDING_DRIVER', 'ACCEPTED_BY_DRIVER', 'NEGOTIATING')
                          AND created_at < DATEADD(MINUTE, -:ttlMinutes, SYSUTCDATETIME())
                        """,
                new MapSqlParameterSource()
                        .addValue("requestId", requestId)
                        .addValue("riderId", riderId)
                        .addValue("ttlMinutes", REQUEST_TTL_MINUTES));
        return updated > 0;
    }

    public List<RideRequestRow> findDriverRideRequests(UUID driverId) {
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
                            rr.created_at,
                            rr.updated_at,
                            ro.first_name,
                            ro.last_name
                        FROM dbo.ride_requests rr
                        INNER JOIN dbo.rider_onboarding ro ON ro.rider_id = rr.rider_id
                        WHERE (rr.status = 'PENDING_DRIVER'
                           OR rr.assigned_driver_id = :driverId)
                          AND rr.status <> 'VOID'
                        ORDER BY
                            CASE WHEN rr.status = 'PENDING_DRIVER' THEN 0 ELSE 1 END,
                            rr.created_at DESC,
                            rr.updated_at DESC
                        """,
                new MapSqlParameterSource("driverId", driverId),
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
                        rs.getBoolean("driver_final_acceptance"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()
                ));
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

    public Optional<RideRequestRow> submitRiderOffer(UUID requestId, UUID riderId, UUID driverId, double riderOffer) {
        expireOpenRideRequests();
        int updated = jdbcTemplate.update("""
                        UPDATE dbo.ride_requests
                        SET assigned_driver_id = :driverId,
                            status = 'NEGOTIATING',
                            rider_offer = :riderOffer,
                            rider_final_acceptance = 0,
                            driver_final_acceptance = 0,
                            updated_at = SYSUTCDATETIME()
                        WHERE request_id = :requestId
                          AND rider_id = :riderId
                          AND assigned_driver_id = :driverId
                          AND status IN ('ACCEPTED_BY_DRIVER', 'NEGOTIATING')
                        """,
                new MapSqlParameterSource()
                        .addValue("requestId", requestId)
                        .addValue("riderId", riderId)
                        .addValue("driverId", driverId)
                        .addValue("riderOffer", BigDecimal.valueOf(riderOffer)));
        if (updated == 0) {
            return Optional.empty();
        }
        return findRideRequestById(requestId);
    }

    public Optional<RideRequestRow> acceptFinalFareAsRider(UUID requestId, UUID riderId, UUID driverId) {
        expireOpenRideRequests();
        int updated = jdbcTemplate.update("""
                        UPDATE dbo.ride_requests
                        SET rider_final_acceptance = 1,
                            updated_at = SYSUTCDATETIME()
                        WHERE request_id = :requestId
                          AND rider_id = :riderId
                          AND assigned_driver_id = :driverId
                          AND counter_offer IS NOT NULL
                          AND status IN ('NEGOTIATING', 'ACCEPTED_BY_DRIVER')
                        """,
                new MapSqlParameterSource()
                        .addValue("requestId", requestId)
                        .addValue("riderId", riderId)
                        .addValue("driverId", driverId));
        if (updated == 0) {
            return Optional.empty();
        }
        return findRideRequestById(requestId);
    }

    public Optional<RideRequestRow> acceptFinalFareAsDriver(UUID requestId, UUID driverId) {
        expireOpenRideRequests();
        int updated = jdbcTemplate.update("""
                        UPDATE dbo.ride_requests
                        SET driver_final_acceptance = 1,
                            updated_at = SYSUTCDATETIME()
                        WHERE request_id = :requestId
                          AND assigned_driver_id = :driverId
                          AND rider_offer IS NOT NULL
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

    public Optional<RideRequestRow> findRideRequestById(UUID requestId) {
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
                            rr.created_at,
                            rr.updated_at,
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
                        rs.getBoolean("driver_final_acceptance"),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getTimestamp("updated_at").toLocalDateTime()
                )).stream().findFirst();
    }

    private UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    public record DriverAvailabilityRow(UUID driverId, String name, String vehicle, BigDecimal rating) {
    }

    public record DriverIdentityRow(UUID driverId, String name) {
    }

    public record RatingAggregate(BigDecimal averageRating, int totalTripsRated) {
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
            boolean driverFinalAcceptance,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
