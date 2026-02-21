package com.example.vianova;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class VianovaApplicationTests {

    @Test
    void acceptsHttpRequests() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VianovaApplication()).build();
        mockMvc.perform(get("/hello").queryParam("myName", "Suraj"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello Suraj!"));
    }

    @Test
    void acceptsForwardedHttpsRequests() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new VianovaApplication()).build();
        mockMvc.perform(get("/hello")
                        .queryParam("myName", "Suraj")
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello Suraj!"));
    }

    @Test
    void returnsRideEstimateForRiderRequest() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RideEstimateController()).build();
        String payload = """
                {
                  "source":"Downtown",
                  "destination":"Airport",
                  "departureTime":"2026-02-21T18:30"
                }
                """;

        mockMvc.perform(post("/rides/estimate")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("Downtown"))
                .andExpect(jsonPath("$.destination").value("Airport"))
                .andExpect(jsonPath("$.estimatedTimeMinutes").isNumber())
                .andExpect(jsonPath("$.estimatedFare").isNumber())
                .andExpect(jsonPath("$.availableDrivers").isArray());
    }

    @Test
    void rejectsInvalidRideTimeFormat() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RideEstimateController()).build();
        String payload = """
                {
                  "source":"Downtown",
                  "destination":"Airport",
                  "departureTime":"today evening"
                }
                """;

        mockMvc.perform(post("/rides/estimate")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsRideOptionsForUberAndLyftWithMultipleCarCards() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RideEstimateController()).build();
        String payload = """
                {
                  "source":"Downtown",
                  "destination":"Airport",
                  "departureTime":"2026-02-21T18:30"
                }
                """;

        mockMvc.perform(post("/rides/options")
                .contentType(APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options").isArray())
                .andExpect(jsonPath("$.options[0].provider").value("Uber"))
                .andExpect(jsonPath("$.options[1].provider").value("Uber"))
                .andExpect(jsonPath("$.options[2].provider").value("Lyft"))
                .andExpect(jsonPath("$.options[3].provider").value("Lyft"));
    }

    @Test
    void negotiatesFareWithDriver() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RideEstimateController()).build();
        String payload = """
                {
                  "source":"Downtown",
                  "destination":"Airport",
                  "departureTime":"2026-02-21T18:30",
                  "driverName":"Aarav S.",
                  "proposedFare":10.5
                }
                """;

        mockMvc.perform(post("/rides/negotiate")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").exists())
                .andExpect(jsonPath("$.counterOffer").isNumber())
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void finalizesTripAndReturnsTracking() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RideEstimateController()).build();
        String payload = """
                {
                  "source":"Downtown",
                  "destination":"Airport",
                  "departureTime":"2026-02-21T18:30",
                  "driverName":"Aarav S.",
                  "finalFare":18.5,
                  "paymentOption":"Card ****2291"
                }
                """;

        String response = mockMvc.perform(post("/rides/finalize")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").isString())
                .andExpect(jsonPath("$.tripPin").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String tripId = response.replaceAll(".*\"tripId\":\"([^\"]+)\".*", "$1");
        mockMvc.perform(get("/rides/tracking").queryParam("tripId", tripId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverLocation.lat").isNumber())
                .andExpect(jsonPath("$.riderLocation.lat").isNumber())
                .andExpect(jsonPath("$.status").value("DRIVER_EN_ROUTE"));
    }

    @Test
    void completesTripAndAllowsFeedback() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RideEstimateController()).build();
        String finalizePayload = """
                {
                  "source":"Downtown",
                  "destination":"Airport",
                  "departureTime":"2026-02-21T18:30",
                  "driverName":"Aarav S.",
                  "finalFare":18.5,
                  "paymentOption":"Card ****2291"
                }
                """;

        String finalizeResponse = mockMvc.perform(post("/rides/finalize")
                        .contentType(APPLICATION_JSON)
                        .content(finalizePayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String tripId = finalizeResponse.replaceAll(".*\"tripId\":\"([^\"]+)\".*", "$1");
        String tripPin = finalizeResponse.replaceAll(".*\"tripPin\":\"([^\"]+)\".*", "$1");

        String completePayload = """
                {
                  "tripId":"%s",
                  "tripPin":"%s"
                }
                """.formatted(tripId, tripPin);

        mockMvc.perform(post("/rides/complete")
                        .contentType(APPLICATION_JSON)
                        .content(completePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.allowFeedback").value(true));

        String feedbackPayload = """
                {
                  "tripId":"%s",
                  "tipAmount":4.5,
                  "rating":5,
                  "comment":"Great ride"
                }
                """.formatted(tripId);

        mockMvc.perform(post("/rides/feedback")
                        .contentType(APPLICATION_JSON)
                        .content(feedbackPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Thanks for the feedback"));
    }

    @Test
    void onboardsDriverWithLicenseDocument() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DriverOnboardingController()).build();
        MockMultipartFile license = new MockMultipartFile(
                "licenseDocument",
                "license.pdf",
                "application/pdf",
                "dummy-license-scan".getBytes()
        );

        mockMvc.perform(multipart("/drivers/onboard")
                        .file(license)
                        .param("name", "Suraj")
                        .param("age", "28")
                        .param("sex", "Male")
                        .param("socialAddress", "Bengaluru")
                        .param("experience", "5")
                        .param("drivingLicenseNumber", "KA0120250001234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverName").value("Suraj"))
                .andExpect(jsonPath("$.status").value("Driver is Onboarded and clearance pending will be send via mail"));
    }

    @Test
    void riderLoginReturnsMockedToken() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController()).build();
        String payload = """
                {
                  "emailOrId":"rider@vianova.app",
                  "password":"secret"
                }
                """;

        mockMvc.perform(post("/auth/rider/login")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userType").value("RIDER"))
                .andExpect(jsonPath("$.message").value("Rider login successful"))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void driverLoginIncludesOnboardingFlag() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController()).build();
        String payload = """
                {
                  "emailOrId":"new.driver@vianova.app",
                  "password":"secret"
                }
                """;

        mockMvc.perform(post("/auth/driver/login")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userType").value("DRIVER"))
                .andExpect(jsonPath("$.onboardingRequired").value(true))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void riderLogoutReturnsSuccess() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController()).build();
        String payload = """
                {
                  "userId":"rider@vianova.app"
                }
                """;

        mockMvc.perform(post("/auth/rider/logout")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Rider logged out successfully"));
    }

    @Test
    void driverLogoutReturnsSuccess() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AuthController()).build();
        String payload = """
                {
                  "userId":"driver@vianova.app"
                }
                """;

        mockMvc.perform(post("/auth/driver/logout")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Driver logged out successfully"));
    }

    @Test
    void returnsDriverPreviousRides() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DriverDashboardController()).build();

        mockMvc.perform(get("/drivers/rides").queryParam("driverId", "driver@vianova.app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value("driver@vianova.app"))
                .andExpect(jsonPath("$.rides").isArray());
    }

    @Test
    void updatesDriverContactInformation() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DriverDashboardController()).build();
        String payload = """
                {
                  "driverId":"driver@vianova.app",
                  "phone":"+1-202-555-0111",
                  "email":"driver@vianova.app",
                  "address":"221B Main Street"
                }
                """;

        mockMvc.perform(post("/drivers/contact/update")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contact information updated successfully"));
    }

    @Test
    void returnsDriverCarsWithMockedExistingCar() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DriverDashboardController()).build();

        mockMvc.perform(get("/drivers/cars").queryParam("driverId", "driver@vianova.app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cars").isArray())
                .andExpect(jsonPath("$.cars[0].plateNumber").value("MOCK-1001"));
    }

    @Test
    void returnsDriverRatingsAndStrengths() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DriverDashboardController()).build();

        mockMvc.perform(get("/drivers/ratings").queryParam("driverId", "driver@vianova.app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallRating").value(4.8))
                .andExpect(jsonPath("$.strengths").isArray());
    }

    @Test
    void returnsDriverProfileWithAddressesAndPayments() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DriverDashboardController()).build();

        mockMvc.perform(get("/drivers/profile").queryParam("driverId", "driver@vianova.app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeAddresses").isArray())
                .andExpect(jsonPath("$.paymentOptions").isArray());
    }

    @Test
    void addsDriverAddressAndPaymentOption() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DriverDashboardController()).build();
        String addressPayload = """
                {
                  "driverId":"driver@vianova.app",
                  "address":"221B Baker Street"
                }
                """;
        String paymentPayload = """
                {
                  "driverId":"driver@vianova.app",
                  "type":"Card",
                  "details":"**** 4432"
                }
                """;

        mockMvc.perform(post("/drivers/profile/address")
                        .contentType(APPLICATION_JSON)
                        .content(addressPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/drivers/profile/payment")
                        .contentType(APPLICATION_JSON)
                        .content(paymentPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void returnsRiderProfileWithAddressesAndPayments() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RiderProfileController()).build();

        mockMvc.perform(get("/riders/profile").queryParam("riderId", "rider@vianova.app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeAddresses").isArray())
                .andExpect(jsonPath("$.paymentOptions").isArray());
    }

    @Test
    void addsRiderAddressAndPaymentOption() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RiderProfileController()).build();
        String addressPayload = """
                {
                  "riderId":"rider@vianova.app",
                  "address":"77 Oak Avenue"
                }
                """;
        String paymentPayload = """
                {
                  "riderId":"rider@vianova.app",
                  "type":"UPI",
                  "details":"rider@upi"
                }
                """;

        mockMvc.perform(post("/riders/profile/address")
                        .contentType(APPLICATION_JSON)
                        .content(addressPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        mockMvc.perform(post("/riders/profile/payment")
                        .contentType(APPLICATION_JSON)
                        .content(paymentPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void addsNewDriverCarAndHandlesDuplicate() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DriverDashboardController()).build();
        String payload = """
                {
                  "driverId":"driver@vianova.app",
                  "model":"Honda City",
                  "plateNumber":"KA01AB1234",
                  "color":"Blue"
                }
                """;

        mockMvc.perform(post("/drivers/cars")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Car added successfully"));

        mockMvc.perform(post("/drivers/cars")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Car already exists"));
    }
}
