(function () {
    const ReactRef = window.React;
    const h = ReactRef && ReactRef.createElement;
    const ReactDOMRef = window.ReactDOM;
    const modules = window.VianovaUiModules || {};
    const shared = modules.shared || {};
    const rider = modules.rider || {};
    const driver = modules.driver || {};

    if (!ReactRef || !h || !ReactDOMRef || !shared.Hero || !shared.HomePanel || !shared.ModuleNavigation || !shared.ModeToggle || !rider.RiderModule || !driver.DriverModule) {
        return;
    }

    function ModulePanel(props) {
        return h(
            "section",
            { className: "panel" },
            h(shared.ModuleNavigation, { page: props.page }),
            h(shared.ModeToggle),
            h(rider.RiderModule),
            h(driver.DriverModule)
        );
    }

    function App(props) {
        const page = props.page === "driver" || props.page === "rider" ? props.page : "home";
        const description = page === "home" ? null : "Book rides in seconds and see ETA, fare, and available drivers instantly.";

        return h(
            "div",
            { className: "react-root-shell" },
            h(
                "div",
                { className: "shell" },
                h(shared.Hero, { description: description }),
                page === "home" ? h(shared.HomePanel) : h(ModulePanel, { page: page })
            )
        );
    }

    function mount(page) {
        const container = document.getElementById("app-root");
        if (!container) {
            return;
        }

        if (ReactDOMRef.createRoot && typeof ReactDOMRef.flushSync === "function") {
            const root = ReactDOMRef.createRoot(container);
            const renderApp = function () {
                root.render(h(App, { page: page }));
            };

            ReactDOMRef.flushSync(renderApp);
            return;
        }

        if (typeof ReactDOMRef.render === "function") {
            ReactDOMRef.render(h(App, { page: page }), container);
            return;
        }

        if (ReactDOMRef.createRoot) {
            ReactDOMRef.createRoot(container).render(h(App, { page: page }));
        }
    }

    window.VianovaUiModules.appShell = {
        mount: mount
    };
}());
