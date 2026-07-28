const form = document.getElementById("intern-permissions-form");
const loadUserButton = document.getElementById("load-user-btn");
const emailInput = document.getElementById("email");
const feedbackOverlay = document.getElementById("intern-feedback");
const feedbackCard = feedbackOverlay.querySelector(".confirm-card");
const feedbackIcon = feedbackOverlay.querySelector(".confirm-icon");
const feedbackTitle = feedbackOverlay.querySelector(".feedback-title");
const feedbackMessage = feedbackOverlay.querySelector(".feedback-message");
const feedbackOkButton = document.getElementById("feedback-ok-btn");
const rolesTrigger = document.getElementById("roles-trigger");
const rolesBackdrop = document.getElementById("roles-backdrop");
const rolesCard = document.getElementById("roles-card");
const rolesSearch = document.getElementById("roles-search");
const rolesOptions = document.getElementById("roles-options");
const rolesConfirm = document.getElementById("roles-confirm");
const selectedRolesContainer = document.getElementById("selected-roles");

const API_BASE_PATH = "/api/v1/estagiarios";
const selectedRoles = new Set();
let availableRoles = [];
let csrfToken = null;
let loadedUserEmail = null;

const getAccessToken = () => localStorage.getItem("sc_access_token");
const normalizeEmail = (value) => String(value || "").trim().toLowerCase();
const normalizeRole = (value) => String(value || "").trim().toUpperCase();

const extractErrorMessage = async (response, fallbackMessage) => {
  try {
    const payload = await response.json();
    if (payload && typeof payload === "object") {
      return payload.message || payload.error || fallbackMessage;
    }
  } catch (_) {
    // Mantem a mensagem padrao quando a resposta nao for JSON.
  }
  return fallbackMessage;
};

const showFeedback = (type, message) => {
  const isSuccess = type === "success";
  feedbackCard.classList.remove("is-success", "is-error");
  feedbackCard.classList.add(isSuccess ? "is-success" : "is-error");
  feedbackIcon.textContent = isSuccess ? "✓" : "✕";
  feedbackTitle.textContent = isSuccess ? "Permissoes atualizadas" : "Nao foi possivel salvar";
  feedbackMessage.textContent = message || "";
  feedbackMessage.hidden = !message;
  feedbackOverlay.classList.add("is-visible");
  feedbackOverlay.setAttribute("aria-hidden", "false");
};

const closeFeedback = () => {
  feedbackOverlay.classList.remove("is-visible");
  feedbackOverlay.setAttribute("aria-hidden", "true");
};

const redirectToLoginIfNeeded = (response) => {
  if (response.status === 401) {
    window.location.href = "/login";
    return true;
  }
  return false;
};

const carregarCsrfToken = async (forceRefresh = false) => {
  if (!forceRefresh && csrfToken) {
    return csrfToken;
  }

  const response = await fetch("/api/v1/auth/csrf", {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
    headers: getAccessToken() ? { Authorization: `Bearer ${getAccessToken()}` } : {},
  });
  if (!response.ok) {
    throw new Error("Falha ao obter token CSRF.");
  }

  const payload = await response.json();
  csrfToken = payload.token || null;
  if (!csrfToken) {
    throw new Error("Token CSRF ausente na resposta.");
  }
  return csrfToken;
};

const sortRoles = (roles) =>
  [...new Set((Array.isArray(roles) ? roles : []).map((role) => String(role || "").trim()).filter(Boolean))].sort(
    (first, second) => first.localeCompare(second, "pt-BR", { sensitivity: "base" }),
  );

const renderSelectedRoles = () => {
  selectedRolesContainer.innerHTML = "";
  if (selectedRoles.size === 0) {
    const empty = document.createElement("p");
    empty.className = "roles-empty";
    empty.textContent = "Nenhuma campanha selecionada.";
    selectedRolesContainer.appendChild(empty);
    return;
  }

  sortRoles([...selectedRoles]).forEach((role) => {
    const chip = document.createElement("span");
    chip.className = "role-chip";
    chip.textContent = role;

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.className = "role-chip-remove";
    removeButton.setAttribute("aria-label", `Remover ${role}`);
    removeButton.textContent = "-";
    removeButton.addEventListener("click", () => {
      selectedRoles.delete(role);
      const checkbox = rolesOptions.querySelector(`input[data-role="${CSS.escape(role)}"]`);
      if (checkbox) checkbox.checked = false;
      renderSelectedRoles();
    });

    chip.appendChild(removeButton);
    selectedRolesContainer.appendChild(chip);
  });
};

const filterRoleOptions = () => {
  const query = normalizeRole(rolesSearch.value);
  let visibleCount = 0;
  rolesOptions.querySelector("[data-search-empty]")?.remove();

  rolesOptions.querySelectorAll(".roles-option").forEach((option) => {
    const isVisible = !query || normalizeRole(option.dataset.role).includes(query);
    option.hidden = !isVisible;
    if (isVisible) visibleCount += 1;
  });

  if (query && visibleCount === 0) {
    const empty = document.createElement("p");
    empty.className = "roles-empty";
    empty.dataset.searchEmpty = "true";
    empty.textContent = "Nenhuma campanha encontrada.";
    rolesOptions.appendChild(empty);
  }
};

