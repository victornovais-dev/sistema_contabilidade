(function () {
  const TOKEN_STORAGE_KEY = "sc_access_token";
  const PLACEHOLDER_TOKEN = "__sc_bootstrap_pending__";
  const REFRESH_MARGIN_MS = 60_000;
  const TRANSIENT_REFRESH_RETRY_MS = 15_000;
  const ROUTES_ENDPOINT = "/api/v1/auth/routes";
  const USER_ROLES_ENDPOINT = "/api/v1/auth/me/roles";
  const AUTH_API_PREFIX = "/api/v1/auth/";
  const AUTH_BOOTSTRAP_BYPASS_PATHS = new Set([
    "/api/v1/auth/csrf",
    "/api/v1/auth/login",
    "/api/v1/auth/logout",
    "/api/v1/auth/refresh",
  ]);
  const state = {
    accessToken: null,
    tokenExpiryMs: null,
    refreshTimer: null,
    csrfToken: null,
    bootstrapComplete: false,
    bootstrapPromise: null,
    routeConfigLoaded:
      Boolean(window.__SC_ROUTE_CONFIG) &&
      typeof window.__SC_ROUTE_CONFIG === "object" &&
      Object.keys(window.__SC_ROUTE_CONFIG).length > 0,
    routeConfigPromise: null,
    routeConfigScheduledPromise: null,
    userRoles: null,
    userRolesPromise: null,
    routeConfig:
      window.__SC_ROUTE_CONFIG && typeof window.__SC_ROUTE_CONFIG === "object"
        ? { ...window.__SC_ROUTE_CONFIG }
        : {},
  };

  const nativeFetch = window.fetch.bind(window);
  const storage = window.localStorage;
  const nativeGetItem = storage.getItem.bind(storage);
  const nativeSetItem = storage.setItem.bind(storage);
  const nativeRemoveItem = storage.removeItem.bind(storage);

  const decodeBase64Url = (value) => {
    const normalized = String(value || "").replace(/-/g, "+").replace(/_/g, "/");
    const padding = normalized.length % 4 === 0 ? "" : "=".repeat(4 - (normalized.length % 4));
    return window.atob(normalized + padding);
  };

  const parseTokenExpiry = (token) => {
    try {
      const [, payloadPart] = String(token || "").split(".");
      if (!payloadPart) return null;
      const payload = JSON.parse(decodeBase64Url(payloadPart));
      return typeof payload.exp === "number" ? payload.exp * 1000 : null;
    } catch (error) {
      return null;
    }
  };

  const normalizeUserRoles = (payload) =>
    Array.isArray(payload)
      ? payload.map((role) => String(role || "").trim().toUpperCase()).filter(Boolean)
      : [];

  const currentStorageValue = () => {
    if (state.accessToken) return state.accessToken;
    if (!state.bootstrapComplete) return PLACEHOLDER_TOKEN;
    return null;
  };

  const clearRefreshTimer = () => {
    if (state.refreshTimer) {
      window.clearTimeout(state.refreshTimer);
      state.refreshTimer = null;
    }
  };

  const clearUserRolesCache = () => {
    state.userRoles = null;
    state.userRolesPromise = null;
  };

  const scheduleRefreshRetry = (delayMs = TRANSIENT_REFRESH_RETRY_MS) => {
    clearRefreshTimer();
    if (!state.accessToken) {
      return;
    }
    state.refreshTimer = window.setTimeout(() => {
      void refreshAccessToken();
    }, delayMs);
  };

  const dispatchAuthEvent = (name, detail) => {
    window.dispatchEvent(new CustomEvent(name, { detail }));
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

  const hydrateRouteAnchors = () => {
    document.querySelectorAll("[data-route-key]").forEach((element) => {
      if (!(element instanceof HTMLAnchorElement)) return;
      const routeKey = String(element.dataset.routeKey || "").trim();
      const routeValue = state.routeConfig[routeKey];
      if (routeValue) {
        element.href = routeValue;
      }
    });
  };

  const setRouteConfig = (routeConfig) => {
    state.routeConfig =
      routeConfig && typeof routeConfig === "object" ? { ...routeConfig } : {};
    state.routeConfigLoaded = Object.keys(state.routeConfig).length > 0;
    window.__SC_ROUTE_CONFIG = { ...state.routeConfig };
    hydrateRouteAnchors();
    dispatchAuthEvent("sc:routes-updated", { routeConfig: { ...state.routeConfig } });
  };

  const scheduleRefresh = () => {
    clearRefreshTimer();
    if (!state.tokenExpiryMs) return;
    const refreshDelay = Math.max(state.tokenExpiryMs - Date.now() - REFRESH_MARGIN_MS, 0);
    state.refreshTimer = window.setTimeout(() => {
      void refreshAccessToken();
    }, refreshDelay);
  };

  const setAccessToken = (token) => {
    state.accessToken = token || null;
    state.tokenExpiryMs = token ? parseTokenExpiry(token) : null;
    state.bootstrapComplete = true;
    scheduleRefresh();
    dispatchAuthEvent("sc:auth-updated", {
      authenticated: Boolean(state.accessToken),
      routeConfig: { ...state.routeConfig },
    });
  };

  const clearAuthState = (options = {}) => {
    clearRefreshTimer();
    clearUserRolesCache();
    state.accessToken = null;
    state.tokenExpiryMs = null;
    state.csrfToken = null;
    state.bootstrapComplete = true;
    state.routeConfigPromise = null;
    state.routeConfigScheduledPromise = null;
    if (!options.keepRoutes) {
      setRouteConfig({});
      return;
    }
    dispatchAuthEvent("sc:auth-updated", {
      authenticated: false,
      routeConfig: { ...state.routeConfig },
    });
  };

  const normalizeUrl = (input) => {
    if (input instanceof Request) {
      return new URL(input.url, window.location.origin);
    }
    return new URL(String(input), window.location.origin);
  };

  const sameOriginApiRequest = (url) =>
    url.origin === window.location.origin && url.pathname.startsWith("/api/");

  const shouldWaitForBootstrap = (url) =>
    sameOriginApiRequest(url) && !AUTH_BOOTSTRAP_BYPASS_PATHS.has(url.pathname);

  const shouldClearAuthStateAfterRefreshFailure = (status) =>
    status === 401 || status === 403 || !state.accessToken;

  const ensureCsrfToken = async (forceRefresh = false) => {
    if (!forceRefresh && state.csrfToken) {
      return state.csrfToken;
    }

    const response = await nativeFetch("/api/v1/auth/csrf", {
      method: "GET",
      credentials: "same-origin",
      cache: "no-store",
      headers: {
        "X-Requested-With": "XMLHttpRequest",
      },
    });

    if (!response.ok) {
      throw new Error("Falha ao obter token CSRF.");
    }

    const payload = await response.json().catch(() => ({}));
    state.csrfToken = payload.token || null;
    if (!state.csrfToken) {
      throw new Error("Token CSRF ausente.");
    }
    return state.csrfToken;
  };

  const loadRouteConfig = async (forceRefresh = false) => {
    if (!state.accessToken) {
      if (!forceRefresh) {
        return { ...state.routeConfig };
      }
      setRouteConfig({});
      return {};
    }

    if (!forceRefresh) {
      if (state.routeConfigLoaded) {
        return { ...state.routeConfig };
      }
      if (state.routeConfigPromise) {
        return { ...(await state.routeConfigPromise) };
      }
    }

    let requestPromise;
    requestPromise = (async () => {
      try {
        const response = await nativeFetch(ROUTES_ENDPOINT, {
          method: "GET",
          credentials: "same-origin",
          cache: "no-store",
          headers: {
            "X-Requested-With": "XMLHttpRequest",
            Authorization: `Bearer ${state.accessToken}`,
          },
        });
        if (!response.ok) {
          setRouteConfig({});
          return {};
        }
        const payload = await response.json().catch(() => ({}));
        setRouteConfig(payload);
        return payload;
      } catch (error) {
        setRouteConfig({});
        return {};
      } finally {
        if (state.routeConfigPromise === requestPromise) {
          state.routeConfigPromise = null;
        }
      }
    })();

    state.routeConfigPromise = requestPromise;
    return { ...(await requestPromise) };
  };

  const preloadRouteConfig = (forceRefresh = false) => {
    if (!state.accessToken) {
      return Promise.resolve({});
    }

    if (!forceRefresh) {
      if (state.routeConfigLoaded) {
        return Promise.resolve({ ...state.routeConfig });
      }
      if (state.routeConfigPromise) {
        return state.routeConfigPromise;
      }
      if (state.routeConfigScheduledPromise) {
        return state.routeConfigScheduledPromise;
      }
    }

    let scheduledPromise;
    scheduledPromise = new Promise((resolve) => {
      scheduleNonCriticalTask(() => {
        if (state.routeConfigScheduledPromise === scheduledPromise) {
          state.routeConfigScheduledPromise = null;
        }
        if (!state.accessToken) {
          resolve({});
          return;
        }
        void loadRouteConfig(forceRefresh)
          .then((routeConfig) => {
            resolve(routeConfig);
          })
          .catch(() => {
            resolve({});
          });
      });
    });

    state.routeConfigScheduledPromise = scheduledPromise;
    return scheduledPromise;
  };

  const loadUserRoles = async (forceRefresh = false) => {
    if (!forceRefresh) {
      if (Array.isArray(state.userRoles)) {
        return [...state.userRoles];
      }
      if (state.userRolesPromise) {
        return [...(await state.userRolesPromise)];
      }
    }

    if (!state.bootstrapComplete && state.bootstrapPromise) {
      try {
        await state.bootstrapPromise;
      } catch (error) {
        // Se o bootstrap falhar, ainda tentamos seguir com o estado atual.
      }
    }

    if (!state.accessToken) {
      return [];
    }

    let requestPromise;
    requestPromise = (async () => {
      try {
        const response = await nativeFetch(USER_ROLES_ENDPOINT, {
          method: "GET",
          credentials: "same-origin",
          cache: "no-store",
          headers: {
            Authorization: `Bearer ${state.accessToken}`,
            "X-Requested-With": "XMLHttpRequest",
          },
        });

        if (response.status === 401 || response.status === 403) {
          clearUserRolesCache();
          return [];
        }
        if (!response.ok) {
          return [];
        }

        const roles = normalizeUserRoles(await response.json().catch(() => []));
        state.userRoles = roles;
        return roles;
      } catch (error) {
        return [];
      } finally {
        if (state.userRolesPromise === requestPromise) {
          state.userRolesPromise = null;
        }
      }
    })();

    state.userRolesPromise = requestPromise;
    return [...(await requestPromise)];
  };

  const refreshAccessToken = async () => {
    const executeRefresh = async () => {
      const csrfToken = await ensureCsrfToken(true);
      return nativeFetch("/api/v1/auth/refresh", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "X-CSRF-TOKEN": csrfToken,
          "X-Requested-With": "XMLHttpRequest",
        },
      });
    };

    let response;
    try {
      response = await executeRefresh();
    } catch (error) {
      if (shouldClearAuthStateAfterRefreshFailure()) {
        clearAuthState();
      } else {
        scheduleRefreshRetry();
      }
      throw error;
    }

    if (!response.ok) {
      if (shouldClearAuthStateAfterRefreshFailure(response.status)) {
        clearAuthState();
      } else {
        scheduleRefreshRetry();
      }
      throw new Error("Nao foi possivel renovar a sessao.");
    }

    const payload = await response.json().catch(() => ({}));
    if (!payload.accessToken) {
      clearAuthState();
      throw new Error("Resposta sem access token.");
    }

    setAccessToken(payload.accessToken);
    void preloadRouteConfig(true);
    return payload.accessToken;
  };

  const bootstrapSession = async () => {
    try {
      await refreshAccessToken();
      return true;
    } catch (error) {
      clearAuthState();
      return false;
    }
  };

  const logout = async () => {
    const executeLogout = async () => {
      const csrfToken = await ensureCsrfToken(true);
      return nativeFetch("/api/v1/auth/logout", {
        method: "POST",
        credentials: "same-origin",
        cache: "no-store",
        headers: {
          "X-CSRF-TOKEN": csrfToken,
          "X-Requested-With": "XMLHttpRequest",
        },
      });
    };

    try {
      let response = await executeLogout();
      if (response.status === 403) {
        response = await executeLogout();
      }
      if (!response.ok && response.status !== 401 && response.status !== 403) {
        throw new Error("Nao foi possivel encerrar a sessao.");
      }
    } catch (error) {
      // O estado local ainda precisa ser limpo mesmo se a sessao do servidor expirou.
    } finally {
      clearAuthState();
    }
  };

  storage.getItem = function getItem(key) {
    if (key === TOKEN_STORAGE_KEY) {
      return currentStorageValue();
    }
    return nativeGetItem(key);
  };

  storage.setItem = function setItem(key, value) {
    if (key === TOKEN_STORAGE_KEY) {
      clearUserRolesCache();
      setAccessToken(value);
      return;
    }
    nativeSetItem(key, value);
  };

  storage.removeItem = function removeItem(key) {
    if (key === TOKEN_STORAGE_KEY) {
      clearAuthState({ keepRoutes: true });
      return;
    }
    nativeRemoveItem(key);
  };

  window.fetch = async function wrappedFetch(input, init) {
    const url = normalizeUrl(input);
    if (!sameOriginApiRequest(url)) {
      return nativeFetch(input, init);
    }

    if (!state.bootstrapComplete && state.bootstrapPromise && shouldWaitForBootstrap(url)) {
      try {
        await state.bootstrapPromise;
      } catch (error) {
        // Se o bootstrap falhar, deixamos a requisicao seguir com cookie/sessao.
      }
    }

    const requestInit = { ...(init || {}) };
    const alreadyRetried = Boolean(requestInit.__scRetried);
    delete requestInit.__scRetried;

    if (!requestInit.credentials) {
      requestInit.credentials = "same-origin";
    }

    const originalHeaders =
      requestInit.headers || (input instanceof Request ? new Headers(input.headers) : undefined);
    const headers = new Headers(originalHeaders || {});
    const existingAuthorization = headers.get("Authorization");
    const isPlaceholderAuthorization = existingAuthorization === `Bearer ${PLACEHOLDER_TOKEN}`;

    if (state.accessToken && (!existingAuthorization || isPlaceholderAuthorization)) {
      headers.set("Authorization", `Bearer ${state.accessToken}`);
    } else if (isPlaceholderAuthorization && state.bootstrapComplete && !state.accessToken) {
      headers.delete("Authorization");
    }

    headers.set("X-Requested-With", "XMLHttpRequest");
    requestInit.headers = headers;

    let response = await nativeFetch(input, requestInit);

    if (
      response.status === 401 &&
      !alreadyRetried &&
      !url.pathname.startsWith(AUTH_API_PREFIX) &&
      state.bootstrapComplete
    ) {
      try {
        await refreshAccessToken();
        const retryHeaders = new Headers(requestInit.headers || {});
        if (state.accessToken) {
          retryHeaders.set("Authorization", `Bearer ${state.accessToken}`);
        }
        response = await nativeFetch(input, {
          ...requestInit,
          headers: retryHeaders,
          __scRetried: true,
        });
      } catch (error) {
        // A chamada original devolvera o 401 para o chamador tratar.
      }
    }

    return response;
  };

  document.addEventListener("visibilitychange", () => {
    if (document.hidden || !state.accessToken || !state.tokenExpiryMs) {
      return;
    }
    if (Date.now() >= state.tokenExpiryMs - REFRESH_MARGIN_MS) {
      void refreshAccessToken();
    }
  });

  window.SCAuth = {
    clearClientAuthState: () => clearAuthState({ keepRoutes: true }),
    ensureCsrfToken,
    getAccessToken: () => state.accessToken,
    getAdminApiBasePath: () => state.routeConfig.adminApiBasePath || "/api/v1/admin",
    getAdminUserApiBasePath: () => state.routeConfig.adminUserApiBasePath || "/api/v1/usuarios",
    getRouteConfig: () => ({ ...state.routeConfig }),
    getUserRoles: loadUserRoles,
    hydrateRouteAnchors,
    loadRouteConfig,
    logout,
    preloadRouteConfig,
    refreshAccessToken,
    storeAccessToken: (token) => {
      clearUserRolesCache();
      setAccessToken(token);
      void preloadRouteConfig(true);
      return Promise.resolve({ ...state.routeConfig });
    },
    waitUntilReady: () => state.bootstrapPromise || Promise.resolve(Boolean(state.accessToken)),
  };

  hydrateRouteAnchors();
  state.bootstrapPromise = bootstrapSession();
})();
