package com.example.vianova;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RideMlEstimatorService {

    private static final int NYC_ZONE_COUNT = 263;
    private static final Logger log = LoggerFactory.getLogger(RideMlEstimatorService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ml.service.enabled:true}")
    private boolean mlServiceEnabled;

    @Value("${ml.service.base-url:http://127.0.0.1:8001}")
    private String mlServiceBaseUrl;

    public EstimateResult estimate(String source, String destination, LocalDateTime departureTime) {
        String flowId = UUID.randomUUID().toString();
        EstimateFeatures features = buildFeatures(source, destination, departureTime);
        log.info(
                "Ride estimate flow {} prepared source='{}' destination='{}' departureTime='{}' fallbackFeatures={}",
                flowId,
                source,
                destination,
                departureTime,
                features
        );
        if (!mlServiceEnabled) {
            log.info("Ride estimate flow {} using fallback because ml.service.enabled=false", flowId);
            return fallbackEstimate(features, "disabled");
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("source_address", source);
            payload.put("destination_address", destination);
            payload.put("departure_time", departureTime.toString());
            String requestBody = objectMapper.writeValueAsString(payload);
            String endpoint = trimTrailingSlash(mlServiceBaseUrl) + "/ml/v1/predict-addresses";
            log.info(
                    "Ride estimate flow {} sending ML request endpoint='{}' source='{}' destination='{}' payload={}",
                    flowId,
                    endpoint,
                    source,
                    destination,
                    requestBody
            );

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("X-Flow-Id", flowId)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info(
                    "Ride estimate flow {} received ML response status={} body={}",
                    flowId,
                    response.statusCode(),
                    response.body()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Ride estimate flow {} falling back because ML status was {}", flowId, response.statusCode());
                return fallbackEstimate(features, "http_" + response.statusCode());
            }

            JsonNode body = objectMapper.readTree(response.body());
            double estimatedFare = positiveOrElse(body.path("total_no_tip").asDouble(Double.NaN), body.path("fare_pred").asDouble(0.0));
            double etaSeconds = Math.max(0.0, body.path("eta_pred_sec").asDouble(0.0));
            int etaMinutes = Math.max(1, (int) Math.ceil(etaSeconds / 60.0));
            String modelVersion = body.path("model_version").asText("ml-service");
            log.info(
                    "Ride estimate flow {} resolved ML geocoding sourceZone='{}' destinationZone='{}' tripDistance={} sourceLat={} sourceLon={} destinationLat={} destinationLon={}",
                    flowId,
                    body.path("PULocationID").asText(""),
                    body.path("DOLocationID").asText(""),
                    body.path("trip_distance").asDouble(Double.NaN),
                    body.path("source_lat").asDouble(Double.NaN),
                    body.path("source_lon").asDouble(Double.NaN),
                    body.path("destination_lat").asDouble(Double.NaN),
                    body.path("destination_lon").asDouble(Double.NaN)
            );
            EstimateResult result = new EstimateResult(
                    round2(estimatedFare),
                    etaMinutes,
                    "USD",
                    modelVersion,
                    false
            );
            log.info("Ride estimate flow {} completed with result={}", flowId, result);

            return result;
        } catch (Exception ex) {
            log.error("Ride estimate flow {} failed while calling ML service", flowId, ex);
            return fallbackEstimate(features, "exception");
        }
    }

    public FeatureEstimateResult estimateFeatures(double tripDistance, String pickupZoneId, String dropoffZoneId, LocalDateTime departureTime) {
        String flowId = UUID.randomUUID().toString();
        EstimateFeatures features = new EstimateFeatures(tripDistance, pickupZoneId, dropoffZoneId);
        if (!mlServiceEnabled) {
            return fallbackFeatureEstimate(features, "disabled");
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("trip_distance", tripDistance);
            payload.put("PULocationID", pickupZoneId);
            payload.put("DOLocationID", dropoffZoneId);
            payload.put("pickup_hour", departureTime.getHour());
            payload.put("pickup_dow", departureTime.getDayOfWeek().getValue() - 1);
            payload.put("pickup_month", departureTime.getMonthValue());

            String endpoint = trimTrailingSlash(mlServiceBaseUrl) + "/ml/v1/predict";
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("X-Flow-Id", flowId)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Ride feature estimate flow {} falling back because ML status was {}", flowId, response.statusCode());
                return fallbackFeatureEstimate(features, "http_" + response.statusCode());
            }

            JsonNode body = objectMapper.readTree(response.body());
            return new FeatureEstimateResult(
                    round2(body.path("total_no_tip").asDouble(0.0)),
                    round2(body.path("fare_pred").asDouble(0.0)),
                    round2(body.path("tolls_pred").asDouble(0.0)),
                    round2(body.path("eta_pred_sec").asDouble(0.0)),
                    "USD",
                    body.path("model_version").asText("ml-service"),
                    false
            );
        } catch (Exception ex) {
            log.error("Ride feature estimate flow {} failed while calling ML service", flowId, ex);
            return fallbackFeatureEstimate(features, "exception");
        }
    }

    private EstimateFeatures buildFeatures(String source, String destination, LocalDateTime departureTime) {
        String sourceKey = normalize(source);
        String destinationKey = normalize(destination);

        // Placeholder feature engineering until the API has real geocoding and zone mapping.
        int pickupZoneId = stableZoneId(sourceKey);
        int dropoffZoneId = stableZoneId(destinationKey);
        double tripDistance = estimateDistanceMiles(sourceKey, destinationKey, departureTime);

        return new EstimateFeatures(
                tripDistance,
                String.valueOf(pickupZoneId),
                String.valueOf(dropoffZoneId)
        );
    }

    private EstimateResult fallbackEstimate(EstimateFeatures features, String modelVersionSuffix) {
        int etaMinutes = Math.max(4, (int) Math.ceil(features.tripDistance() * 3.2));
        double baseFare = 3.20 + (features.tripDistance() * 2.85);
        double estimatedFare = baseFare + 3.05;
        return new EstimateResult(round2(estimatedFare), etaMinutes, "USD", "fallback_" + modelVersionSuffix, true);
    }

    private FeatureEstimateResult fallbackFeatureEstimate(EstimateFeatures features, String modelVersionSuffix) {
        EstimateResult estimate = fallbackEstimate(features, modelVersionSuffix);
        return new FeatureEstimateResult(
                estimate.estimatedFare(),
                estimate.estimatedFare(),
                0.0,
                estimate.estimatedTimeMinutes() * 60.0,
                estimate.currency(),
                estimate.modelVersion(),
                true
        );
    }

    private int stableZoneId(String value) {
        return Math.floorMod(value.hashCode(), NYC_ZONE_COUNT) + 1;
    }

    private double estimateDistanceMiles(String source, String destination, LocalDateTime departureTime) {
        int routeSeed = Math.abs((source + "|" + destination).hashCode());
        int timeSeed = departureTime.get(ChronoField.MINUTE_OF_DAY);
        double distance = 1.2 + ((routeSeed % 180) / 10.0) + ((timeSeed % 7) * 0.15);
        return Math.min(35.0, round2(distance));
    }

    private double positiveOrElse(double preferred, double fallback) {
        return preferred > 0 ? preferred : Math.max(0.0, fallback);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record EstimateResult(
            double estimatedFare,
            int estimatedTimeMinutes,
            String currency,
            String modelVersion,
            boolean fallbackUsed
    ) {
    }

    public record FeatureEstimateResult(
            double totalNoTip,
            double fare,
            double tolls,
            double etaSeconds,
            String currency,
            String modelVersion,
            boolean fallbackUsed
    ) {
    }

    private record EstimateFeatures(
            double tripDistance,
            String pickupZoneId,
            String dropoffZoneId
    ) {
        @Override
        public String toString() {
            return "EstimateFeatures{" +
                    "tripDistance=" + tripDistance +
                    ", pickupZoneId='" + pickupZoneId + '\'' +
                    ", dropoffZoneId='" + dropoffZoneId + '\'' +
                    '}';
        }
    }
}
