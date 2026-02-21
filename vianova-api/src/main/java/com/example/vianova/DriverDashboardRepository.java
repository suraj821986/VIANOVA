package com.example.vianova;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DriverDashboardRepository {

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

    public record RideRow(String rideId, String source, String destination, BigDecimal fare, String date) {
    }

    public record RatingSummaryRow(BigDecimal overallRating, int totalTripsRated) {
    }

    public record RatingStrengthRow(String name, BigDecimal score) {
    }

    public record CarRow(long carId, String model, String plateNumber, String color) {
    }
}
