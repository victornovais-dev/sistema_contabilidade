(() => {
  const accountThemes = {
    "andre-do-prado": {
      accountTheme: "andre-do-prado",
      stylesheet: "/assets/css/account-theme-20260902-andre-do-prado-1.css",
    },
    "marco-feliciano": {
      accountTheme: "victor-campaign",
      stylesheet: "/assets/css/account-theme-20260826-victor-campaign-responsive-1.css",
    },
  };

  const loadStyles = (stylesheetHref) => {
    if (document.querySelector(`link[href="${stylesheetHref}"]`)) return;

    const stylesheet = document.createElement("link");
    stylesheet.rel = "stylesheet";
    stylesheet.href = stylesheetHref;
    document.head.append(stylesheet);
  };

  const applyAccountTheme = (theme) => {
    const themeDefinition = accountThemes[theme];
    if (!themeDefinition) return;

    document.documentElement.dataset.accountTheme = themeDefinition.accountTheme;
    loadStyles(themeDefinition.stylesheet);
    window.dispatchEvent(new CustomEvent("sc:account-theme-ready", { detail: { theme } }));
  };

  const loadAccountTheme = async () => {
    const serverTheme = document.documentElement.dataset.accountTheme;
    if (serverTheme) {
      applyAccountTheme(serverTheme);
      return;
    }

    try {
      const response = await fetch("/api/v1/usuarios/me", { credentials: "same-origin" });
      if (!response.ok) return;

      const user = await response.json();
      applyAccountTheme(user?.tema);
    } catch (error) {
      // Falha no carregamento preserva tema padrao.
    }
  };

  void loadAccountTheme();
})();
