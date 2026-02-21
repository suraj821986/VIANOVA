package com.example.vianova;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RideFlowRepository {

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

    public record DriverAvailabilityRow(UUID driverId, String name, String vehicle, BigDecimal rating) {
    }

    public record DriverIdentityRow(UUID driverId, String name) {
    }

    public record RatingAggregate(BigDecimal averageRating, int totalTripsRated) {
    }
}
