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
                text: "Ask anything about rides, onboarding, or payments. Responses are mocked by the backend for now."
            }
        ]);
        const [input, setInput] = useState("");
        const [status, setStatus] = useState("Connected to /api/chatbot/message");
        const [loading, setLoading] = useState(false);
        const messageListRef = useRef(null);

        function getChatContext() {
            return readChatContext();
        }

        useEffect(function () {
            if (messageListRef.current) {
                messageListRef.current.scrollTop = messageListRef.current.scrollHeight;
            }
        }, [messages]);

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

            setMessages(function (current) {
                return current.concat({ role: "user", text: trimmed });
            });
            setInput("");
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
                        h("p", { className: "chatbot-subtitle" }, "Ask for rides, cards, payments, ratings, cars, or trip tracking."),
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
                        h("textarea", {
                            className: "chatbot-input",
                            rows: 3,
                            placeholder: "Type a message for the backend mock...",
                            value: input,
                            onChange: function (event) {
                                setInput(event.target.value);
                            }
                        }),
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
}());
