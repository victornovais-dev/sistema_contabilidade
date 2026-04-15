const state = {
  notifications: [],
  selectedRole: "",
};

const REFRESH_ANIMATION_MS = 180;

const notificationsState = document.getElementById("notifications-state");
const notificationsList = document.getElementById("notifications-list");
const notificationCardTemplate = document.getElementById("notification-card-template");
const summaryGrid = document.getElementById("summary-grid");
const summaryCount = document.querySelector('[data-summary="count"]');
const summaryTotal = document.querySelector('[data-summary="total"]');
const summaryLatest = document.querySelector('[data-summary="latest"]');
const roleFilterBox = document.getElementById("role-filter-box");
const roleFilterSelect = document.getElementById("role-filter-select");
const roleDropdown =
  typeof window.createRoleDropdown === "function" && roleFilterSelect
    ? window.createRoleDropdown({
        select: roleFilterSelect,
        onChange: async (value) => {
          state.selectedRole = value || "";
          try {
            await loadNotifications({ preserveVisibleContent: state.notifications.length > 0 });
          } catch (error) {
            setRefreshing(false);
            showState(
              error instanceof Error
                ? error.message
                : "Erro ao carregar notificacoes do politico selecionado.",
              true,
            );
          }
        },
      })
    : null;

const getAccessToken = () => localStorage.getItem("sc_access_token");
const wait = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms));

const buildRoleQuery = () => {
  if (!state.selectedRole) return "";
  const params = new URLSearchParams({ role: state.selectedRole });
  return `?${params.toString()}`;
};

const orderRoles = (roles) => {
  const normalizedRoles = Array.isArray(roles)
    ? [...new Set(roles.map((role) => String(role || "").trim()).filter(Boolean))]
    : [];
  if (!normalizedRoles.length) return [];

  const firstRole = normalizedRoles.includes("ADMIN") ? "ADMIN" : normalizedRoles[0];
  const remaining = normalizedRoles.filter((role) => role !== firstRole && role !== "MANAGER");
  remaining.sort((a, b) => a.localeCompare(b, "pt-BR"));

  if (!normalizedRoles.includes("MANAGER") || firstRole === "MANAGER") {
    return [firstRole, ...remaining];
  }
  return [firstRole, "MANAGER", ...remaining];
};

const removeRoleFilterBox = () => {
  state.selectedRole = "";
  if (roleFilterBox) {
    roleFilterBox.hidden = true;
  }
  if (roleFilterSelect) {
    roleFilterSelect.innerHTML = '<option value="" disabled selected>Selecione</option>';
  }
  roleDropdown?.clear();
};

const applyRoleOptions = (roles) => {
  if (!roleFilterBox || !roleFilterSelect) return;
  const orderedRoles = orderRoles(roles);
  if (orderedRoles.length <= 1) {
    removeRoleFilterBox();
    return;
  }

  roleDropdown?.setOptions(orderedRoles);
  if (
    (!state.selectedRole || !orderedRoles.includes(state.selectedRole)) &&
    orderedRoles.includes("ADMIN")
  ) {
    state.selectedRole = "ADMIN";
  } else if (!state.selectedRole || !orderedRoles.includes(state.selectedRole)) {
    state.selectedRole = orderedRoles[0];
  }

  roleFilterSelect.value = state.selectedRole;
  roleFilterBox.hidden = false;
  roleDropdown?.setValue(state.selectedRole);
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

const renderSummary = () => {
  if (!summaryGrid) return;
  const notifications = Array.isArray(state.notifications) ? state.notifications : [];
  const totalValue = notifications.reduce((total, notification) => {
    return total + Number(notification?.valor || 0);
  }, 0);
  const latest = notifications[0]?.criadoEm || null;

  if (summaryCount) summaryCount.textContent = String(notifications.length);
  if (summaryTotal) summaryTotal.textContent = formatCurrency(totalValue);
  if (summaryLatest) summaryLatest.textContent = formatDateTime(latest);
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
    card.querySelector('[data-field="role"]').textContent = formatText(notification.role);
    card.querySelector('[data-field="criadoEm"]').textContent = formatDateTime(notification.criadoEm);
    card.querySelector('[data-field="valor"]').textContent = formatCurrency(notification.valor);
    card.querySelector('[data-field="descricao"]').textContent = formatText(notification.descricao);
    card.querySelector('[data-field="razaoSocialNome"]').textContent = formatText(
      notification.razaoSocialNome,
    );
    notificationsList.appendChild(node);
  });
};

const renderPage = () => {
  renderSummary();
  renderNotifications();
  hideState();
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
    throw new Error("Acesso negado as notificacoes do politico selecionado.");
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
  if (!response.ok) {
    removeRoleFilterBox();
    return;
  }

  const roles = await response.json();
  applyRoleOptions(roles);
};

const init = async () => {
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
