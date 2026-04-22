const state = {
  notifications: [],
  selectedRole: "",
  csrfToken: null,
  userRoles: [],
};

const REFRESH_ANIMATION_MS = 180;

const notificationsState = document.getElementById("notifications-state");
const notificationsList = document.getElementById("notifications-list");
const notificationCardTemplate = document.getElementById("notification-card-template");
const summaryGrid = document.getElementById("summary-grid");
const summaryCount = document.querySelector('[data-summary="count"]');
const summaryTotal = document.querySelector('[data-summary="total"]');
const summaryLaunched = document.querySelector('[data-summary="launched"]');
const technicalRoles = new Set(["ADMIN", "CONTABIL", "MANAGER", "SUPPORT", "CANDIDATO"]);
const roleFilterStorageKey = "sc_home_selected_role";
const getStoredSelectedRole = () => String(localStorage.getItem(roleFilterStorageKey) || "").trim();
const setSelectedRole = (role) => {
  const normalizedRole = String(role || "").trim();
  state.selectedRole = normalizedRole;

  if (normalizedRole) {
    localStorage.setItem(roleFilterStorageKey, normalizedRole);
  } else {
    localStorage.removeItem(roleFilterStorageKey);
  }

  window.dispatchEvent(
    new CustomEvent("sc:home-role-change", {
      detail: { role: normalizedRole },
    }),
  );
};
const roleFilterBox = document.getElementById("role-filter-box");
const roleFilterSelect = document.getElementById("role-filter-select");
const roleDropdown =
  typeof window.createRoleDropdown === "function" && roleFilterSelect
    ? window.createRoleDropdown({
        select: roleFilterSelect,
        onChange: async (value) => {
          setSelectedRole(value || "");
          try {
            await loadNotifications({ preserveVisibleContent: state.notifications.length > 0 });
          } catch (error) {
            setRefreshing(false);
            showState(
              error instanceof Error
                ? error.message
                : "Erro ao carregar notificacoes do candidato selecionado.",
              true,
            );
          }
        },
      })
    : null;

const getAccessToken = () => localStorage.getItem("sc_access_token");
const wait = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms));
const SUPPORT_UNCHECK_BLOCKED_MESSAGE =
  "Usuarios SUPPORT nao podem desmarcar comprovantes verificados.";

const loadCurrentUserRoles = async () => {
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
    if (response.status === 401) {
      window.location.href = "/login";
      return [];
    }
    if (!response.ok) return [];
    const roles = await response.json();
    return Array.isArray(roles)
      ? roles.map((role) => String(role || "").trim().toUpperCase()).filter(Boolean)
      : [];
  } catch (error) {
    return [];
  }
};

const isSupportUser = () => state.userRoles.includes("SUPPORT");

const extractErrorMessage = async (response, fallbackMessage) => {
  try {
    const payload = await response.json();
    if (payload && typeof payload === "object") {
      return payload.message || payload.error || fallbackMessage;
    }
  } catch (error) {
    // Keep fallback when backend returns no JSON body.
  }
  return fallbackMessage;
};

const buildRoleQuery = () => {
  if (!state.selectedRole) return "";
  const params = new URLSearchParams({ role: state.selectedRole });
  return `?${params.toString()}`;
};

const orderRoles = (roles) => {
  const normalizedRoles = Array.isArray(roles)
    ? [
        ...new Set(
          roles
            .map((role) => String(role || "").trim())
            .filter((role) => role && !technicalRoles.has(role.toUpperCase())),
        ),
      ].sort((a, b) => a.localeCompare(b, "pt-BR"))
    : [];
  return normalizedRoles;
};

const removeRoleFilterBox = () => {
  setSelectedRole("");
  if (roleFilterBox) {
    roleFilterBox.hidden = true;
  }
  if (roleFilterSelect) {
    roleFilterSelect.innerHTML = '<option value="" disabled selected>Selecione</option>';
  }
  roleDropdown?.clear();
};

const renderRoleOptions = (roles) => {
  roleDropdown?.setOptions(roles);
};

const applyRoleOptions = (roles) => {
  if (!roleFilterBox || !roleFilterSelect) return;
  const orderedRoles = orderRoles(roles);
  if (orderedRoles.length === 0) {
    removeRoleFilterBox();
    return;
  }

  renderRoleOptions(orderedRoles);

  const currentRole = getStoredSelectedRole();
  const nextRole = orderedRoles.includes(currentRole) ? currentRole : orderedRoles[0];

  roleFilterSelect.value = nextRole;
  roleFilterBox.hidden = false;
  roleDropdown?.setValue(nextRole);
  setSelectedRole(nextRole);
};

const formatCurrency = (value) =>
  Number(value || 0).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

