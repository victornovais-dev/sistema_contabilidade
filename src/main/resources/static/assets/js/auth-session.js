(function () {
  const TOKEN_STORAGE_KEY = "sc_access_token";
  const PLACEHOLDER_TOKEN = "__sc_bootstrap_pending__";
  const REFRESH_MARGIN_MS = 60_000;
  const ROUTES_ENDPOINT = "/api/v1/auth/routes";
  const AUTH_API_PREFIX = "/api/v1/auth/";
  const state = {
    accessToken: null,
    tokenExpiryMs: null,
    refreshTimer: null,
    csrfToken: null,
    bootstrapComplete: false,
    bootstrapPromise: null,
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

  const dispatchAuthEvent = (name, detail) => {
    window.dispatchEvent(new CustomEvent(name, { detail }));
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
    state.accessToken = null;
    state.tokenExpiryMs = null;
    state.csrfToken = null;
    state.bootstrapComplete = true;
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

  const loadRouteConfig = async () => {
    try {
      const response = await nativeFetch(ROUTES_ENDPOINT, {
        method: "GET",
        credentials: "same-origin",
        cache: "no-store",
        headers: {
          "X-Requested-With": "XMLHttpRequest",
          ...(state.accessToken ? { Authorization: `Bearer ${state.accessToken}` } : {}),
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
    }
  };

  const refreshAccessToken = async () => {
    const csrfToken = await ensureCsrfToken();
    const response = await nativeFetch("/api/v1/auth/refresh", {
      method: "POST",
      credentials: "same-origin",
      headers: {
        "X-CSRF-TOKEN": csrfToken,
        "X-Requested-With": "XMLHttpRequest",
      },
    });

    if (!response.ok) {
      clearAuthState();
      throw new Error("Nao foi possivel renovar a sessao.");
    }

    const payload = await response.json().catch(() => ({}));
    if (!payload.accessToken) {
      clearAuthState();
      throw new Error("Resposta sem access token.");
    }

    setAccessToken(payload.accessToken);
    await loadRouteConfig();
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
    try {
      const csrfToken = await ensureCsrfToken();
      await nativeFetch("/api/v1/auth/logout", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "X-CSRF-TOKEN": csrfToken,
          "X-Requested-With": "XMLHttpRequest",
        },
      });
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
    getAccessToken: () => state.accessToken,
    getAdminApiBasePath: () => state.routeConfig.adminApiBasePath || "/api/v1/admin",
    getAdminUserApiBasePath: () => state.routeConfig.adminUserApiBasePath || "/api/v1/usuarios",
    getRouteConfig: () => ({ ...state.routeConfig }),
    hydrateRouteAnchors,
    loadRouteConfig,
    logout,
    refreshAccessToken,
    storeAccessToken: (token) => {
      setAccessToken(token);
      return loadRouteConfig();
    },
    waitUntilReady: () => state.bootstrapPromise || Promise.resolve(Boolean(state.accessToken)),
  };

  hydrateRouteAnchors();
  state.bootstrapPromise = bootstrapSession();
})();
