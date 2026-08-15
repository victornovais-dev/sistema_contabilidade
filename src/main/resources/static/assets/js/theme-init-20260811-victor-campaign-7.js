(() => {
  try {
    const match = document.cookie.match(/(?:^|;\s*)theme=([^;]+)/);
    const cookieTheme = match ? decodeURIComponent(match[1]) : null;
    const theme = cookieTheme || localStorage.getItem("theme");
    document.documentElement.dataset.theme = theme === "dark" ? "dark" : "light";
  } catch (error) {
    document.documentElement.dataset.theme = "light";
  }

  const accountThemeScript = document.createElement("script");
  accountThemeScript.src = "/assets/js/account-theme-20260811-victor-campaign-7.js";
  accountThemeScript.defer = true;
  document.head.append(accountThemeScript);
})();
