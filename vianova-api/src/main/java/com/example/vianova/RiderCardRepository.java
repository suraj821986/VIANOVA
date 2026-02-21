package com.example.vianova;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class RiderCardRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RiderCardRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SavedCardRow> findByRiderId(UUID riderId) {
        return jdbcTemplate.query("""
                        SELECT card_id, card_holder_name, last4, expiry_month, expiry_year
                        FROM dbo.rider_saved_cards
                        WHERE rider_id = :riderId
                        ORDER BY created_at DESC
                        """,
                new MapSqlParameterSource("riderId", riderId),
                (rs, rowNum) -> new SavedCardRow(
                        UUID.fromString(rs.getString("card_id")),
                        rs.getString("card_holder_name"),
                        rs.getString("last4"),
                        rs.getInt("expiry_month"),
                        rs.getInt("expiry_year")
                ));
    }

    public boolean exists(UUID riderId, String cardHolderName, String last4, int expiryMonth, int expiryYear) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1)
                        FROM dbo.rider_saved_cards
                        WHERE rider_id = :riderId
                          AND card_holder_name = :cardHolderName
                          AND last4 = :last4
                          AND expiry_month = :expiryMonth
                          AND expiry_year = :expiryYear
                        """,
                new MapSqlParameterSource()
                        .addValue("riderId", riderId)
                        .addValue("cardHolderName", cardHolderName)
                        .addValue("last4", last4)
                        .addValue("expiryMonth", expiryMonth)
                        .addValue("expiryYear", expiryYear),
                Integer.class);
        return count != null && count > 0;
    }

    public UUID save(UUID riderId, String cardHolderName, String last4, int expiryMonth, int expiryYear) {
        UUID cardId = UUID.randomUUID();
        jdbcTemplate.update("""
                        INSERT INTO dbo.rider_saved_cards (
                            card_id, rider_id, card_holder_name, last4, expiry_month, expiry_year
                        )
                        VALUES (
                            :cardId, :riderId, :cardHolderName, :last4, :expiryMonth, :expiryYear
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("cardId", cardId)
                        .addValue("riderId", riderId)
                        .addValue("cardHolderName", cardHolderName)
                        .addValue("last4", last4)
                        .addValue("expiryMonth", expiryMonth)
                        .addValue("expiryYear", expiryYear));
        return cardId;
    }

    public int delete(UUID riderId, UUID cardId) {
        return jdbcTemplate.update("""
                        DELETE FROM dbo.rider_saved_cards
                        WHERE rider_id = :riderId
                          AND card_id = :cardId
                        """,
                new MapSqlParameterSource()
                        .addValue("riderId", riderId)
                        .addValue("cardId", cardId));
    }

    public record SavedCardRow(UUID cardId, String cardHolderName, String last4, int expiryMonth, int expiryYear) {
    }
}