const formatText = (value) => {
  const text = String(value || "").trim();
  return text || "-";
};

const formatDateTime = (value) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return date.toLocaleString("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const isNotificationChecked = (notification) =>
  notification && Object.prototype.hasOwnProperty.call(notification, "verificado")
    ? Boolean(notification.verificado)
    : Boolean(notification?.limpa);

const getPendingNotificationCount = () =>
  (Array.isArray(state.notifications) ? state.notifications : []).filter(
    (notification) => !isNotificationChecked(notification),
  ).length;

const notifyNavbarCountChanged = () => {
  window.dispatchEvent(
    new CustomEvent("notifications:changed", {
      detail: { pendingCount: getPendingNotificationCount() },
    }),
  );
};

const showState = (message, isError = false) => {
  if (!notificationsState) return;
  notificationsState.hidden = false;
  notificationsState.textContent = message;
  notificationsState.classList.toggle("is-error", isError);
  if (summaryGrid) summaryGrid.hidden = true;
  if (notificationsList) notificationsList.hidden = true;
};

const hideState = () => {
  if (!notificationsState) return;
  notificationsState.hidden = true;
  notificationsState.textContent = "";
  notificationsState.classList.remove("is-error");
  if (summaryGrid) summaryGrid.hidden = false;
  if (notificationsList) notificationsList.hidden = false;
};

const setRefreshing = (refreshing) => {
  [summaryGrid, notificationsList].forEach((element) => {
    if (!element) return;
    element.classList.toggle("is-refreshing", refreshing);
  });
};

const ensureCsrfToken = async (forceRefresh = false) => {
  if (!forceRefresh && state.csrfToken) return state.csrfToken;
  const response = await fetch("/api/v1/auth/csrf", {
    method: "GET",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
    },
  });
  if (!response.ok) {
    throw new Error("Nao foi possivel carregar o token CSRF.");
  }
  const data = await response.json();
  state.csrfToken = data.token || null;
  if (!state.csrfToken) {
    throw new Error("Token CSRF invalido.");
  }
  return state.csrfToken;
};

const syncNotificationCheckedState = (notificationId, verificado) => {
  const index = state.notifications.findIndex(
    (notification) => String(notification?.id) === String(notificationId),
  );
  if (index < 0) return;
  state.notifications[index] = {
    ...state.notifications[index],
    limpa: verificado === true,
    verificado: verificado === true,
  };
};

const renderSummary = () => {
  if (!summaryGrid) return;
  const notifications = Array.isArray(state.notifications) ? state.notifications : [];
  const totalValue = notifications.reduce((total, notification) => {
    return total + Number(notification?.valor || 0);
  }, 0);
  const launchedValue = notifications.reduce((total, notification) => {
    if (!isNotificationChecked(notification)) return total;
    return total + Number(notification?.valor || 0);
  }, 0);

  if (summaryCount) summaryCount.textContent = String(notifications.length);
  if (summaryTotal) summaryTotal.textContent = formatCurrency(totalValue);
  if (summaryLaunched) summaryLaunched.textContent = formatCurrency(launchedValue);
};

const renderNotifications = () => {
  if (!notificationsList || !notificationCardTemplate) return;
  notificationsList.innerHTML = "";
  const notifications = Array.isArray(state.notifications) ? state.notifications : [];

  if (!notifications.length) {
    const emptyCard = document.createElement("article");
    emptyCard.className = "card notification-card notification-card-empty";
    emptyCard.innerHTML =
      '<p class="notification-empty-copy">Nenhuma receita lancada para os filtros selecionados.</p>';
    notificationsList.appendChild(emptyCard);
    return;
  }

  notifications.forEach((notification) => {
    const node = notificationCardTemplate.content.cloneNode(true);
    const card = node.querySelector(".notification-card");
    if (!card) return;
    card.dataset.id = String(notification.id || "");
    card.dataset.itemId = String(notification.itemId || "");
    const checked = isNotificationChecked(notification);
    const supportLocked = isSupportUser() && checked;
    card.classList.toggle("is-cleaned", checked);
    card.querySelector('[data-field="role"]').textContent = formatText(notification.role);
    card.querySelector('[data-field="criadoEm"]').textContent = formatDateTime(notification.criadoEm);
    card.querySelector('[data-field="valor"]').textContent = formatCurrency(notification.valor);
    card.querySelector('[data-field="descricao"]').textContent = formatText(notification.descricao);
    card.querySelector('[data-field="razaoSocialNome"]').textContent = formatText(
      notification.razaoSocialNome,
    );
    const checkButton = card.querySelector(".notification-check-toggle");
    if (checkButton instanceof HTMLButtonElement) {
      checkButton.classList.toggle("is-checked", checked);
      checkButton.classList.toggle("is-locked", supportLocked);
      checkButton.disabled = supportLocked;
      checkButton.setAttribute("aria-pressed", String(checked));
      checkButton.setAttribute(
        "aria-label",
        supportLocked
          ? SUPPORT_UNCHECK_BLOCKED_MESSAGE
          : checked
            ? "Desmarcar comprovante"
            : "Marcar comprovante como verificado",
      );
      if (supportLocked) {
        checkButton.title = SUPPORT_UNCHECK_BLOCKED_MESSAGE;
      } else {
        checkButton.removeAttribute("title");
      }
    }
    notificationsList.appendChild(node);
  });
};

