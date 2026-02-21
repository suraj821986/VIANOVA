package com.example.vianovaui;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class HelloProxyController {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${vianova.api.base-url:http://localhost:8080}")
    private String backendBaseUrl;

    @GetMapping("/api/hello")
    public ResponseEntity<String> hello(@RequestParam(value = "myName", defaultValue = "World") String name) {
        try {
            String base = backendBaseUrl.endsWith("/")
                    ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                    : backendBaseUrl;
            String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
            URI uri = URI.create(base + "/hello?myName=" + encodedName);

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Unable to reach vianova API: " + ex.getMessage());
        }
    }

    @PostMapping(value = "/api/rides/estimate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> rideEstimate(@RequestBody String payload) {
        return forwardJson("/rides/estimate", payload);
    }

    @PostMapping(value = "/api/rides/options", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> rideOptions(@RequestBody String payload) {
        return forwardJson("/rides/options", payload);
    }

    @PostMapping(value = "/api/rides/negotiate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> negotiateFare(@RequestBody String payload) {
        return forwardJson("/rides/negotiate", payload);
    }

    @PostMapping(value = "/api/rides/tracking-preview", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> trackingPreview(@RequestBody String payload) {
        return forwardJson("/rides/tracking-preview", payload);
    }

    @PostMapping(value = "/api/rides/finalize", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> finalizeTrip(@RequestBody String payload) {
        return forwardJson("/rides/finalize", payload);
    }

    @GetMapping(value = "/api/rides/tracking", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> tracking(@RequestParam String tripId) {
        try {
            String base = backendBaseUrl.endsWith("/")
                    ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                    : backendBaseUrl;
            String encodedTripId = URLEncoder.encode(tripId, StandardCharsets.UTF_8);
            URI uri = URI.create(base + "/rides/tracking?tripId=" + encodedTripId);

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Unable to reach vianova API: " + ex.getMessage().replace("\"", "'") + "\"}");
        }
    }

    @PostMapping(value = "/api/auth/rider/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> riderLogin(@RequestBody String payload) {
        return forwardJson("/auth/rider/login", payload);
    }

    @PostMapping(value = "/api/auth/driver/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> driverLogin(@RequestBody String payload) {
        return forwardJson("/auth/driver/login", payload);
    }

    @PostMapping(value = "/api/auth/rider/logout", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> riderLogout(@RequestBody String payload) {
        return forwardJson("/auth/rider/logout", payload);
    }

    @PostMapping(value = "/api/auth/driver/logout", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> driverLogout(@RequestBody String payload) {
        return forwardJson("/auth/driver/logout", payload);
    }

    @GetMapping(value = "/api/riders/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> riderProfile(@RequestParam String riderId) {
        try {
            String base = backendBaseUrl.endsWith("/")
                    ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                    : backendBaseUrl;
            String encodedRiderId = URLEncoder.encode(riderId, StandardCharsets.UTF_8);
            URI uri = URI.create(base + "/riders/profile?riderId=" + encodedRiderId);

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Unable to reach vianova API: " + ex.getMessage().replace("\"", "'") + "\"}");
        }
    }

    @PostMapping(value = "/api/riders/profile/address", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addRiderAddress(@RequestBody String payload) {
        return forwardJson("/riders/profile/address", payload);
    }

    @PostMapping(value = "/api/riders/profile/payment", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addRiderPayment(@RequestBody String payload) {
        return forwardJson("/riders/profile/payment", payload);
    }

    @GetMapping(value = "/api/drivers/rides", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> driverRides(@RequestParam String driverId) {
        try {
            String base = backendBaseUrl.endsWith("/")
                    ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                    : backendBaseUrl;
            String encodedDriverId = URLEncoder.encode(driverId, StandardCharsets.UTF_8);
            URI uri = URI.create(base + "/drivers/rides?driverId=" + encodedDriverId);

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Unable to reach vianova API: " + ex.getMessage().replace("\"", "'") + "\"}");
        }
    }

    @GetMapping(value = "/api/drivers/cars", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> driverCars(@RequestParam String driverId) {
        try {
            String base = backendBaseUrl.endsWith("/")
                    ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                    : backendBaseUrl;
            String encodedDriverId = URLEncoder.encode(driverId, StandardCharsets.UTF_8);
            URI uri = URI.create(base + "/drivers/cars?driverId=" + encodedDriverId);

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Unable to reach vianova API: " + ex.getMessage().replace("\"", "'") + "\"}");
        }
    }

    @GetMapping(value = "/api/drivers/ratings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> driverRatings(@RequestParam String driverId) {
        try {
            String base = backendBaseUrl.endsWith("/")
                    ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                    : backendBaseUrl;
            String encodedDriverId = URLEncoder.encode(driverId, StandardCharsets.UTF_8);
            URI uri = URI.create(base + "/drivers/ratings?driverId=" + encodedDriverId);

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Unable to reach vianova API: " + ex.getMessage().replace("\"", "'") + "\"}");
        }
    }

    @GetMapping(value = "/api/drivers/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> driverProfile(@RequestParam String driverId) {
        try {
            String base = backendBaseUrl.endsWith("/")
                    ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                    : backendBaseUrl;
            String encodedDriverId = URLEncoder.encode(driverId, StandardCharsets.UTF_8);
            URI uri = URI.create(base + "/drivers/profile?driverId=" + encodedDriverId);

            HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Unable to reach vianova API: " + ex.getMessage().replace("\"", "'") + "\"}");
        }
    }

    @PostMapping(value = "/api/drivers/cars", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addDriverCar(@RequestBody String payload) {
        return forwardJson("/drivers/cars", payload);
    }

    @PostMapping(value = "/api/drivers/profile/address", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addDriverAddress(@RequestBody String payload) {
        return forwardJson("/drivers/profile/address", payload);
    }

    @PostMapping(value = "/api/drivers/profile/payment", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> addDriverPayment(@RequestBody String payload) {
        return forwardJson("/drivers/profile/payment", payload);
    }

    @PostMapping(value = "/api/drivers/contact/update", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateDriverContact(@RequestBody String payload) {
        return forwardJson("/drivers/contact/update", payload);
    }

    private ResponseEntity<String> forwardJson(String path, String payload) {
        try {
            String base = backendBaseUrl.endsWith("/")
                    ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                    : backendBaseUrl;
            URI uri = URI.create(base + path);

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Unable to reach vianova API: " + ex.getMessage().replace("\"", "'") + "\"}");
        }
    }

    @PostMapping(value = "/api/drivers/onboard", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> onboardDriver(
            @RequestParam String name,
            @RequestParam String age,
            @RequestParam String sex,
            @RequestParam String socialAddress,
            @RequestParam String experience,
            @RequestParam String drivingLicenseNumber,
            @RequestParam MultipartFile licenseDocument
    ) {
        try {
            String base = backendBaseUrl.endsWith("/")
                    ? backendBaseUrl.substring(0, backendBaseUrl.length() - 1)
                    : backendBaseUrl;
            URI uri = URI.create(base + "/drivers/onboard");

            String boundary = "----VianovaBoundary" + UUID.randomUUID();
            byte[] payload = buildMultipartPayload(boundary, name, age, sex, socialAddress, experience, drivingLicenseNumber, licenseDocument);

            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("{\"error\":\"Unable to reach vianova API: " + ex.getMessage().replace("\"", "'") + "\"}");
        }
    }

    private byte[] buildMultipartPayload(
            String boundary,
            String name,
            String age,
            String sex,
            String socialAddress,
            String experience,
            String drivingLicenseNumber,
            MultipartFile licenseDocument
    ) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writeTextPart(output, boundary, "name", name);
        writeTextPart(output, boundary, "age", age);
        writeTextPart(output, boundary, "sex", sex);
        writeTextPart(output, boundary, "socialAddress", socialAddress);
        writeTextPart(output, boundary, "experience", experience);
        writeTextPart(output, boundary, "drivingLicenseNumber", drivingLicenseNumber);

        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"licenseDocument\"; filename=\"" + licenseDocument.getOriginalFilename() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + (licenseDocument.getContentType() == null ? "application/octet-stream" : licenseDocument.getContentType()) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(licenseDocument.getBytes());
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));

        output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return output.toByteArray();
    }

    private void writeTextPart(ByteArrayOutputStream output, String boundary, String fieldName, String fieldValue) throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write((fieldValue == null ? "" : fieldValue).getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
}
