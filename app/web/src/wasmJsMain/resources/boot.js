// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

(() => {
    const strings = {
        ru: { loading: "Загрузка игры", starting: "Запускаем игру…", retry: "Повторить", failed: "Не удалось загрузить игру. Попробуй ещё раз." },
        en: { loading: "Loading the game", starting: "Starting the game…", retry: "Retry", failed: "Couldn't load the game. Try again." },
    };
    // The game saves its language here; Russian matches the default profile.
    let language = "ru";
    try {
        if (window.localStorage.getItem("kinetickk_demo_language") === "en") language = "en";
    } catch (_) {}
    const text = strings[language];
    const boot = document.getElementById("kinetickk-boot");
    const progress = document.getElementById("boot-progress");
    const status = document.getElementById("boot-status");
    const retry = document.getElementById("boot-retry");
    document.documentElement.lang = language;
    progress.setAttribute("aria-label", text.loading);
    status.textContent = text.starting;
    retry.textContent = text.retry;
    const fail = () => {
        if (!boot.isConnected) return;
        boot.setAttribute("aria-busy", "false");
        progress.hidden = true;
        status.setAttribute("role", "alert");
        status.textContent = text.failed;
        retry.hidden = false;
    };
    const onError = event => {
        if (event.target === window || event.target instanceof HTMLScriptElement) fail();
    };
    retry.addEventListener("click", () => window.location.reload());
    window.addEventListener("error", onError, true);
    window.addEventListener("unhandledrejection", fail);
    window.addEventListener("kinetickk:canvas-ready", () => {
        window.removeEventListener("error", onError, true);
        window.removeEventListener("unhandledrejection", fail);
        boot.remove();
    }, { once: true });
})();
