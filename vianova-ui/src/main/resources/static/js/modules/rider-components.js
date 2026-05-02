(function () {
    const ReactRef = window.React;
    const h = ReactRef && ReactRef.createElement;

    if (!ReactRef || !h) {
        return;
    }

    window.VianovaUiModules = window.VianovaUiModules || {};

    function RiderAuthCard() {
        return h(
            "div",
            { id: "riderAuthCard", className: "auth-card" },
            h("h3", { className: "auth-title" }, "Rider Login"),
            h(
                "form",
                { id: "riderLoginForm", className: "auth-form" },
                h("input", { id: "riderEmail", type: "email", placeholder: "Email", required: true }),
                h("input", { id: "riderPassword", type: "password", placeholder: "Password", required: true }),
                h("button", { className: "primary", type: "submit" }, "Login")
            ),
            h("div", { id: "riderLoginMessage", className: "auth-message" }, "Please login to request rides."),
            h("button", { id: "showRiderOnboardingBtn", className: "link-button", type: "button" }, "New user? Start onboarding")
        );
    }

    function RiderOnboardingSection() {
        return h(
            "div",
            { id: "riderOnboardingWrap", className: "hidden" },
            h("h3", null, "Rider Onboarding"),
            h(
                "form",
                { id: "riderOnboardingForm", className: "driver-form" },
                h("input", { id: "riderFirstName", placeholder: "First name", required: true }),
                h("input", { id: "riderLastName", placeholder: "Last name", required: true }),
                h("input", { id: "riderOnboardingEmail", className: "driver-full", type: "email", placeholder: "Email", required: true }),
                h("input", { id: "riderPhoneNumber", className: "driver-full", placeholder: "Phone number", required: true }),
                h("input", { id: "riderOnboardingPassword", className: "driver-full", type: "password", minLength: 8, placeholder: "Password", required: true }),
                h("input", { id: "riderConfirmPassword", className: "driver-full", type: "password", minLength: 8, placeholder: "Confirm password", required: true }),
                h("button", { className: "primary driver-full", type: "submit" }, "Submit Rider Details")
            ),
            h("div", { id: "riderOnboardingMessage", className: "driver-message" }, "Fill rider details to create account.")
        );
    }

    function RiderActions() {
        return h(
            "div",
            { id: "riderActions", className: "section-actions hidden" },
            h("button", { id: "riderProfileBtn", className: "secondary hidden", type: "button" }, "My Profile"),
            h("button", { id: "riderLogoutBtn", className: "secondary hidden", type: "button" }, "Logout")
        );
    }

    function RiderProfilePanel() {
        return h(
            "div",
            { id: "riderProfilePanel", className: "profile-panel hidden" },
            h("h4", null, "Home Address"),
            h("div", { id: "riderAddressList", className: "list-grid" }),
            h(
                "form",
                { id: "riderAddressForm", className: "profile-form" },
                h("input", { id: "riderNewAddress", placeholder: "Add new address", required: true }),
                h("button", { className: "secondary", type: "submit" }, "Add Address")
            ),
            h("h4", null, "Payment Options"),
            h("div", { id: "riderPaymentList", className: "list-grid" }),
            h(
                "form",
                { id: "riderPaymentForm", className: "profile-form payment-form" },
                h("input", { id: "riderPaymentType", placeholder: "Type (Card/Cash)", required: true }),
                h("input", { id: "riderPaymentDetails", placeholder: "Details (e.g. ****4432 or Cash)", required: true }),
                h("button", { className: "secondary", type: "submit" }, "Add Payment")
            ),
            h("h4", null, "Saved Credit Cards"),
            h("div", { id: "riderSavedCardsList", className: "list-grid" }),
            h(
                "form",
                { id: "riderCardForm", className: "profile-form payment-form" },
                h("input", { id: "riderCardHolderName", placeholder: "Card holder name", required: true }),
                h("input", { id: "riderCardNumber", placeholder: "Card number", inputMode: "numeric", required: true }),
                h("input", { id: "riderCardExpiryMonth", type: "number", min: "1", max: "12", placeholder: "MM", required: true }),
                h("input", { id: "riderCardExpiryYear", type: "number", min: "2026", max: "2099", placeholder: "YYYY", required: true }),
                h("button", { className: "secondary", type: "submit" }, "Save Card")
            ),
            h("div", { id: "riderProfileMessage", className: "profile-message" }, "Manage rider addresses and payment options here.")
        );
    }

    function RideRequestForm() {
        return h(
            ReactRef.Fragment,
            null,
            h(
                "div",
                { className: "rider-tool-tabs hidden", id: "riderToolTabs" },
                h("button", { id: "rideEstimateTabBtn", className: "tool-tab active", type: "button" }, "Ride estimate"),
                h("button", { id: "accuracyCheckTabBtn", className: "tool-tab", type: "button" }, "Accuracy check")
            ),
            h(
                "form",
                { id: "riderForm", className: "rider-form hidden" },
                h(
                    "div",
                    { className: "field source-field" },
                    h("input", { id: "source", placeholder: "Source address", autoComplete: "off", required: true }),
                    h("ul", { id: "sourceSuggestions", className: "suggestions hidden" })
                ),
                h("button", { className: "secondary use-location-btn", id: "useLocationBtn", type: "button" }, "Use My Location"),
                h(
                    "div",
                    { className: "field destination-field" },
                    h("input", { id: "destination", placeholder: "Destination address", autoComplete: "off", required: true }),
                    h("ul", { id: "destinationSuggestions", className: "suggestions hidden" })
                ),
                h("input", { className: "departure-time", id: "departureTime", type: "datetime-local", required: true }),
                h("button", { className: "primary", type: "submit" }, "Get Estimate")
            ),
            h("p", { id: "riderHint", className: "hint hidden" }),
            h("div", { id: "estimate", className: "hidden" }, "Fill source, destination and time, then click Get Estimate."),
            h(
                "section",
                { id: "accuracyCheckPanel", className: "accuracy-panel hidden" },
                h("h4", null, "Accuracy Check"),
                h(
                    "div",
                    { className: "accuracy-controls" },
                    h("select", { id: "accuracySampleSelect", "aria-label": "Excel sample trip" }),
                    h("button", { id: "accuracyCheckBtn", className: "primary", type: "button" }, "Get Estimate")
                ),
                h("div", { id: "accuracySampleDetails", className: "accuracy-details" }),
                h("div", { id: "accuracyResult", className: "accuracy-result" })
            ),
            h("div", { id: "drivers" })
        );
    }

    function FareNegotiationPanel() {
        return h(
            ReactRef.Fragment,
            null,
            h(
                "button",
                { id: "negotiationLauncherBtn", className: "secondary negotiation-launcher hidden", type: "button" },
                "Open Fare Chat"
            ),
            h(
                "div",
                { id: "negotiationBlock", className: "chat-block negotiation-panel rider-negotiation-panel hidden" },
                h(
                    "div",
                    { className: "negotiation-panel-header" },
                    h("h4", { id: "negotiationTitle", className: "negotiation-panel-title" }, "Negotiate Fare"),
                    h("button", { id: "negotiationMinimizeBtn", className: "secondary", type: "button" }, "Minimize")
                ),
                h("div", { id: "negotiationMessages", className: "chat-messages" }),
                h(
                    "form",
                    { id: "negotiationForm", className: "chat-form" },
                    h("input", { id: "negotiationFare", type: "number", step: "0.1", min: "1", placeholder: "Propose fare (USD)", required: true }),
                    h("button", { className: "secondary", type: "submit" }, "Send Offer")
                ),
                h(
                    "div",
                    { id: "negotiationActions", className: "chat-actions hidden" },
                    h("button", { id: "proposeAgainBtn", className: "secondary", type: "button" }, "Propose New Price"),
                    h("button", { id: "acceptFinalBtn", className: "primary", type: "button" }, "Accept Final Price")
                )
            )
        );
    }

    function RideOptionsPanel() {
        return h(
            "div",
            { id: "riderOptionsBlock", className: "options-block hidden" },
            h("h4", null, "Compare Options"),
            h("div", { id: "riderOptions", className: "options-list" })
        );
    }

    function TripMap() {
        return h("div", { id: "tripMap", className: "trip-map" });
    }

    function TripCheckoutPanel() {
        return h(
            "div",
            { id: "tripCheckout", className: "checkout-page hidden" },
            h("h4", null, "Finalize Trip"),
            h("div", { id: "tripCheckoutSummary" }),
            h(
                "button",
                { id: "toggleTripSaveCardBtn", className: "secondary trip-save-card-toggle", type: "button", "aria-expanded": "false" },
                "Save Card For Future Use"
            ),
            h(
                "form",
                { id: "tripSaveCardForm", className: "checkout-form trip-card-form hidden" },
                h("input", { id: "tripCardHolderName", placeholder: "Card holder name", required: true }),
                h("input", { id: "tripCardNumber", placeholder: "Card number", inputMode: "numeric", required: true }),
                h("input", { id: "tripCardExpiryMonth", type: "number", min: "1", max: "12", placeholder: "MM", required: true }),
                h("input", { id: "tripCardExpiryYear", type: "number", min: "2026", max: "2099", placeholder: "YYYY", required: true }),
                h("button", { className: "secondary", type: "submit" }, "Save Card")
            ),
            h(
                "form",
                { id: "tripCheckoutForm", className: "checkout-form trip-payment-form" },
                h(
                    "select",
                    { id: "tripPaymentOption", className: "trip-payment-select", required: true },
                    h("option", { value: "" }, "Select payment option")
                ),
                h("button", { className: "primary", type: "submit" }, "Finalize Trip")
            ),
            h("div", { id: "tripCheckoutMessage", className: "hint" }),
            h("div", { id: "tripPinDisplay", className: "hint" }),
            h(
                "div",
                { id: "tripCompleteActions", className: "checkout-actions hidden" },
                h("button", { id: "completeTripBtn", className: "secondary", type: "button" }, "Complete Trip")
            ),
            h(
                "div",
                { id: "tripFeedbackBlock", className: "hidden" },
                h("h4", null, "Trip Feedback"),
                h(
                    "form",
                    { id: "tripFeedbackForm", className: "feedback-form" },
                    h("input", { id: "tripTipAmount", type: "number", step: "0.1", min: "0", placeholder: "Tip amount (USD)", required: true }),
                    h("input", { id: "tripRating", type: "number", min: "1", max: "5", placeholder: "Rating (1-5)", required: true }),
                    h("button", { className: "primary", type: "submit" }, "Submit Feedback")
                ),
                h("div", { id: "tripFeedbackMessage", className: "hint" })
            ),
            h(TripMap)
        );
    }

    function RiderModule() {
        return h(
            "div",
            { id: "riderView" },
            h(RiderAuthCard),
            h(RiderOnboardingSection),
            h(RiderActions),
            h(RiderProfilePanel),
            h(RideRequestForm),
            h(FareNegotiationPanel),
            h(RideOptionsPanel),
            h(TripCheckoutPanel)
        );
    }

    window.VianovaUiModules.rider = {
        RiderModule: RiderModule
    };
}());
