const root = document.documentElement;
const form = document.getElementById("update-user-form");
const loadUserButton = document.getElementById("load-user-btn");
const emailInput = document.getElementById("email");
const senhaInput = document.getElementById("senha");
const confirmOverlay = document.getElementById("update-user-feedback");
const confirmCard = confirmOverlay.querySelector(".confirm-card");
const feedbackOkBtn = document.getElementById("feedback-ok-btn");
const confirmIcon = confirmOverlay.querySelector(".confirm-icon");
const confirmTitle = confirmOverlay.querySelector(".feedback-title");
const confirmMessage = confirmOverlay.querySelector(".feedback-message");
let toggle = document.querySelector(".theme-toggle");
let csrfToken = null;
const rolesTrigger = document.getElementById("roles-trigger");
const rolesBackdrop = document.getElementById("roles-backdrop");
const rolesCard = document.getElementById("roles-card");
const rolesSearch = document.getElementById("roles-search");
const rolesOptions = document.getElementById("roles-options");
const rolesConfirm = document.getElementById("roles-confirm");
const selectedRolesContainer = document.getElementById("selected-roles");
const rolesHidden = document.getElementById("roles-hidden");
const selectedRoles = new Set();
const ROLE_SUPPORT = "SUPPORT";
const ROLE_MANAGER = "MANAGER";
const PRIORITY_ROLES = ["ADMIN", "MANAGER", "CONTABIL", "SUPPORT"];
const ADMIN_ROUTE_CONFIG_ERROR_MESSAGE =
  "Nao foi possivel carregar as rotas administrativas desta sessao.";
let availableRoles = [];
let adminRoutePaths = null;
let loadedUserEmail = null;

const readCookie = (name) => {
  const match = document.cookie.match(new RegExp("(^| )" + name + "=([^;]+)"));
  return match ? decodeURIComponent(match[2]) : null;
};

