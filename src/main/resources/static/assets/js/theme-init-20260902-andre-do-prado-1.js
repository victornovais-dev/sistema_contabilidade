(() => {
  const themeStyles = {
    "andre-do-prado": {
      accountTheme: "andre-do-prado",
      stylesheet: "/assets/css/account-theme-20260902-andre-do-prado-1.css",
      defaultColorTheme: "light",
    },
    "marco-feliciano": {
      accountTheme: "victor-campaign",
      stylesheet: "/assets/css/account-theme-20260826-victor-campaign-responsive-1.css",
      defaultColorTheme: "light",
    },
  };

  const getSavedColorTheme = () => {
    const cookieMatch = document.cookie.match(/(?:^|;\s*)theme=([^;]+)/);
    if (cookieMatch) return decodeURIComponent(cookieMatch[1]);
    return localStorage.getItem("theme");
  };

  const loadStylesheet = (href) => {
    if (document.querySelector(`link[href="${href}"]`)) return;

    const stylesheet = document.createElement("link");
    stylesheet.rel = "stylesheet";
    stylesheet.href = href;
    document.head.append(stylesheet);
  };

  const applyAccountTheme = (theme) => {
    const definition = themeStyles[theme];
    if (!definition) return false;

    document.documentElement.dataset.accountTheme = definition.accountTheme;
    loadStylesheet(definition.stylesheet);

    if (!savedColorTheme) {
      document.documentElement.dataset.theme = definition.defaultColorTheme;
    }

    window.dispatchEvent(new CustomEvent("sc:account-theme-ready", { detail: { theme } }));
    return true;
  };

  let savedColorTheme = null;
  try {
    savedColorTheme = getSavedColorTheme();
  } catch (error) {
    savedColorTheme = null;
  }

  const serverTheme = document.documentElement.dataset.accountTheme;
  const defaultColorTheme = themeStyles[serverTheme]?.defaultColorTheme || "light";
  document.documentElement.dataset.theme = savedColorTheme === "dark" ? "dark" : savedColorTheme === "light" ? "light" : defaultColorTheme;

  if (applyAccountTheme(serverTheme)) return;

  const loadAccountTheme = async () => {
    try {
      const response = await fetch("/api/v1/usuarios/me", { credentials: "same-origin" });
      if (!response.ok) return;

      const user = await response.json();
      applyAccountTheme(user?.tema);
    } catch (error) {
      // Public pages and expired sessions keep the default visual theme.
    }
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", loadAccountTheme, { once: true });
  } else {
    loadAccountTheme();
  }
})();
