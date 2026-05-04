(() => {
  const root = document.documentElement;
  const navbarRoot = document.querySelector("#navbar-root,[data-navbar-root]");
  const NAVBAR_VERSION = "20260502-startup-perf-1";
  const roleFilterStorageKey = "sc_home_selected_role";
  let roleVisibilityRefreshScheduled = false;
  let notificationRefreshScheduled = false;

  const readCookie = (name) => {
    const match = document.cookie.match(new RegExp("(^| )" + name + "=([^;]+)"));
    return match ? decodeURIComponent(match[2]) : null;
  };

  const writeCookie = (name, value, days = 365) => {
    const expires = new Date(Date.now() + days * 864e5).toUTCString();
    document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=/`;
  };

  const getAccessToken = () => localStorage.getItem("sc_access_token");

  const clearClientAuthState = () => {
    if (window.SCAuth?.clearClientAuthState) {
      window.SCAuth.clearClientAuthState();
      return;
    }
    localStorage.removeItem("sc_access_token");
  };

  const fetchCsrfToken = async () => {
    const response = await fetch("/api/v1/auth/csrf", {
      method: "GET",
      credentials: "same-origin",
    });
    if (!response.ok) {
      throw new Error("Falha ao obter token CSRF.");
    }
    const payload = await response.json();
    if (!payload || !payload.token) {
      throw new Error("Token CSRF ausente.");
    }
    return payload.token;
  };

  const logout = async () => {
    if (window.SCAuth?.logout) {
      try {
        await window.SCAuth.logout();
      } finally {
        window.location.href = "/login";
      }
      return;
    }
    try {
      const csrfToken = await fetchCsrfToken();
      await fetch("/api/v1/auth/logout", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "X-CSRF-TOKEN": csrfToken,
        },
      });
    } catch (error) {
      // Mesmo se a sessao do servidor ja tiver expirado, limpamos o estado local.
    } finally {
      clearClientAuthState();
      window.location.href = "/login";
    }
  };

  const getStoredSelectedRole = () =>
    String(localStorage.getItem(roleFilterStorageKey) || "").trim();

  const buildNotificationQuery = () => {
    const selectedRole = getStoredSelectedRole();
    if (!selectedRole) return "";
    const params = new URLSearchParams({ role: selectedRole });
    return `?${params.toString()}`;
  };

  const getUserRoles = async () => {
    if (window.SCAuth?.getUserRoles) {
      return window.SCAuth.getUserRoles();
    }

    const accessToken = getAccessToken();
    if (!accessToken) return [];

    try {
      const response = await fetch("/api/v1/auth/me/roles", {
        method: "GET",
        credentials: "same-origin",
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      if (!response.ok) return [];
      const payload = await response.json();
      return Array.isArray(payload) ? payload.map((role) => String(role || "").toUpperCase()) : [];
    } catch (error) {
      return [];
    }
  };

  const getPendingNotificationCount = (notifications) =>
    (Array.isArray(notifications) ? notifications : []).filter(
      (notification) =>
        notification && Object.prototype.hasOwnProperty.call(notification, "verificado")
          ? !Boolean(notification.verificado)
          : !Boolean(notification?.limpa),
    ).length;

  const setNotificationBadgeCount = (count) => {
    const badge = document.querySelector(".navbar [data-notification-count]");
    if (!(badge instanceof HTMLElement)) return;

    const numericCount = Number(count || 0);
    if (!Number.isFinite(numericCount) || numericCount <= 0) {
      badge.hidden = true;
      badge.textContent = "";
      badge.removeAttribute("aria-label");
      return;
    }

    badge.textContent = numericCount > 99 ? "99+" : String(numericCount);
    badge.hidden = false;
    badge.setAttribute("aria-label", `${numericCount} notificacoes`);
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

  const scheduleNonCriticalTask = (callback) => {
    if (typeof window.requestIdleCallback === "function") {
      window.requestIdleCallback(() => {
        callback();
      }, { timeout: 500 });
      return;
    }
    window.setTimeout(() => {
      callback();
    }, 0);
  };

  const bindNavbarEvents = () => {
    const logoutButton = document.querySelector(".navbar .logout-btn");
    if (logoutButton) {
      logoutButton.addEventListener("click", () => {
        void logout();
      });
    }

    const themeToggle = document.querySelector(".navbar .theme-toggle");
    if (themeToggle) {
      if (themeToggle.dataset.navbarManaged === "true") {
        updateThemeToggleLabel(themeToggle);
        return;
      }
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

  const applyRoleVisibility = async () => {
    const roles = await getUserRoles();

    document.querySelectorAll(".navbar [data-hide-for-role]").forEach((element) => {
      if (!(element instanceof HTMLElement)) return;
      const role = String(element.dataset.hideForRole || "").toUpperCase();
      if (role && roles.includes(role)) {
        element.remove();
      }
    });

    document.querySelectorAll(".navbar [data-show-for-roles]").forEach((element) => {
      if (!(element instanceof HTMLElement)) return;
      const allowedRoles = String(element.dataset.showForRoles || "")
        .split(",")
        .map((role) => role.trim().toUpperCase())
        .filter(Boolean);
      if (allowedRoles.length && !allowedRoles.some((role) => roles.includes(role))) {
        element.remove();
      }
    });
  };

  const updateNotificationCount = async () => {
    const badge = document.querySelector(".navbar [data-notification-count]");
    if (!(badge instanceof HTMLElement)) return;

    const accessToken = getAccessToken();
    if (!accessToken) {
      setNotificationBadgeCount(0);
      return;
    }

    try {
      const response = await fetch(`/api/v1/notificacoes${buildNotificationQuery()}`, {
        method: "GET",
        credentials: "same-origin",
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });

      if (response.status === 401 || response.status === 403) {
        setNotificationBadgeCount(0);
        return;
      }
      if (!response.ok) {
        throw new Error("Falha ao carregar contador de notificacoes.");
      }

      const payload = await response.json();
      const count = getPendingNotificationCount(payload);
      setNotificationBadgeCount(count);
    } catch (error) {
      setNotificationBadgeCount(0);
    }
  };

  const scheduleRoleVisibilityRefresh = () => {
    if (roleVisibilityRefreshScheduled) return;
    roleVisibilityRefreshScheduled = true;
    scheduleNonCriticalTask(() => {
      roleVisibilityRefreshScheduled = false;
      void applyRoleVisibility();
    });
  };

  const scheduleNotificationRefresh = () => {
    if (notificationRefreshScheduled) return;
    notificationRefreshScheduled = true;
    scheduleNonCriticalTask(() => {
      notificationRefreshScheduled = false;
      void updateNotificationCount();
    });
  };

  const scheduleNavbarDataRefresh = () => {
    scheduleRoleVisibilityRefresh();
    scheduleNotificationRefresh();
  };

  const initRenderedNavbar = () => {
    const navbar = document.querySelector(".navbar");
    if (!navbar) return false;
    bindNavbarEvents();
    setActiveLink();
    scheduleNavbarDataRefresh();
    window.dispatchEvent(new Event("navbar:ready"));
    return true;
  };

  const renderNavbar = async () => {
    if (!navbarRoot) return;
    try {
      const response = await fetch(`/partials/navbar.html?v=${NAVBAR_VERSION}`, {
        credentials: "same-origin",
      });
      if (!response.ok) throw new Error("Falha ao carregar navbar.");
      const markup = await response.text();
      navbarRoot.innerHTML = markup;
      bindNavbarEvents();
      setActiveLink();
      scheduleNavbarDataRefresh();
      window.dispatchEvent(new Event("navbar:ready"));
    } catch (error) {
      navbarRoot.innerHTML = "";
    }
  };

  window.addEventListener("notifications:changed", (event) => {
    const pendingCount =
      event instanceof CustomEvent && event.detail ? Number(event.detail.pendingCount) : NaN;
    if (Number.isFinite(pendingCount)) {
      setNotificationBadgeCount(pendingCount);
      return;
    }
    scheduleNotificationRefresh();
  });

  window.addEventListener("sc:home-role-change", () => {
    scheduleNotificationRefresh();
  });

  window.addEventListener("sc:routes-updated", () => {
    setActiveLink();
  });

  initTheme();
  if (!initRenderedNavbar()) {
    void renderNavbar();
  }
})();
