(function () {
    const ReactRef = window.React;
    const h = ReactRef && ReactRef.createElement;

    if (!ReactRef || !h) {
        return;
    }

    window.VianovaUiModules = window.VianovaUiModules || {};

    function DriverAuthCard() {
        return h(
            "div",
            { id: "driverAuthCard", className: "auth-card" },
            h("h3", { className: "auth-title" }, "Driver Login"),
            h(
                "form",
                { id: "driverLoginForm", className: "auth-form" },
                h("input", { id: "driverLoginId", type: "email", placeholder: "Email", required: true }),
                h("input", { id: "driverLoginPassword", type: "password", placeholder: "Password", required: true }),
                h("button", { className: "primary", type: "submit" }, "Login")
            ),
            h("div", { id: "driverLoginMessage", className: "auth-message" }, "Already registered? Login to continue."),
            h("button", { id: "showOnboardingBtn", className: "link-button", type: "button" }, "New user? Start onboarding")
        );
    }

    function DriverRidesSection() {
        return h(
            ReactRef.Fragment,
            null,
            h("h4", null, "Previous Rides"),
            h("div", { id: "driverRides", className: "rides-list" })
        );
    }

    function DriverRideRequestsSection() {
        return h(
            "div",
            { className: "chat-block" },
            h("h4", null, "Incoming Ride Requests"),
            h("div", { id: "driverRideRequestsMessage", className: "hint" }, "Nearby rider requests will appear here after login."),
            h("div", { id: "driverRideRequests", className: "rides-list" }),
            h(
                "button",
                { id: "driverNegotiationLauncherBtn", className: "secondary negotiation-launcher hidden", type: "button" },
                "Open Fare Chat"
            ),
            h(
                "div",
                { id: "driverAcceptedRideBlock", className: "chat-block negotiation-panel driver-negotiation-panel hidden" },
                h(
                    "div",
                    { className: "negotiation-panel-header" },
                    h("h4", { id: "driverNegotiationTitle", className: "negotiation-panel-title" }, "Accepted Ride"),
                    h("button", { id: "driverNegotiationMinimizeBtn", className: "secondary", type: "button" }, "Minimize")
                ),
                h("div", { id: "driverAcceptedRideSummary", className: "list-item" }),
                h("div", { id: "driverNegotiationMessages", className: "rides-list" }),
                h(
                    "div",
                    { id: "driverNegotiationActions", className: "section-actions" },
                    h("button", { id: "driverAcceptFinalBtn", className: "primary", type: "button" }, "Accept Rider Offer")
                ),
                h(
                    "form",
                    { id: "driverRideNegotiationForm", className: "chat-form" },
                    h("input", { id: "driverRideCounterOffer", type: "number", step: "0.1", min: "1", placeholder: "Counter offer (USD)", required: true }),
                    h("button", { className: "secondary", type: "submit" }, "Send Counter Offer")
                ),
                h("div", { id: "driverRideNegotiationMessage", className: "hint" }, "Accept a ride request to start negotiating with the rider.")
            ),
            h(
                "div",
                { id: "driverStartRidePrompt", className: "driver-start-ride-prompt hidden" },
                h(
                    "div",
                    { className: "driver-start-ride-header" },
                    h("h4", null, "Start Ride"),
                    h("button", { id: "driverDismissStartRideBtn", className: "secondary", type: "button" }, "Dismiss")
                ),
                h("div", { id: "driverStartRideSummary", className: "list-item" }),
                h(
                    "div",
                    { className: "section-actions" },
                    h("button", { id: "driverStartRideBtn", className: "primary", type: "button" }, "Start To Pickup")
                )
            )
        );
    }

    function DriverRatingsSection() {
        return h(
            "div",
            { className: "ratings-block" },
            h("h4", null, "Ratings"),
            h("div", { id: "driverOverallRating", className: "rating-overall" }),
            h("div", { id: "driverStrengths", className: "strength-list" })
        );
    }

    function DriverCarsSection() {
        return h(
            "div",
            { className: "cars-block" },
            h("h4", null, "My Cars"),
            h("div", { id: "driverCars", className: "cars-list" }),
            h(
                "form",
                { id: "driverCarForm", className: "car-form" },
                h("input", { id: "carModel", placeholder: "Car model", required: true }),
                h("input", { id: "carPlateNumber", placeholder: "Plate number", required: true }),
                h("input", { id: "carColor", placeholder: "Color", required: true }),
                h("button", { className: "secondary", type: "submit" }, "Add Car")
            ),
            h("div", { id: "driverCarMessage", className: "car-message" }, "Existing cars are shown above.")
        );
    }

    function DriverContactSection() {
        return h(
            ReactRef.Fragment,
            null,
            h("h4", null, "Update Contact Information"),
            h(
                "form",
                { id: "driverContactForm", className: "contact-form" },
                h("input", { id: "driverContactPhone", placeholder: "Phone number", required: true }),
                h("input", { id: "driverContactEmail", type: "email", placeholder: "Email", required: true }),
                h("input", { id: "driverContactAddress", className: "contact-full", placeholder: "Address", required: true }),
                h("button", { className: "secondary contact-full", type: "submit" }, "Update Contact Info")
            ),
            h("div", { id: "driverContactMessage", className: "contact-message" }, "Contact information can be updated after login.")
        );
    }

    function DriverDashboard() {
        return h(
            "div",
            { id: "driverDashboard", className: "driver-dashboard hidden" },
            h(
                "div",
                { className: "section-actions" },
                h("button", { id: "driverLogoutBtn", className: "secondary hidden", type: "button" }, "Logout")
            ),
            h(DriverRideRequestsSection),
            h(DriverRidesSection),
            h(DriverRatingsSection),
            h(DriverCarsSection),
            h(DriverContactSection)
        );
    }

    function DriverOnboardingSection() {
        return h(
            "div",
            { id: "driverOnboardingWrap", className: "hidden" },
            h("h3", null, "Driver Onboarding"),
            h(
                "form",
                { id: "driverForm", className: "driver-form" },
                h("input", { id: "driverName", placeholder: "Name", required: true }),
                h("input", { id: "driverAge", type: "number", min: "18", placeholder: "Age", required: true }),
                h("input", { id: "driverSex", placeholder: "Sex", required: true }),
                h("input", { id: "driverExperience", type: "number", min: "0", placeholder: "Experience in years", required: true }),
                h("input", { id: "driverEmail", className: "driver-full", type: "email", placeholder: "Email", required: true }),
                h("input", { id: "driverPhoneNumber", className: "driver-full", placeholder: "Phone number", required: true }),
                h("input", { id: "driverPassword", className: "driver-full", type: "password", minLength: 8, placeholder: "Password", required: true }),
                h("input", { id: "driverConfirmPassword", className: "driver-full", type: "password", minLength: 8, placeholder: "Confirm password", required: true }),
                h("input", { id: "driverSocialAddress", className: "driver-full", placeholder: "Social address", required: true }),
                h("input", { id: "driverLicenseNumber", className: "driver-full", placeholder: "Driving license number", required: true }),
                h("input", { id: "driverLicenseDocument", className: "driver-full", type: "file", accept: ".pdf,.png,.jpg,.jpeg", required: true }),
                h("button", { className: "primary driver-full", type: "submit" }, "Submit Driver Details")
            ),
            h("div", { id: "driverMessage", className: "driver-message" }, "Fill the form and upload scanned license copy.")
        );
    }

    function DriverModule() {
        return h(
            "div",
            { id: "driverView", className: "hidden" },
            h(DriverAuthCard),
            h(DriverDashboard),
            h(DriverOnboardingSection)
        );
    }

    window.VianovaUiModules.driver = {
        DriverModule: DriverModule
    };
}());
