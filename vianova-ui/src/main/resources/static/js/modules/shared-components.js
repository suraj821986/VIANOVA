(function () {
    const ReactRef = window.React;
    const h = ReactRef && ReactRef.createElement;

    if (!ReactRef || !h) {
        return;
    }

    window.VianovaUiModules = window.VianovaUiModules || {};

    function ActionLink(props) {
        return h(
            "a",
            {
                className: props.variant + " action-link",
                href: props.href
            },
            props.label
        );
    }

    function Hero(props) {
        const children = [
            h(
                "div",
                { className: "hero-top", key: "top" },
                h("img", { className: "app-logo", src: "/images/vianova-logo.png", alt: "Vianova logo" }),
                h("h1", null, "Vianova")
            )
        ];

        if (props.description) {
            children.push(
                h(
                    "div",
                    { className: "hero-copy", key: "copy" },
                    h("p", null, props.description)
                )
            );
        }

        return h("section", { className: "hero" }, children);
    }

    function HomePanel() {
        return h(
            "section",
            { className: "panel" },
            h(
                "div",
                { className: "section-actions" },
                h(ActionLink, { variant: "primary", href: "/rider.html", label: "Open Rider Module" }),
                h(ActionLink, { variant: "secondary", href: "/driver.html", label: "Open Driver Module" })
            )
        );
    }

    function ModuleNavigation(props) {
        if (props.page === "driver") {
            return h(
                "div",
                { className: "section-actions" },
                h(ActionLink, { variant: "secondary", href: "/rider.html", label: "Switch to Rider" }),
                h(ActionLink, { variant: "primary", href: "/driver.html", label: "Driver Module" }),
                h(ActionLink, { variant: "secondary", href: "/index.html", label: "Home" })
            );
        }

        return h(
            "div",
            { className: "section-actions" },
            h(ActionLink, { variant: "primary", href: "/rider.html", label: "Rider Module" }),
            h(ActionLink, { variant: "secondary", href: "/driver.html", label: "Switch to Driver" }),
            h(ActionLink, { variant: "secondary", href: "/index.html", label: "Home" })
        );
    }

    function ModeToggle() {
        return h(
            "div",
            { className: "mode-toggle" },
            h("button", { id: "riderBtn", className: "mode-btn active" }, "Rider"),
            h("button", { id: "driverBtn", className: "mode-btn" }, "Driver")
        );
    }

    window.VianovaUiModules.shared = {
        ActionLink: ActionLink,
        Hero: Hero,
        HomePanel: HomePanel,
        ModuleNavigation: ModuleNavigation,
        ModeToggle: ModeToggle
    };
}());