const writeCookie = (name, value, days = 365) => {
  const expires = new Date(Date.now() + days * 864e5).toUTCString();
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=/`;
};

const savedTheme = readCookie("theme") || localStorage.getItem("theme");
root.dataset.theme = savedTheme === "dark" ? "dark" : "light";
writeCookie("theme", root.dataset.theme);
localStorage.setItem("theme", root.dataset.theme);

const extractErrorMessage = async (response, fallbackMessage) => {
  try {
    const payload = await response.json();
    if (payload && typeof payload === "object") {
      return payload.message || payload.error || fallbackMessage;
    }
  } catch (error) {
    // Keep fallback message when payload is not JSON.
  }
  return fallbackMessage;
};

const buildAdminUserErrorMessage = async (response, fallbackMessage) => {
  if (response.status === 401) {
    return "Sessao expirada. Faca login novamente.";
  }
  if (response.status === 403) {
    return "Voce nao tem permissao para administrar usuarios nesta sessao.";
  }
  if (response.status === 404) {
    return "Usuario nao encontrado para o email informado.";
  }
  return extractErrorMessage(response, fallbackMessage);
};

const carregarCsrfToken = async (forceRefresh = false) => {
  if (!forceRefresh && csrfToken) {
    return csrfToken;
  }

  const response = await fetch("/api/v1/auth/csrf", {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
    headers: getAccessToken()
      ? {
          Authorization: `Bearer ${getAccessToken()}`,
        }
      : {},
  });
  if (!response.ok) {
    throw new Error("Falha ao obter token CSRF");
  }
  const data = await response.json();
  csrfToken = data.token || null;
  if (!csrfToken) {
    throw new Error("Token CSRF ausente na resposta");
  }
  return csrfToken;
};

const updateLabel = () => {
  if (!toggle) return;
  const isDark = root.dataset.theme === "dark";
  toggle.setAttribute("aria-pressed", isDark ? "true" : "false");
  toggle.setAttribute("aria-label", isDark ? "Ativar modo claro" : "Ativar modo escuro");
  toggle.querySelector(".theme-icon").innerHTML =
    '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="4.6" fill="currentColor"/><g stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><line x1="12" y1="2.4" x2="12" y2="5"/><line x1="12" y1="19" x2="12" y2="21.6"/><line x1="2.4" y1="12" x2="5" y2="12"/><line x1="19" y1="12" x2="21.6" y2="12"/><line x1="5.1" y1="5.1" x2="6.9" y2="6.9"/><line x1="17.1" y1="17.1" x2="18.9" y2="18.9"/><line x1="17.1" y1="6.9" x2="18.9" y2="5.1"/><line x1="5.1" y1="18.9" x2="6.9" y2="17.1"/></g></svg>';
};

const bindThemeToggle = () => {
  toggle = document.querySelector(".theme-toggle");
  if (!toggle) return;
  if (toggle.dataset.navbarManaged === "true") return;
  updateLabel();
  toggle.addEventListener("click", () => {
    const isDark = root.dataset.theme === "dark";
    root.dataset.theme = isDark ? "light" : "dark";
    writeCookie("theme", root.dataset.theme);
    localStorage.setItem("theme", root.dataset.theme);
    updateLabel();
  });
};

bindThemeToggle();

const getAccessToken = () => localStorage.getItem("sc_access_token");
const resolveAdminRoutePaths = async (forceRefresh = false) => {
  if (!forceRefresh && adminRoutePaths) {
    return adminRoutePaths;
  }
  if (!window.SCAuth?.requireAdminRouteConfig) {
    throw new Error(ADMIN_ROUTE_CONFIG_ERROR_MESSAGE);
  }

  adminRoutePaths = await window.SCAuth.requireAdminRouteConfig(forceRefresh);
  return adminRoutePaths;
};

window.addEventListener("sc:routes-updated", () => {
  adminRoutePaths = null;
});

if (!getAccessToken()) {
  window.location.href = "/login";
}

const syncRolesHidden = () => {
  const roles = Array.from(selectedRoles);
  rolesHidden.value = roles.join(",");
  rolesHidden.setCustomValidity(roles.length > 0 ? "" : "Selecione ao menos uma role.");
};

const normalizeEmail = (value) => String(value || "").trim().toLowerCase();

const hasSupportManagerConflict = (roles) => {
  const normalized = new Set((roles || []).map((role) => String(role || "").trim().toUpperCase()));
  return normalized.has(ROLE_SUPPORT) && normalized.has(ROLE_MANAGER);
};

const normalizeRoleText = (value) =>
  String(value || "")
    .trim()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase();

const sortRoles = (roles) =>
  [...new Set((Array.isArray(roles) ? roles : []).map((role) => String(role || "").trim()).filter(Boolean))].sort(
    (a, b) => {
      const roleA = normalizeRoleText(a);
      const roleB = normalizeRoleText(b);
      const priorityA = PRIORITY_ROLES.indexOf(roleA);
      const priorityB = PRIORITY_ROLES.indexOf(roleB);

      if (priorityA >= 0 || priorityB >= 0) {
        if (priorityA < 0) return 1;
        if (priorityB < 0) return -1;
        return priorityA - priorityB;
      }

      return a.localeCompare(b, "pt-BR", { sensitivity: "base" });
    },
  );

const removeRoleSearchEmpty = () => {
  const empty = rolesOptions?.querySelector("[data-role-search-empty='true']");
  if (empty) empty.remove();
};

const renderRoleSearchEmpty = () => {
  const empty = document.createElement("p");
  empty.className = "roles-empty";
  empty.dataset.roleSearchEmpty = "true";
  empty.textContent = "Nenhuma role encontrada.";
  rolesOptions.appendChild(empty);
};

const renderRoleOptions = (roles) => {
  if (!rolesOptions) return;

  availableRoles = sortRoles(roles);

  rolesOptions.innerHTML = "";

  if (availableRoles.length === 0) {
    const empty = document.createElement("p");
    empty.className = "roles-empty";
    empty.textContent = "Nenhuma role cadastrada.";
    rolesOptions.appendChild(empty);
    return;
  }

  availableRoles.forEach((role) => {
    const label = document.createElement("label");
    label.className = "roles-option";
    label.dataset.role = role;

    const input = document.createElement("input");
    input.type = "checkbox";
    input.value = role;

    const span = document.createElement("span");
    span.textContent = role;

    label.appendChild(input);
    label.appendChild(span);
    rolesOptions.appendChild(label);
  });

  filterRoleOptions();
};

const loadAvailableRoles = async () => {
  const { adminApiBasePath } = await resolveAdminRoutePaths();
  const response = await fetch(`${adminApiBasePath}/roles/disponiveis`, {
    method: "GET",
    headers: {
      Authorization: `Bearer ${getAccessToken()}`,
    },
    credentials: "same-origin",
  });

  if (response.status === 401) {
    window.location.href = "/login";
    return;
  }

  const data = await response.json().catch(() => []);
  if (!response.ok) {
    throw new Error("Falha ao carregar roles cadastradas.");
  }

  const roles = Array.isArray(data) ? data.map((item) => String(item || "").trim()).filter(Boolean) : [];
  renderRoleOptions(roles);
};

const setCheckedRoles = (roles) => {
  const normalizedRoles = Array.isArray(roles)
    ? [...new Set(roles.map((role) => String(role || "").trim()).filter(Boolean))]
    : [];
  const missingRoles = normalizedRoles.filter((role) => !availableRoles.includes(role));
  if (missingRoles.length > 0) {
    renderRoleOptions([...availableRoles, ...missingRoles]);
  }

  rolesOptions.querySelectorAll("input[type='checkbox']").forEach((checkbox) => {
    checkbox.checked = normalizedRoles.includes(checkbox.value);
  });
  selectedRoles.clear();
  normalizedRoles.forEach((role) => selectedRoles.add(role));
  renderSelectedRoles();
};

const clearLoadedUserState = ({ keepEmail = true } = {}) => {
  loadedUserEmail = null;
  senhaInput.value = "";
  selectedRoles.clear();
  if (rolesOptions) {
    rolesOptions.querySelectorAll("input[type='checkbox']").forEach((checkbox) => {
      checkbox.checked = false;
      checkbox.parentElement.hidden = false;
    });
  }
  if (rolesSearch) {
    rolesSearch.value = "";
  }
  renderSelectedRoles();
  if (!keepEmail) {
    emailInput.value = "";
  }
};

const renderSelectedRoles = () => {
  const roles = Array.from(selectedRoles);
  selectedRolesContainer.innerHTML = "";
  if (roles.length === 0) {
    const empty = document.createElement("p");
    empty.className = "roles-empty";
    empty.textContent = "Nenhuma role selecionada.";
    selectedRolesContainer.appendChild(empty);
    syncRolesHidden();
    return;
  }

  roles.forEach((role) => {
    const chip = document.createElement("span");
    chip.className = "role-chip";
    chip.textContent = role;

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "role-chip-remove";
    remove.setAttribute("aria-label", `Remover ${role}`);
    remove.textContent = "-";
    remove.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      selectedRoles.delete(role);
      const checkbox = rolesOptions.querySelector(`input[value="${role}"]`);
      if (checkbox) checkbox.checked = false;
      renderSelectedRoles();
    });

    chip.appendChild(remove);
    selectedRolesContainer.appendChild(chip);
  });

  syncRolesHidden();
};

const openRolesCard = () => {
  rolesBackdrop.hidden = false;
  rolesCard.hidden = false;
  rolesTrigger.setAttribute("aria-expanded", "true");
  rolesSearch.focus();
};

const closeRolesCard = () => {
  rolesBackdrop.hidden = true;
  rolesCard.hidden = true;
  rolesTrigger.setAttribute("aria-expanded", "false");
};

const applySelectedRolesFromChecks = () => {
  selectedRoles.clear();
  rolesOptions.querySelectorAll("input[type='checkbox']").forEach((checkbox) => {
    if (checkbox.checked) {
      selectedRoles.add(checkbox.value);
    }
  });
  renderSelectedRoles();
};

const filterRoleOptions = () => {
  if (!rolesSearch || !rolesOptions) return;

  const query = normalizeRoleText(rolesSearch.value);
  let visibleCount = 0;
  removeRoleSearchEmpty();

  rolesOptions.querySelectorAll(".roles-option").forEach((option) => {
    const text = normalizeRoleText(option.dataset.role || option.textContent);
    const isVisible = !query || text.includes(query);
    option.hidden = !isVisible;
    if (isVisible) visibleCount += 1;
  });

  if (query && visibleCount === 0) {
    renderRoleSearchEmpty();
  }
};

rolesTrigger.addEventListener("click", () => {
  if (rolesCard.hidden) {
    openRolesCard();
    return;
  }
  closeRolesCard();
});

rolesConfirm.addEventListener("click", () => {
  applySelectedRolesFromChecks();
  closeRolesCard();
});

rolesSearch.addEventListener("input", filterRoleOptions);
rolesSearch.addEventListener("search", filterRoleOptions);
rolesBackdrop.addEventListener("click", closeRolesCard);

document.addEventListener("click", (event) => {
  if (rolesCard.hidden) return;
  const target = event.target;
  if (!(target instanceof Node)) return;
  if (!rolesCard.contains(target) && !rolesTrigger.contains(target)) {
    closeRolesCard();
  }
});

const showFeedback = (type, message) => {
  confirmCard.classList.remove("is-success", "is-error");
  confirmCard.classList.add(type === "success" ? "is-success" : "is-error");
  confirmIcon.textContent = type === "success" ? "\u2713" : "\u2715";
  confirmTitle.textContent =
    type === "success" ? "Usuario atualizado" : "Erro ao atualizar usuario";
  confirmMessage.textContent = message || "";
  confirmMessage.hidden = !message;
  confirmOverlay.classList.add("is-visible");
  confirmOverlay.setAttribute("aria-hidden", "false");
};

feedbackOkBtn.addEventListener("click", () => {
  confirmOverlay.classList.remove("is-visible");
  confirmOverlay.setAttribute("aria-hidden", "true");
});

emailInput.addEventListener("input", () => {
  if (!loadedUserEmail) {
    return;
  }
  if (normalizeEmail(emailInput.value) === loadedUserEmail) {
    return;
  }
  clearLoadedUserState();
});

const carregarUsuario = async () => {
  const email = normalizeEmail(emailInput.value);
  if (!email) {
    clearLoadedUserState();
    showFeedback("error", "Informe um email valido.");
    return;
  }

  const { adminUserApiBasePath } = await resolveAdminRoutePaths();
  const response = await fetch(
    `${adminUserApiBasePath}/por-email?email=${encodeURIComponent(email)}`,
    {
      method: "GET",
      headers: {
        Authorization: `Bearer ${getAccessToken()}`,
      },
      credentials: "same-origin",
    },
  );

  const data = await response.json().catch(() => ({}));
  if (response.status === 401) {
    window.location.href = "/login";
    return;
  }
  if (response.status === 403) {
    clearLoadedUserState();
    showFeedback("error", "Voce nao tem permissao para administrar usuarios nesta sessao.");
    return;
  }
  if (!response.ok) {
    clearLoadedUserState();
    showFeedback(
      "error",
      data.message || data.error || "Usuario nao encontrado para o email informado.",
    );
    return;
  }

  loadedUserEmail = email;
  senhaInput.value = "";
  const roles = Array.isArray(data.roles) ? data.roles.map((item) => item.nome).filter(Boolean) : [];
  setCheckedRoles(roles);
};

loadUserButton.addEventListener("click", async () => {
  try {
    await carregarUsuario();
  } catch (error) {
    showFeedback(
      "error",
      error instanceof Error ? error.message : "Erro de conexao com o servidor.",
    );
  }
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const email = emailInput.value.trim();
  applySelectedRolesFromChecks();
  const selectedRolesList = Array.from(selectedRoles);
  if (!email) {
    showFeedback("error", "Informe um email valido.");
    return;
  }
  if (selectedRolesList.length === 0) {
    showFeedback("error", "Selecione ao menos uma role.");
    return;
  }
  if (hasSupportManagerConflict(selectedRolesList)) {
    showFeedback("error", "Usuario nao pode ter as roles SUPPORT e MANAGER ao mesmo tempo.");
    return;
  }
  if (!loadedUserEmail || loadedUserEmail !== normalizeEmail(email)) {
    showFeedback("error", "Carregue o usuario novamente antes de salvar as alteracoes.");
    return;
  }

  const payload = {
    email,
    senha: senhaInput.value.trim() ? senhaInput.value : null,
    roles: selectedRolesList,
  };

  try {
    const { adminUserApiBasePath } = await resolveAdminRoutePaths();
    const enviarAtualizacao = async (csrf) =>
      fetch(`${adminUserApiBasePath}/por-email`, {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-TOKEN": csrf,
          Authorization: `Bearer ${getAccessToken()}`,
        },
        credentials: "same-origin",
        body: JSON.stringify(payload),
      });

    let tokenCsrf = await carregarCsrfToken();
    let response = await enviarAtualizacao(tokenCsrf);

    if (response.status === 403) {
      tokenCsrf = await carregarCsrfToken(true);
      response = await enviarAtualizacao(tokenCsrf);
    }

    if (response.status === 401) {
      window.location.href = "/login";
      return;
    }
    if (response.status === 403) {
      showFeedback("error", await buildAdminUserErrorMessage(response, "Acesso negado."));
      return;
    }
    if (response.status === 404) {
      clearLoadedUserState();
      showFeedback(
        "error",
        "Usuario nao encontrado para o email informado. Carregue o usuario novamente.",
      );
      return;
    }

    if (!response.ok) {
      showFeedback(
        "error",
        await buildAdminUserErrorMessage(response, "Falha ao atualizar usuario."),
      );
      return;
    }

    const data = await response.json().catch(() => ({}));
    const roles = Array.isArray(data.roles) ? data.roles.map((item) => item.nome).filter(Boolean) : [];
    loadedUserEmail = normalizeEmail(data.email || email);
    setCheckedRoles(roles);
    senhaInput.value = "";
    csrfToken = null;
    showFeedback("success", "Usuario atualizado com sucesso.");
  } catch (error) {
    showFeedback(
      "error",
      error instanceof Error ? error.message : "Erro de conexao com o servidor.",
    );
  }
});

const init = async () => {
  renderSelectedRoles();
  try {
    await resolveAdminRoutePaths();
    await loadAvailableRoles();
  } catch (error) {
    showFeedback(
      "error",
      error instanceof Error ? error.message : "Nao foi possivel carregar as roles cadastradas.",
    );
  }
};

init();
