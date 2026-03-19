(() => {
  const root = document.documentElement;
  const navbarRoot = document.querySelector("#navbar-root,[data-navbar-root]");

  const readCookie = (name) => {
    const match = document.cookie.match(new RegExp("(^| )" + name + "=([^;]+)"));
    return match ? decodeURIComponent(match[2]) : null;
  };

  const writeCookie = (name, value, days = 365) => {
    const expires = new Date(Date.now() + days * 864e5).toUTCString();
    document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=/`;
  };

  const updateThemeToggleLabel = (toggle) => {
    if (!toggle) return;
    const isDark = root.dataset.theme === "dark";
    toggle.setAttribute("aria-pressed", isDark ? "true" : "false");
    toggle.setAttribute("aria-label", isDark ? "Ativar modo claro" : "Ativar modo escuro");
    const icon = toggle.querySelector(".theme-icon");
    if (!icon) return;
    icon.innerHTML =
      '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="4.6" fill="currentColor"></circle><g stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><line x1="12" y1="2.4" x2="12" y2="5"></line><line x1="12" y1="19" x2="12" y2="21.6"></line><line x1="2.4" y1="12" x2="5" y2="12"></line><line x1="19" y1="12" x2="21.6" y2="12"></line><line x1="5.1" y1="5.1" x2="6.9" y2="6.9"></line><line x1="17.1" y1="17.1" x2="18.9" y2="18.9"></line><line x1="17.1" y1="6.9" x2="18.9" y2="5.1"></line><line x1="5.1" y1="18.9" x2="6.9" y2="17.1"></line></g></svg>';
  };

  const initTheme = () => {
    const savedTheme = readCookie("theme") || localStorage.getItem("theme");
    root.dataset.theme = savedTheme === "dark" ? "dark" : "light";
    writeCookie("theme", root.dataset.theme);
    localStorage.setItem("theme", root.dataset.theme);
  };

  const bindNavbarEvents = () => {
    const logoutButton = document.querySelector(".navbar .logout-btn");
    if (logoutButton) {
      logoutButton.addEventListener("click", () => {
        localStorage.removeItem("sc_access_token");
        document.cookie = "SC_TOKEN=; Max-Age=0; path=/";
        window.location.href = "/login";
      });
    }

    const themeToggle = document.querySelector(".navbar .theme-toggle");
    if (themeToggle) {
      themeToggle.dataset.navbarManaged = "true";
      updateThemeToggleLabel(themeToggle);
      themeToggle.addEventListener("click", () => {
        const isDark = root.dataset.theme === "dark";
        root.dataset.theme = isDark ? "light" : "dark";
        writeCookie("theme", root.dataset.theme);
        localStorage.setItem("theme", root.dataset.theme);
        updateThemeToggleLabel(themeToggle);
      });
    }
  };

  const setActiveLink = () => {
    const path = window.location.pathname;
    const links = document.querySelectorAll(".navbar .nav-link");
    links.forEach((link) => {
      if (!(link instanceof HTMLAnchorElement)) return;
      link.classList.toggle("is-active", link.getAttribute("href") === path);
    });
  };

  const renderNavbar = async () => {
    if (!navbarRoot) return;
    try {
      const response = await fetch("/partials/navbar.html", { credentials: "same-origin" });
      if (!response.ok) throw new Error("Falha ao carregar navbar.");
      const markup = await response.text();
      navbarRoot.innerHTML = markup;
      bindNavbarEvents();
      setActiveLink();
      window.dispatchEvent(new Event("navbar:ready"));
    } catch (error) {
      navbarRoot.innerHTML = "";
    }
  };

  initTheme();
  renderNavbar();
})();
