(() => {
  const campaignTheme = "victor-campaign";
  const campaignStylesheet = "/assets/css/account-theme-20260811-victor-campaign-10.css";

  const loadCampaignStyles = () => {
    if (document.querySelector(`link[href="${campaignStylesheet}"]`)) return;

    const stylesheet = document.createElement("link");
    stylesheet.rel = "stylesheet";
    stylesheet.href = campaignStylesheet;
    document.head.append(stylesheet);
  };

  const applyAccountTheme = (theme) => {
    if (theme !== campaignTheme) return;

    document.documentElement.dataset.accountTheme = campaignTheme;
    loadCampaignStyles();
    window.dispatchEvent(new CustomEvent("sc:account-theme-ready", { detail: { theme } }));
  };

  const loadAccountTheme = async () => {
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
