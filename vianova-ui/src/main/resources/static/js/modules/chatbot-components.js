(function () {
    const ReactRef = window.React;
    const h = ReactRef && ReactRef.createElement;
    const ReactDOMRef = window.ReactDOM;

    if (!ReactRef || !h || !ReactDOMRef) {
        return;
    }

    window.VianovaUiModules = window.VianovaUiModules || {};

    function ChatbotPanel() {
        const useEffect = ReactRef.useEffect;
        const useRef = ReactRef.useRef;
        const useState = ReactRef.useState;
        const [chatContext, setChatContext] = useState(readChatContext());
        const [isMinimized, setIsMinimized] = useState(false);
        const [messages, setMessages] = useState([
            {
                role: "assistant",
                text: "Ask for ride estimates, payments, cards, ratings, cars, ride history, or trip tracking."
            }
        ]);
        const [input, setInput] = useState("");
        const [addressSuggestions, setAddressSuggestions] = useState([]);
        const [activeAddressField, setActiveAddressField] = useState("");
        const [confirmedRideFields, setConfirmedRideFields] = useState({ source: "", destination: "" });
        const [suggestionFeedback, setSuggestionFeedback] = useState("");
        const [status, setStatus] = useState("Connected to /api/chatbot/message");
        const [loading, setLoading] = useState(false);
        const messageListRef = useRef(null);
        const inputRef = useRef(null);
        const suggestionRequestRef = useRef(0);
        const bookingTarget = getBookingAutocompleteTarget(input, confirmedRideFields);
        const bookingDraft = extractRideSegments(input, confirmedRideFields);

        function getChatContext() {
            return readChatContext();
        }

        useEffect(function () {
            if (messageListRef.current) {
                messageListRef.current.scrollTop = messageListRef.current.scrollHeight;
            }
        }, [messages]);

        useEffect(function () {
            if (!bookingTarget.query) {
                setAddressSuggestions([]);
                setActiveAddressField("");
                setSuggestionFeedback("");
                return;
            }

            setActiveAddressField(bookingTarget.field);
            setSuggestionFeedback("Looking up " + (bookingTarget.field === "destination" ? "destination" : "pickup") + " suggestions for \"" + bookingTarget.query + "\"...");
            const requestId = ++suggestionRequestRef.current;
            const timer = window.setTimeout(async function () {
                try {
                    const response = await fetch("https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=5&q=" + encodeURIComponent(bookingTarget.query), {
                        headers: { "Accept": "application/json" }
                    });
                    if (!response.ok) {
                        if (requestId === suggestionRequestRef.current) {
                            setAddressSuggestions([]);
                            setSuggestionFeedback("Address lookup is unavailable right now.");
                        }
                        return;
                    }

                    const data = await response.json();
                    if (requestId !== suggestionRequestRef.current) {
                        return;
                    }

                    setAddressSuggestions(Array.isArray(data) ? data : []);
                    setSuggestionFeedback(Array.isArray(data) && data.length
                        ? ""
                        : "No " + (bookingTarget.field === "destination" ? "destination" : "pickup") + " suggestions found for \"" + bookingTarget.query + "\".");
                } catch (_) {
                    if (requestId === suggestionRequestRef.current) {
                        setAddressSuggestions([]);
                        setSuggestionFeedback("Address lookup failed. Keep typing or try a more specific location.");
                    }
                }
            }, 250);

            return function cleanup() {
                window.clearTimeout(timer);
            };
        }, [input, confirmedRideFields]);

        useEffect(function () {
            function handleContextChanged() {
                const nextContext = readChatContext();
                setChatContext(nextContext);
                setStatus(formatContextStatus(nextContext));
            }

            window.addEventListener("vianova-chat-context-changed", handleContextChanged);
            handleContextChanged();

            return function cleanup() {
                window.removeEventListener("vianova-chat-context-changed", handleContextChanged);
            };
        }, []);

        async function handleSubmit(event) {
            event.preventDefault();
            const trimmed = input.trim();
            if (!trimmed || loading) {
                return;
            }

            const chatContext = getChatContext();
            const rideCommand = parseRideBookingCommand(trimmed);
            if (chatContext.userType === "RIDER" && rideCommand && window.VianovaRiderChatBridge) {
                setMessages(function (current) {
                    return current.concat({ role: "user", text: trimmed });
                });
                const prefills = window.VianovaRiderChatBridge.prefillRideRequest(
                    rideCommand.source,
                    rideCommand.destination,
                    rideCommand.departureTime
                );
                if (prefills) {
                    window.VianovaRiderChatBridge.submitRideEstimate();
                    setMessages(function (current) {
                        return current.concat({
                            role: "assistant",
                            text: "I filled the rider form from chat and started the estimate flow in the UI."
                        });
                    });
                    setInput("");
                    setAddressSuggestions([]);
                    setActiveAddressField("");
                    setSuggestionFeedback("");
                    setStatus("Rider estimate opened from chat");
                    return;
                }
            }

            setMessages(function (current) {
                return current.concat({ role: "user", text: trimmed });
            });
            setInput("");
            setAddressSuggestions([]);
            setActiveAddressField("");
            setSuggestionFeedback("");
            setConfirmedRideFields({ source: "", destination: "" });
            setLoading(true);
            setStatus("Waiting for backend response...");

            try {
                const response = await fetch("/api/chatbot/message", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify(Object.assign({ message: trimmed }, getChatContext()))
                });
                const body = await response.text();

                if (!response.ok) {
                    throw new Error(body || ("HTTP " + response.status));
                }

                const result = JSON.parse(body);
                const lines = [result.response || "Mocked response received."];
                if (Array.isArray(result.suggestions) && result.suggestions.length) {
                    lines.push("Suggestions: " + result.suggestions.join(" | "));
                }
                if (result.payload && result.payload.rideEstimate) {
                    window.dispatchEvent(new CustomEvent("vianova-chat-ride-estimate", {
                        detail: result.payload.rideEstimate
                    }));
                }

                setMessages(function (current) {
                    return current.concat({
                        role: "assistant",
                        text: lines.join("\n")
                    });
                });
                setStatus("Last response served by " + (result.source || "backend router") + " | " + formatContextStatus(getChatContext()));
            } catch (error) {
                setMessages(function (current) {
                    return current.concat({
                        role: "assistant",
                        text: "The chatbot request failed: " + error.message
                    });
                });
                setStatus("Backend unavailable | " + formatContextStatus(getChatContext()));
            } finally {
                setLoading(false);
            }
        }

        return h(
            ReactRef.Fragment,
            null,
            h(
                "button",
                {
                    className: "chatbot-launcher" + (isMinimized ? " chatbot-launcher-visible" : ""),
                    type: "button",
                    onClick: function () {
                        setIsMinimized(false);
                    }
                },
                "Open Chatbot"
            ),
            h(
                "section",
                { className: "chatbot-panel" + (isMinimized ? " chatbot-panel-minimized" : "") },
                h(
                    "div",
                    { className: "chatbot-header" },
                    h(
                        "div",
                        null,
                        h("p", { className: "chatbot-eyebrow" }, "Assistant"),
                        h("h3", null, "Vianova Chatbot"),
                        h("p", { className: "chatbot-subtitle" }, "Ask for ride estimates, cards, payments, ratings, cars, ride history, or trip tracking."),
                        h("p", { className: "chatbot-session" }, formatContextStatus(chatContext))
                    ),
                    h(
                        "button",
                        {
                            className: "secondary chatbot-minimize-btn",
                            type: "button",
                            onClick: function () {
                                setIsMinimized(true);
                            }
                        },
                        "Minimize"
                    )
                ),
                h(
                    "div",
                    { className: "chatbot-body" + (isMinimized ? " hidden" : "") },
                    h(
                        "div",
                        { className: "chatbot-message-list", ref: messageListRef },
                        messages.map(function (message, index) {
                            return h(
                                "article",
                                {
                                    key: message.role + "-" + index,
                                    className: "chatbot-message chatbot-message-" + message.role
                                },
                                h("span", { className: "chatbot-role" }, message.role === "user" ? "You" : "Bot"),
                                h("p", null, message.text)
                            );
                        })
                    ),
                    h(
                        "form",
                        { className: "chatbot-form", onSubmit: handleSubmit },
                        h(
                            "div",
                            { className: "chatbot-input-wrap" },
                            h("textarea", {
                                ref: inputRef,
                                className: "chatbot-input",
                                rows: 3,
                                placeholder: "Try: book a ride from JFK Airport to Times Square",
                                value: input,
                                onChange: function (event) {
                                    setInput(event.target.value);
                                    setConfirmedRideFields(function (current) {
                                        const extracted = extractRideSegments(event.target.value, current);
                                        return {
                                            source: extracted.sourceRaw === current.source ? current.source : "",
                                            destination: extracted.destinationRaw === current.destination ? current.destination : ""
                                        };
                                    });
                                },
                                onKeyDown: function (event) {
                                    if (event.key === "Enter" && !event.shiftKey) {
                                        event.preventDefault();
                                        handleSubmit(event);
                                    }
                                }
                        }),
                            bookingTarget.field
                                ? h(
                                    "div",
                                    { className: "chatbot-suggestions" },
                                    h("p", { className: "chatbot-suggestions-label" }, activeAddressField === "destination" ? "Destination suggestions" : "Pickup suggestions"),
                                    bookingDraft.sourceRaw || bookingDraft.destinationRaw
                                        ? h(
                                            "p",
                                            { className: "chatbot-suggestions-state" },
                                            activeAddressField === "destination"
                                                ? "Selected pickup: " + (confirmedRideFields.source || bookingDraft.sourceRaw || "not selected yet")
                                                : "Type and choose a pickup address before destination suggestions start."
                                        )
                                        : null,
                                    suggestionFeedback
                                        ? h("p", { className: "chatbot-suggestions-feedback" }, suggestionFeedback)
                                        : null,
                                    addressSuggestions.map(function (suggestion, index) {
                                        return h(
                                            "button",
                                            {
                                                key: suggestion.place_id || index,
                                                className: "chatbot-suggestion",
                                                type: "button",
                                                onClick: function () {
                                                    applySuggestion(suggestion.display_name || "");
                                                }
                                            },
                                            suggestion.display_name || ""
                                        );
                                    })
                                )
                                : null,
                            confirmedRideFields.source && confirmedRideFields.destination
                                ? h(
                                    "div",
                                    { className: "chatbot-booking-ready" },
                                    h("p", { className: "chatbot-booking-ready-label" }, "Pickup and destination selected. Estimate will be opened in the rider UI."),
                                    h(
                                        "button",
                                        {
                                            className: "primary chatbot-booking-ready-btn",
                                            type: "button",
                                            onClick: function () {
                                                handoffRideToUi(confirmedRideFields.source, confirmedRideFields.destination, parseRideBookingCommand(input));
                                            }
                                        },
                                        "Continue In Rider UI"
                                    )
                                )
                                : null
                        ),
                        h(
                            "div",
                            { className: "chatbot-footer" },
                            h("p", { className: "chatbot-status" }, status),
                            h(
                                "button",
                                { className: "primary", type: "submit", disabled: loading },
                                loading ? "Sending..." : "Send"
                            )
                        )
                    )
                )
            )
        );

        function applySuggestion(selectedAddress) {
            const extracted = extractRideSegments(input, confirmedRideFields);
            if (!selectedAddress || !bookingTarget.field) {
                return;
            }

            let nextInput = input;
            let nextConfirmed = {
                source: confirmedRideFields.source,
                destination: confirmedRideFields.destination
            };

            if (bookingTarget.field === "source") {
                const suffix = extracted.destinationRaw ? " to " + extracted.destinationRaw : " to ";
                nextInput = extracted.prefix + selectedAddress + suffix;
                nextConfirmed.source = selectedAddress;
                nextConfirmed.destination = extracted.destinationRaw === confirmedRideFields.destination ? confirmedRideFields.destination : "";
            } else {
                nextInput = extracted.prefix + extracted.sourceRaw + " to " + selectedAddress;
                nextConfirmed.destination = selectedAddress;
                if (!nextConfirmed.source && extracted.sourceRaw) {
                    nextConfirmed.source = extracted.sourceRaw;
                }
            }

            setInput(nextInput);
            setConfirmedRideFields(nextConfirmed);
            setAddressSuggestions([]);
            setActiveAddressField("");
            setSuggestionFeedback("");

            window.setTimeout(function () {
                if (inputRef.current) {
                    const nextCursor = nextInput.length;
                    inputRef.current.focus();
                    if (typeof inputRef.current.setSelectionRange === "function") {
                        inputRef.current.setSelectionRange(nextCursor, nextCursor);
                    }
                }

                if (nextConfirmed.source && nextConfirmed.destination) {
                    handoffRideToUi(nextConfirmed.source, nextConfirmed.destination, parseRideBookingCommand(nextInput));
                }
            }, 0);
        }

        function handoffRideToUi(source, destination, parsedCommand) {
            if (chatContext.userType !== "RIDER" || !window.VianovaRiderChatBridge) {
                return;
            }
            const departureTime = parsedCommand && parsedCommand.departureTime ? parsedCommand.departureTime : "";
            const prefills = window.VianovaRiderChatBridge.prefillRideRequest(source, destination, departureTime);
            if (!prefills) {
                return;
            }

            window.VianovaRiderChatBridge.submitRideEstimate();
            setMessages(function (current) {
                return current.concat({
                    role: "assistant",
                    text: "I selected the pickup and destination from chat, filled the rider form, and started the estimate step in the UI."
                });
            });
            setInput("");
            setConfirmedRideFields({ source: "", destination: "" });
            setAddressSuggestions([]);
            setActiveAddressField("");
            setSuggestionFeedback("");
            setStatus("Ride details pushed to rider UI");
        }
    }

    window.VianovaUiModules.chatbot = {
        ChatbotPanel: ChatbotPanel,
        mount: function mount() {
            const container = document.getElementById("chatbot-root");
            if (!container) {
                return;
            }

            if (ReactDOMRef.createRoot) {
                const root = ReactDOMRef.createRoot(container);
                root.render(h(ChatbotPanel));
                return;
            }

            ReactDOMRef.render(h(ChatbotPanel), container);
        }
    };

    function readChatContext() {
        let stored = null;
        try {
            stored = window.sessionStorage ? window.sessionStorage.getItem("vianova-chat-context") : null;
        } catch (_) {
        }

        if (stored) {
            try {
                const parsed = JSON.parse(stored);
                return normalizeContext(parsed);
            } catch (_) {
            }
        }

        return normalizeContext(window.VianovaChatContext || {});
    }

    function normalizeContext(context) {
        return {
            page: context.page || (window.location.pathname || "/").replace("/", "").replace(".html", "") || "home",
            userType: context.userType || "",
            userId: context.userId || ""
        };
    }

    function formatContextStatus(context) {
        if (!context.userType || !context.userId) {
            return "Session: not logged in";
        }
        return "Session: " + context.userType + " | " + context.userId;
    }

    function extractRideSegments(value, confirmedRideFields) {
        const text = (value || "").trim();
        const lower = text.toLowerCase();
        const fromIndex = lower.indexOf(" from ");
        const toIndex = lower.lastIndexOf(" to ");
        const confirmed = confirmedRideFields || { source: "", destination: "" };

        if (fromIndex === -1) {
            return { field: "", query: "", prefix: text, sourceRaw: "", destinationRaw: "" };
        }

        const prefix = text.slice(0, fromIndex + 6);
        if (toIndex === -1 || toIndex < fromIndex) {
            const sourceRaw = text.slice(fromIndex + 6).trim();
            return {
                field: "source",
                query: sourceRaw.length >= 3 ? sourceRaw : "",
                prefix: prefix,
                sourceRaw: sourceRaw,
                destinationRaw: ""
            };
        }

        const sourceRaw = text.slice(fromIndex + 6, toIndex).trim();
        const destinationRaw = text.slice(toIndex + 4).trim();
        const sourceConfirmed = !!confirmed.source && confirmed.source === sourceRaw;
        const destinationConfirmed = !!confirmed.destination && confirmed.destination === destinationRaw;

        if (!sourceConfirmed) {
            return {
                field: "source",
                query: sourceRaw.length >= 3 ? sourceRaw : "",
                prefix: prefix,
                sourceRaw: sourceRaw,
                destinationRaw: destinationRaw
            };
        }

        if (!destinationConfirmed) {
            return {
                field: "destination",
                query: destinationRaw.length >= 3 ? destinationRaw : "",
                prefix: prefix,
                sourceRaw: sourceRaw,
                destinationRaw: destinationRaw
            };
        }

        return {
            field: "",
            query: "",
            prefix: prefix,
            sourceRaw: sourceRaw,
            destinationRaw: destinationRaw
        };
    }

    function parseRideBookingCommand(value) {
        const text = (value || "").trim();
        const lower = text.toLowerCase();
        const fromIndex = lower.indexOf(" from ");
        const toIndex = lower.indexOf(" to ");
        if (fromIndex === -1 || toIndex === -1 || toIndex <= fromIndex) {
            return null;
        }

        const source = text.slice(fromIndex + 6, toIndex).trim();
        let destination = text.slice(toIndex + 4).trim();
        let departureTime = "";

        const timeMatch = destination.match(/\s+\b(?:at|for|on)\b\s+(\d{4}-\d{2}-\d{2}T\d{2}:\d{2})$/i);
        if (timeMatch) {
            departureTime = timeMatch[1];
            destination = destination.slice(0, timeMatch.index).trim();
        }

        if (!source || !destination) {
            return null;
        }

        return {
            source: source,
            destination: destination,
            departureTime: departureTime
        };
    }

    function getBookingAutocompleteTarget(value, confirmedRideFields) {
        const extracted = extractRideSegments(value, confirmedRideFields);
        const confirmed = confirmedRideFields || { source: "", destination: "" };

        if (!extracted.sourceRaw) {
            return { field: "", query: "" };
        }

        if (!confirmed.source) {
            return {
                field: "source",
                query: extracted.sourceRaw.length >= 3 ? extracted.sourceRaw : ""
            };
        }

        if (!confirmed.destination) {
            return {
                field: "destination",
                query: extracted.destinationRaw.length >= 3 ? extracted.destinationRaw : ""
            };
        }

        return { field: "", query: "" };
    }

}());