const renderRoleOptions = (roles) => {
  availableRoles = sortRoles(roles);
  rolesOptions.innerHTML = "";

  if (availableRoles.length === 0) {
    const empty = document.createElement("p");
    empty.className = "roles-empty";
    empty.textContent = "Nenhuma campanha cadastrada.";
    rolesOptions.appendChild(empty);
    return;
  }

  availableRoles.forEach((role) => {
    const label = document.createElement("label");
    label.className = "roles-option";
    label.dataset.role = role;

    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.dataset.role = role;
    checkbox.value = role;
    checkbox.checked = selectedRoles.has(role);

    const text = document.createElement("span");
    text.textContent = role;
    label.append(checkbox, text);
    rolesOptions.appendChild(label);
  });
  filterRoleOptions();
};

const setSelectedRoles = (roles) => {
  const validRoles = new Set(availableRoles.map(normalizeRole));
  selectedRoles.clear();
  (Array.isArray(roles) ? roles : []).forEach((role) => {
    const normalizedRole = normalizeRole(role);
    if (validRoles.has(normalizedRole)) {
      selectedRoles.add(availableRoles.find((availableRole) => normalizeRole(availableRole) === normalizedRole));
    }
  });
  renderRoleOptions(availableRoles);
  renderSelectedRoles();
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

const closeRolesCard = () => {
  rolesBackdrop.hidden = true;
  rolesCard.hidden = true;
  rolesTrigger.setAttribute("aria-expanded", "false");
};

const openRolesCard = () => {
  rolesBackdrop.hidden = false;
  rolesCard.hidden = false;
  rolesTrigger.setAttribute("aria-expanded", "true");
  rolesSearch.focus();
};

const clearLoadedUserState = () => {
  loadedUserEmail = null;
  selectedRoles.clear();
  rolesSearch.value = "";
  renderRoleOptions(availableRoles);
  renderSelectedRoles();
};

const loadAvailableRoles = async () => {
  const response = await fetch(`${API_BASE_PATH}/roles`, {
    method: "GET",
    credentials: "same-origin",
    headers: getAccessToken() ? { Authorization: `Bearer ${getAccessToken()}` } : {},
  });
  if (redirectToLoginIfNeeded(response)) return;
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, "Falha ao carregar as campanhas."));
  }
  renderRoleOptions(await response.json());
  renderSelectedRoles();
};

const loadIntern = async () => {
  const email = normalizeEmail(emailInput.value);
  if (!email) {
    showFeedback("error", "Informe um e-mail valido.");
    return;
  }

  const response = await fetch(`${API_BASE_PATH}/por-email?email=${encodeURIComponent(email)}`, {
    method: "GET",
    credentials: "same-origin",
    headers: getAccessToken() ? { Authorization: `Bearer ${getAccessToken()}` } : {},
  });
  if (redirectToLoginIfNeeded(response)) return;
  if (!response.ok) {
    clearLoadedUserState();
    showFeedback("error", await extractErrorMessage(response, "Estagiario nao encontrado."));
    return;
  }

  const user = await response.json();
  loadedUserEmail = normalizeEmail(user.email || email);
  setSelectedRoles(Array.isArray(user.roles) ? user.roles.map((role) => role.nome) : []);
};

rolesTrigger.addEventListener("click", () => (rolesCard.hidden ? openRolesCard() : closeRolesCard()));
rolesConfirm.addEventListener("click", () => {
  applySelectedRolesFromChecks();
  closeRolesCard();
});
rolesSearch.addEventListener("input", filterRoleOptions);
rolesBackdrop.addEventListener("click", closeRolesCard);
feedbackOkButton.addEventListener("click", closeFeedback);
emailInput.addEventListener("input", () => {
  if (loadedUserEmail && normalizeEmail(emailInput.value) !== loadedUserEmail) {
    clearLoadedUserState();
  }
});
loadUserButton.addEventListener("click", () => void loadIntern().catch((error) => showFeedback("error", error.message)));

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  applySelectedRolesFromChecks();
  const email = normalizeEmail(emailInput.value);
  if (!email || loadedUserEmail !== email) {
    showFeedback("error", "Carregue o estagiario novamente antes de salvar.");
    return;
  }

  const payload = { email, roles: sortRoles([...selectedRoles]) };
  const sendUpdate = async (token) =>
    fetch(`${API_BASE_PATH}/por-email`, {
      method: "PUT",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": token,
        ...(getAccessToken() ? { Authorization: `Bearer ${getAccessToken()}` } : {}),
      },
      body: JSON.stringify(payload),
    });

  try {
    let response = await sendUpdate(await carregarCsrfToken());
    if (response.status === 403) {
      response = await sendUpdate(await carregarCsrfToken(true));
    }
    if (redirectToLoginIfNeeded(response)) return;
    if (!response.ok) {
      showFeedback("error", await extractErrorMessage(response, "Falha ao atualizar as permissoes."));
      return;
    }

    const user = await response.json();
    loadedUserEmail = normalizeEmail(user.email || email);
    setSelectedRoles(Array.isArray(user.roles) ? user.roles.map((role) => role.nome) : []);
    csrfToken = null;
    showFeedback("success", "Permissoes do estagiario atualizadas com sucesso.");
  } catch (error) {
    showFeedback("error", error instanceof Error ? error.message : "Erro de conexao com o servidor.");
  }
});

if (!getAccessToken()) {
  window.location.href = "/login";
} else {
  void loadAvailableRoles().catch((error) => showFeedback("error", error.message));
}
