const root = document.documentElement;
const form = document.getElementById("create-user-form");
const confirmOverlay = document.getElementById("create-user-feedback");
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
let availableRoles = [];

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

const carregarCsrfToken = async () => {
  const response = await fetch("/api/v1/auth/csrf", {
    method: "GET",
    credentials: "same-origin",
  });
  if (!response.ok) {
    throw new Error("Falha ao obter token CSRF");
  }
  const data = await response.json();
  csrfToken = data.token || null;
  if (!csrfToken) {
    throw new Error("Token CSRF ausente na resposta");
  }
};

const updateLabel = () => {
  if (!toggle) return;
  const isDark = root.dataset.theme === "dark";
  toggle.setAttribute("aria-pressed", isDark ? "true" : "false");
  toggle.setAttribute("aria-label", isDark ? "Ativar modo claro" : "Ativar modo escuro");
  toggle.querySelector(".theme-icon").innerHTML = isDark
    ? '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="4.6" fill="currentColor"/><g stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><line x1="12" y1="2.4" x2="12" y2="5"/><line x1="12" y1="19" x2="12" y2="21.6"/><line x1="2.4" y1="12" x2="5" y2="12"/><line x1="19" y1="12" x2="21.6" y2="12"/><line x1="5.1" y1="5.1" x2="6.9" y2="6.9"/><line x1="17.1" y1="17.1" x2="18.9" y2="18.9"/><line x1="17.1" y1="6.9" x2="18.9" y2="5.1"/><line x1="5.1" y1="18.9" x2="6.9" y2="17.1"/></g></svg>'
    : '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="4.6" fill="currentColor"/><g stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><line x1="12" y1="2.4" x2="12" y2="5"/><line x1="12" y1="19" x2="12" y2="21.6"/><line x1="2.4" y1="12" x2="5" y2="12"/><line x1="19" y1="12" x2="21.6" y2="12"/><line x1="5.1" y1="5.1" x2="6.9" y2="6.9"/><line x1="17.1" y1="17.1" x2="18.9" y2="18.9"/><line x1="17.1" y1="6.9" x2="18.9" y2="5.1"/><line x1="5.1" y1="18.9" x2="6.9" y2="17.1"/></g></svg>';
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

const token = localStorage.getItem("sc_access_token");
if (!token) {
  window.location.href = "/login";
}

const syncRolesHidden = () => {
  const roles = Array.from(selectedRoles);
  rolesHidden.value = roles.join(",");
  rolesHidden.setCustomValidity(roles.length > 0 ? "" : "Selecione ao menos uma role.");
};

const hasSupportManagerConflict = (roles) => {
  const normalized = new Set((roles || []).map((role) => String(role || "").trim().toUpperCase()));
  return normalized.has(ROLE_SUPPORT) && normalized.has(ROLE_MANAGER);
};

const renderRoleOptions = (roles) => {
  if (!rolesOptions) return;

  availableRoles = Array.isArray(roles)
    ? [...new Set(roles.map((role) => String(role || "").trim()).filter(Boolean))].sort((a, b) =>
        a.localeCompare(b, "pt-BR"),
      )
    : [];

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

    const input = document.createElement("input");
    input.type = "checkbox";
    input.value = role;
    input.checked = selectedRoles.has(role);

    const span = document.createElement("span");
    span.textContent = role;

    label.appendChild(input);
    label.appendChild(span);
    rolesOptions.appendChild(label);
  });
};

const renderRoleLoadError = (message) => {
  if (!rolesOptions) return;
  rolesOptions.innerHTML = "";
  const empty = document.createElement("p");
  empty.className = "roles-empty";
  empty.textContent = message || "Nao foi possivel carregar as roles cadastradas.";
  rolesOptions.appendChild(empty);
};

const loadAvailableRoles = async () => {
  const response = await fetch("/api/v1/admin/roles", {
    method: "GET",
    headers: {
      Authorization: `Bearer ${token}`,
    },
    credentials: "same-origin",
  });

  if (response.status === 401) {
    window.location.href = "/login";
    return;
  }

  if (!response.ok) {
    throw new Error("Falha ao carregar roles cadastradas.");
  }

  const data = await response.json().catch(() => []);
  const roles = Array.isArray(data) ? data.map((item) => item?.nome).filter(Boolean) : [];
  renderRoleOptions(roles);
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
  const query = rolesSearch.value.trim().toLowerCase();
  rolesOptions.querySelectorAll(".roles-option").forEach((option) => {
    const text = option.textContent.toLowerCase();
    option.hidden = !text.includes(query);
  });
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
  confirmTitle.textContent = type === "success" ? "Usuario criado" : "Erro ao criar usuario";
  confirmMessage.textContent =
    message ||
    (type === "success"
      ? "O usuario foi criado com sucesso."
      : "Nao foi possivel concluir a criacao do usuario.");
  confirmMessage.hidden = false;
  confirmOverlay.classList.add("is-visible");
  confirmOverlay.setAttribute("aria-hidden", "false");
};

feedbackOkBtn.addEventListener("click", () => {
  confirmOverlay.classList.remove("is-visible");
  confirmOverlay.setAttribute("aria-hidden", "true");
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  confirmOverlay.classList.remove("is-visible");
  confirmOverlay.setAttribute("aria-hidden", "true");

  applySelectedRolesFromChecks();
  const selectedRolesList = Array.from(selectedRoles);
  if (selectedRolesList.length === 0) {
    showFeedback("error", "Selecione ao menos uma role.");
    return;
  }
  if (hasSupportManagerConflict(selectedRolesList)) {
    showFeedback("error", "Usuario nao pode ter as roles SUPPORT e MANAGER ao mesmo tempo.");
    return;
  }

  const payload = {
    nome: document.getElementById("nome").value.trim(),
    email: document.getElementById("email").value.trim(),
    senha: document.getElementById("senha").value,
    role: selectedRolesList[0] || null,
    roles: selectedRolesList,
  };

  try {
    if (!csrfToken) {
      await carregarCsrfToken();
    }
    const response = await fetch("/api/v1/usuarios", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": csrfToken,
        Authorization: "Bearer " + token,
      },
      body: JSON.stringify(payload),
    });
    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      showFeedback("error", data.message || data.error || "Falha ao criar usuario");
      return;
    }

    form.reset();
    selectedRoles.clear();
    rolesOptions.querySelectorAll("input[type='checkbox']").forEach((checkbox) => {
      checkbox.checked = false;
      checkbox.parentElement.hidden = false;
    });
    rolesSearch.value = "";
    renderSelectedRoles();
    showFeedback("success", "O usuario foi criado com sucesso.");
  } catch (error) {
    showFeedback("error", "Erro de conexao com o servidor.");
  }
});

renderSelectedRoles();

void (async () => {
  try {
    await loadAvailableRoles();
  } catch (error) {
    renderRoleLoadError("Nao foi possivel carregar as roles cadastradas.");
  }
})();
