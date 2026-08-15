(() => {
  try {
    const match = document.cookie.match(/(?:^|;\s*)theme=([^;]+)/);
    const cookieTheme = match ? decodeURIComponent(match[1]) : null;
    const theme = cookieTheme || localStorage.getItem("theme");
    document.documentElement.dataset.theme = theme === "dark" ? "dark" : "light";
  } catch (error) {
    document.documentElement.dataset.theme = "light";
  }

  if (document.documentElement.dataset.accountTheme) {
    return;
  }

  const accountThemeScript = document.createElement("script");
  accountThemeScript.src = "/assets/js/account-theme-20260811-victor-campaign-9.js";
  accountThemeScript.defer = true;
  document.head.append(accountThemeScript);
})();