const renderPage = () => {
  renderSummary();
  renderNotifications();
  hideState();
  notifyNavbarCountChanged();
};

const patchVerificacao = async (itemId, verificado) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return null;
  }

  const csrfToken = await ensureCsrfToken(true);
  const response = await fetch(`/api/v1/itens/${itemId}/verificacao`, {
    method: "PATCH",
    credentials: "same-origin",
    redirect: "manual",
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      Accept: "application/json",
      "X-CSRF-TOKEN": csrfToken,
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ verificado }),
  });

  const isRedirect =
    response.type === "opaqueredirect" ||
    (typeof response.status === "number" && response.status >= 300 && response.status < 400) ||
    response.redirected;
  if (isRedirect || response.status === 401) {
    window.location.href = "/login";
    throw new Error("Sessao expirada. Faca login novamente.");
  }

  if (!response.ok) {
    const message = await extractErrorMessage(response, "Falha ao atualizar verificacao.");
    throw new Error(message);
  }

  return response.json();
};

const loadNotifications = async ({ preserveVisibleContent = false } = {}) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }

  if (preserveVisibleContent) {
    setRefreshing(true);
    await wait(REFRESH_ANIMATION_MS);
  } else {
    showState("Carregando notificacoes...");
  }

  const response = await fetch(`/api/v1/notificacoes${buildRoleQuery()}`, {
    method: "GET",
    credentials: "same-origin",
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status === 401) {
    window.location.href = "/login";
    return;
  }
  if (response.status === 403) {
    throw new Error("Acesso negado as notificacoes do candidato selecionado.");
  }
  if (!response.ok) {
    throw new Error("Nao foi possivel carregar as notificacoes.");
  }

  const payload = await response.json();
  state.notifications = Array.isArray(payload) ? payload : [];
  renderPage();
  requestAnimationFrame(() => {
    setRefreshing(false);
  });
};

const loadRoleFilterOptions = async () => {
  if (!roleFilterBox || !roleFilterSelect) return;
  const accessToken = getAccessToken();
  if (!accessToken) return;

  const response = await fetch("/api/v1/notificacoes/roles", {
    method: "GET",
    credentials: "same-origin",
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });

  if (response.status === 401) {
    window.location.href = "/login";
    return;
  }
  if (response.status === 403) {
    removeRoleFilterBox();
    return;
  }
  if (!response.ok) {
    removeRoleFilterBox();
    return;
  }

  const roles = await response.json();
  applyRoleOptions(roles);
};

const bindEvents = () => {
  if (!notificationsList) return;
  notificationsList.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const checkButton = target.closest(".notification-check-toggle");
    if (!(checkButton instanceof HTMLButtonElement)) return;

    const card = checkButton.closest(".notification-card");
    const notificationId = card?.dataset.id;
    const itemId = card?.dataset.itemId;
    if (!notificationId || !itemId) return;
    if (isSupportUser() && checkButton.classList.contains("is-checked")) {
      showState(SUPPORT_UNCHECK_BLOCKED_MESSAGE, true);
      return;
    }

    const nextChecked = !checkButton.classList.contains("is-checked");
    checkButton.disabled = true;
    try {
      const updated = await patchVerificacao(itemId, nextChecked);
      const persistedChecked = Boolean(updated?.verificado);
      syncNotificationCheckedState(notificationId, persistedChecked);
      renderPage();
    } catch (error) {
      showState(
        error instanceof Error ? error.message : "Erro ao atualizar verificacao.",
        true,
      );
    } finally {
      checkButton.disabled = false;
    }
  });
};

const init = async () => {
  bindEvents();
  try {
    state.userRoles = await loadCurrentUserRoles();
  } catch (error) {
    state.userRoles = [];
  }
  try {
    await loadRoleFilterOptions();
  } catch (error) {
    removeRoleFilterBox();
  }

  try {
    await loadNotifications();
  } catch (error) {
    showState(
      error instanceof Error ? error.message : "Erro ao carregar notificacoes. Tente novamente.",
      true,
    );
  }
};

init();
