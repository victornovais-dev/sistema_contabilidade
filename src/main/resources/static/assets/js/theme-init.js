(() => {
  try {
    const match = document.cookie.match(/(?:^|;\s*)theme=([^;]+)/);
    const cookieTheme = match ? decodeURIComponent(match[1]) : null;
    const theme = cookieTheme || localStorage.getItem("theme");
    document.documentElement.dataset.theme = theme === "dark" ? "dark" : "light";
  } catch (error) {
    document.documentElement.dataset.theme = "light";
  }
})();
