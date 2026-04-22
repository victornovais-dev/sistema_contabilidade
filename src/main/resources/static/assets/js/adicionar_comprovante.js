const root = document.documentElement;
let toggle = document.querySelector(".theme-toggle");
const fileInput = document.querySelector(".file-input");
const fileHint = document.querySelector(".file-hint");
const fileStatus = document.querySelector(".file-status");
const dropOverlay = document.querySelector(".drop-overlay");
const moneyInput = document.querySelector(".money-input");
const dateInput = document.querySelector(".date-input");
const roleField = document.getElementById("role-field");
const roleSelect = document.querySelector("select[name=\"role\"]");
const typeSelect = document.querySelector("select[name=\"entry_type\"]");
const descricaoSelect = document.querySelector("select[name=\"descricao\"]");
const tipoDocumentoSelect = document.querySelector("select[name=\"tipo_documento\"]");
const numeroDocumentoInput = document.querySelector("input[name=\"numero_documento\"]");
const customSelects = document.querySelectorAll("[data-custom-select]");
const razaoSocialNomeInput = document.querySelector("input[name=\"razao_social_nome\"]");
const cnpjCpfInput = document.querySelector("input[name=\"cnpj_cpf\"]");
const observacaoInput = document.querySelector("textarea[name=\"observacao\"]");
const form = document.querySelector(".form");
const receiptSelected = document.getElementById("receipt-selected");
const confirmOverlay = document.querySelector(".confirm-overlay");
const confirmClose = document.querySelector(".confirm-close");
const confirmTitle = document.querySelector(".confirm-card h2");
const confirmText = document.querySelector(".confirm-card p");
const confirmIcon = document.querySelector(".confirm-icon");
let csrfToken = null;
let lastSubmitOk = false;
let monthMenuCloseHandlerBound = false;
let yearMenuCloseHandlerBound = false;
let retainedReceiptFiles = [];
let settingReceiptFilesProgrammatically = false;
const descricaoOptionsCache = new Map();
const DEFAULT_TIPO_DOCUMENTO_OPTIONS_BY_TIPO = {
  RECEITA: ["Pix", "Transfer\u00eancia", "Cheque", "Dinheiro"],
  DESPESA: ["Nota fiscal", "Fatura", "Boleto", "Outros"],
};
const MAX_VALOR_CENTS = 1000000000;
const MAX_RAZAO_SOCIAL_LENGTH = 150;
const MAX_NUMERO_DOCUMENTO_LENGTH = 50;
const TECHNICAL_ROLES = new Set(["ADMIN", "CONTABIL", "MANAGER", "SUPPORT", "CANDIDATO"]);
const REQUIRED_ATTACHMENT_DESCRIPTIONS = new Set([
  "CONTA DC",
  "CONTA FEFC",
  "CONTA FP",
]);
const tipoDocumentoOptionsCache = new Map();
let descricaoRequestSequence = 0;
let tipoDocumentoRequestSequence = 0;

const parseDate = (value) => {
  const digits = (value || "").replace(/\D/g, "");
  if (digits.length !== 8) return null;
  const day = Number(digits.slice(0, 2));
  const month = Number(digits.slice(2, 4));
  const year = Number(digits.slice(4, 8));
  if (!day || !month || !year) return null;
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }
  date.setHours(0, 0, 0, 0);
  return date;
};

const todayMidnight = () => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return today;
};

const toIsoLocalDate = (value) => {
  const parsed = parseDate(value);
  if (!parsed) return null;
  const day = String(parsed.getDate()).padStart(2, "0");
  const month = String(parsed.getMonth() + 1).padStart(2, "0");
  const year = String(parsed.getFullYear());
  return `${year}-${month}-${day}`;
};

const nowAsLocalDateTime = () => {
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  const dd = String(now.getDate()).padStart(2, "0");
  const hh = String(now.getHours()).padStart(2, "0");
  const mi = String(now.getMinutes()).padStart(2, "0");
  const ss = String(now.getSeconds()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}T${hh}:${mi}:${ss}`;
};

const moneyToDecimal = (value) => {
  const digits = (value || "").replace(/\D/g, "");
  const cents = Number(digits || 0);
  return (cents / 100).toFixed(2);
};

const sanitizeRazaoSocial = (value) => {
  if (!value) return "";
  const cleaned = value.replace(/[^\p{L}\p{N} .&'/-]/gu, "");
  return cleaned.replace(/\s{2,}/g, " ").toUpperCase().slice(0, MAX_RAZAO_SOCIAL_LENGTH);
};

const sanitizeNumeroDocumento = (value) =>
  String(value || "")
    .replace(/\D/g, "")
    .slice(0, MAX_NUMERO_DOCUMENTO_LENGTH);

const normalizeDescricao = (value) => String(value || "").trim().toUpperCase();

const normalizeTipoLancamento = (value) => String(value || "").trim().toUpperCase();

const requiresAttachmentBySelection = (tipo, descricao) =>
  normalizeTipoLancamento(tipo) === "RECEITA" &&
  REQUIRED_ATTACHMENT_DESCRIPTIONS.has(normalizeDescricao(descricao));

const formatCpfCnpj = (value) => {
  const digits = (value || "").replace(/\D/g, "").slice(0, 14);
  if (digits.length <= 11) {
    const cpf = digits.slice(0, 11);
    let out = cpf.slice(0, 3);
    if (cpf.length > 3) out += "." + cpf.slice(3, 6);
    if (cpf.length > 6) out += "." + cpf.slice(6, 9);
    if (cpf.length > 9) out += "-" + cpf.slice(9, 11);
    return out;
  }
  const cnpj = digits.slice(0, 14);
  let out = cnpj.slice(0, 2);
  if (cnpj.length > 2) out += "." + cnpj.slice(2, 5);
  if (cnpj.length > 5) out += "." + cnpj.slice(5, 8);
  if (cnpj.length > 8) out += "/" + cnpj.slice(8, 12);
  if (cnpj.length > 12) out += "-" + cnpj.slice(12, 14);
  return out;
};

const fileToBase64 = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || "");
      const comma = result.indexOf(",");
      resolve(comma >= 0 ? result.slice(comma + 1) : result);
    };
    reader.onerror = () => reject(new Error("Falha ao ler arquivo"));
    reader.readAsDataURL(file);
  });

const filesToBase64 = async (files) => {
  if (!files || files.length === 0) return [];
  const array = Array.from(files);
  const encoded = [];
  for (const file of array) {
    encoded.push(await fileToBase64(file));
  }
  return encoded;
};

const filesToNames = (files) =>
  files && files.length > 0 ? Array.from(files).map((file) => file.name) : [];

const isPdfFile = (file) => {
  if (!file) return false;
  const name = String(file.name || "").toLowerCase();
  return file.type === "application/pdf" || name.endsWith(".pdf");
};

const ensureCsrfToken = async (forceRefresh = false) => {
  if (!forceRefresh && csrfToken) return csrfToken;
  const accessToken = localStorage.getItem("sc_access_token");
  const response = await fetch("/api/v1/auth/csrf", {
    method: "GET",
    credentials: "same-origin",
    cache: "no-store",
    headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
  });
  if (!response.ok) {
    throw new Error("Falha ao obter token CSRF");
  }
  const data = await response.json();
  csrfToken = data.token || null;
  if (!csrfToken) {
    throw new Error("Token CSRF ausente");
  }
  return csrfToken;
};

const extractErrorMessage = async (response, fallbackMessage) => {
  try {
    const payload = await response.json();
    if (payload && typeof payload === "object") {
      return payload.message || payload.detail || payload.error || payload.title || fallbackMessage;
    }
  } catch (error) {
    // Keep fallback message when payload is not JSON.
  }
  return fallbackMessage;
};

const showConfirm = (ok, message) => {
  lastSubmitOk = ok;
  if (!confirmOverlay) return;
  if (confirmTitle) confirmTitle.textContent = ok ? "Comprovante enviado" : "Falha ao enviar";
  if (confirmText) confirmText.textContent = message;
  if (confirmIcon) {
    confirmIcon.textContent = ok ? "✓" : "✕";
    confirmIcon.style.background = ok ? "#2eb05e" : "#d84b34";
    confirmIcon.style.boxShadow = ok
      ? "0 10px 20px rgba(46, 176, 94, 0.35)"
      : "0 10px 20px rgba(216, 75, 52, 0.35)";
  }
  confirmOverlay.classList.add("is-visible");
  confirmOverlay.setAttribute("aria-hidden", "false");
};

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

const bindCustomSelect = (selectElement) => {
  if (!(selectElement instanceof HTMLSelectElement)) return null;
  const wrapper = selectElement.closest("[data-custom-select]");
  if (!wrapper) return null;
  const trigger = wrapper.querySelector(".custom-select-trigger");
  const menu = wrapper.querySelector(".custom-select-menu");
  if (!(trigger instanceof HTMLButtonElement) || !(menu instanceof HTMLElement)) return null;

  const close = () => {
    menu.hidden = true;
    trigger.setAttribute("aria-expanded", "false");
  };

  const open = () => {
    if (trigger.disabled) return;
    menu.hidden = false;
    trigger.setAttribute("aria-expanded", "true");
  };

  const syncFromSelect = () => {
    const selected = selectElement.selectedOptions && selectElement.selectedOptions.length > 0
      ? selectElement.selectedOptions[0]
      : null;
    const label = selected ? String(selected.textContent || "").trim() : "";
    trigger.textContent = label || "Selecione";

    wrapper.querySelectorAll(".custom-select-option").forEach((node) => {
      if (!(node instanceof HTMLElement)) return;
      node.classList.toggle("is-active", (node.dataset.value || "") === selectElement.value);
    });
  };

  trigger.addEventListener("click", (event) => {
    event.preventDefault();
    trigger.classList.remove("is-invalid");
    if (menu.hidden) open();
    else close();
  });

  menu.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const option = target.closest(".custom-select-option");
    if (!(option instanceof HTMLElement)) return;
    const value = option.dataset.value || "";
    selectElement.value = value;
    selectElement.dispatchEvent(new Event("change", { bubbles: true }));
    selectElement.setCustomValidity("");
    trigger.classList.remove("is-invalid");
    syncFromSelect();
    close();
  });

  document.addEventListener(
    "mousedown",
    (event) => {
      const target = event.target;
      if (!(target instanceof Node)) return;
      if (trigger.contains(target)) return;
      if (menu.contains(target)) return;
      close();
    },
    { capture: true },
  );

  selectElement.addEventListener("change", syncFromSelect);
  syncFromSelect();

  return { trigger, open, close, syncFromSelect };
};

const customRole = roleSelect ? bindCustomSelect(roleSelect) : null;
const customType = typeSelect ? bindCustomSelect(typeSelect) : null;
const customDescricao = descricaoSelect ? bindCustomSelect(descricaoSelect) : null;
const customTipoDocumento = tipoDocumentoSelect ? bindCustomSelect(tipoDocumentoSelect) : null;

const resetNativeSelect = (selectElement) => {
  if (!(selectElement instanceof HTMLSelectElement)) return;
  selectElement.value = "";
  selectElement.setCustomValidity("");
};

const renderRoleOptions = (roles) => {
  if (!roleSelect) return;
  const wrapper = roleSelect.closest("[data-custom-select]");
  if (!wrapper) return;
  const menu = wrapper.querySelector(".custom-select-menu");
  const trigger = wrapper.querySelector(".custom-select-trigger");
  if (!(menu instanceof HTMLElement) || !(trigger instanceof HTMLButtonElement)) return;

  const orderedRoles = Array.isArray(roles)
    ? [
        ...new Set(
          roles
            .map((role) => String(role || "").trim())
            .filter((role) => role && !TECHNICAL_ROLES.has(role.toUpperCase())),
        ),
      ].sort((a, b) => a.localeCompare(b, "pt-BR"))
    : [];

  roleSelect.querySelectorAll("option:not([value=\"\"])").forEach((option) => option.remove());
  menu.innerHTML = "";

  orderedRoles.forEach((role) => {
    const option = document.createElement("option");
    option.value = role;
    option.textContent = role;
    roleSelect.appendChild(option);

    const button = document.createElement("button");
    button.className = "custom-select-option";
    button.type = "button";
    button.setAttribute("role", "option");
    button.dataset.value = role;
    button.textContent = role;
    menu.appendChild(button);
  });

  if (orderedRoles.length <= 1) {
    const singleRole = orderedRoles[0] || "";
    roleSelect.value = singleRole;
    roleSelect.disabled = true;
    roleSelect.required = false;
    trigger.disabled = true;
    trigger.textContent = singleRole || "Role única";
    if (roleField) {
      roleField.hidden = true;
    }
  } else {
    roleSelect.value = "";
    roleSelect.disabled = false;
    roleSelect.required = true;
    trigger.disabled = false;
    trigger.textContent = "Selecione";
    if (roleField) {
      roleField.hidden = false;
    }
  }

  customRole?.syncFromSelect();
};

const loadRoleOptions = async () => {
  const accessToken = localStorage.getItem("sc_access_token");
  if (!accessToken || !roleSelect) {
    return;
  }

  const response = await fetch("/api/v1/itens/roles", {
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
    renderRoleOptions([]);
    return;
  }

  const roles = await response.json();
  renderRoleOptions(roles);
};

const renderDescricaoOptions = (descriptions) => {
  if (!descricaoSelect) return;
  const wrapper = descricaoSelect.closest("[data-custom-select]");
  if (!wrapper) return;
  const menu = wrapper.querySelector(".custom-select-menu");
  if (!(menu instanceof HTMLElement)) return;

  const ordered = (descriptions || [])
    .filter((value) => value != null && String(value).trim().length > 0)
    .map((value) => String(value));

  descricaoSelect.querySelectorAll("option:not([value=\"\"])").forEach((opt) => opt.remove());
  ordered.forEach((label) => {
    const option = document.createElement("option");
    option.value = label;
    option.textContent = label;
    descricaoSelect.appendChild(option);
  });

  menu.innerHTML = "";
  ordered.forEach((label) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "custom-select-option";
    button.setAttribute("role", "option");
    button.dataset.value = label;
    button.textContent = label;
    menu.appendChild(button);
  });

  menu.classList.toggle("is-scroll", ordered.length > 10);

  customDescricao?.syncFromSelect?.();
  customDescricao?.close?.();
};

const renderTipoDocumentoOptions = (documentTypes) => {
  if (!tipoDocumentoSelect) return;
  const wrapper = tipoDocumentoSelect.closest("[data-custom-select]");
  if (!wrapper) return;
  const menu = wrapper.querySelector(".custom-select-menu");
  if (!(menu instanceof HTMLElement)) return;

  const ordered = (documentTypes || [])
    .filter((value) => value != null && String(value).trim().length > 0)
    .map((value) => String(value));

  tipoDocumentoSelect.querySelectorAll("option:not([value=\"\"])").forEach((opt) => opt.remove());
  ordered.forEach((label) => {
    const option = document.createElement("option");
    option.value = label;
    option.textContent = label;
    tipoDocumentoSelect.appendChild(option);
  });

  menu.innerHTML = "";
  ordered.forEach((label) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "custom-select-option";
    button.setAttribute("role", "option");
    button.dataset.value = label;
    button.textContent = label;
    menu.appendChild(button);
  });

  customTipoDocumento?.syncFromSelect?.();
  customTipoDocumento?.close?.();
};

const setDescricaoEnabled = (enabled) => {
  if (!descricaoSelect) return;
  const wrapper = descricaoSelect.closest("[data-custom-select]");
  if (!wrapper) return;
  const trigger = wrapper.querySelector(".custom-select-trigger");

  if (trigger instanceof HTMLButtonElement) {
    trigger.disabled = !enabled;
    trigger.classList.remove("is-invalid");
    if (!enabled) {
      trigger.textContent = "Selecione";
    }
  }

  descricaoSelect.disabled = !enabled;
  if (!enabled) {
    resetNativeSelect(descricaoSelect);
    customDescricao?.syncFromSelect?.();
    customDescricao?.close?.();
  }
};

const setDescricaoLoadingState = (loading) => {
  if (!descricaoSelect) return;
  const wrapper = descricaoSelect.closest("[data-custom-select]");
  if (!wrapper) return;
  const trigger = wrapper.querySelector(".custom-select-trigger");
  if (!(trigger instanceof HTMLButtonElement)) return;
  if (loading) {
    trigger.disabled = true;
    trigger.textContent = "Carregando...";
    return;
  }
  trigger.textContent = "Selecione";
};

const setTipoDocumentoEnabled = (enabled) => {
  if (!tipoDocumentoSelect) return;
  const wrapper = tipoDocumentoSelect.closest("[data-custom-select]");
  if (!wrapper) return;
  const trigger = wrapper.querySelector(".custom-select-trigger");

  if (trigger instanceof HTMLButtonElement) {
    trigger.disabled = !enabled;
    trigger.classList.remove("is-invalid");
    if (!enabled) {
      trigger.textContent = "Selecione";
    }
  }

  tipoDocumentoSelect.disabled = !enabled;
  if (!enabled) {
    resetNativeSelect(tipoDocumentoSelect);
    customTipoDocumento?.syncFromSelect?.();
    customTipoDocumento?.close?.();
  }
};

const setTipoDocumentoLoadingState = (loading) => {
  if (!tipoDocumentoSelect) return;
  const wrapper = tipoDocumentoSelect.closest("[data-custom-select]");
  if (!wrapper) return;
  const trigger = wrapper.querySelector(".custom-select-trigger");
  if (!(trigger instanceof HTMLButtonElement)) return;
  if (loading) {
    trigger.disabled = true;
    trigger.textContent = "Carregando...";
    return;
  }
  trigger.textContent = "Selecione";
};

const preloadDescricaoOptions = async () => {
  try {
    await Promise.all([loadDescricaoOptions("RECEITA"), loadDescricaoOptions("DESPESA")]);
  } catch (error) {
    // Keep lazy loading on demand when preload fails.
  }
};

const loadDescricaoOptions = async (tipo) => {
  if (descricaoOptionsCache.has(tipo)) {
    return descricaoOptionsCache.get(tipo);
  }

  const accessToken = localStorage.getItem("sc_access_token");
  if (!accessToken) {
    window.location.href = "/login";
    return [];
  }

  const response = await fetch(`/api/v1/itens/descricoes?tipo=${encodeURIComponent(tipo)}`, {
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
  if (!response.ok) {
    throw new Error(`Falha ao carregar descricoes para ${tipo}`);
  }

  const descriptions = await response.json();
  const normalized = Array.isArray(descriptions) ? descriptions : [];
  descricaoOptionsCache.set(tipo, normalized);
  return normalized;
};

const loadTipoDocumentoOptions = async (tipo) => {
  if (tipoDocumentoOptionsCache.has(tipo)) {
    return tipoDocumentoOptionsCache.get(tipo);
  }

  const accessToken = localStorage.getItem("sc_access_token");
  if (!accessToken) {
    window.location.href = "/login";
    return [];
  }

  const response = await fetch(`/api/v1/itens/tipos-documento?tipo=${encodeURIComponent(tipo)}`, {
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
  if (!response.ok) {
    throw new Error("Falha ao carregar tipos de documento");
  }

  const documentTypes = await response.json();
  const normalized = Array.isArray(documentTypes) ? documentTypes : [];
  tipoDocumentoOptionsCache.set(tipo, normalized);
  return normalized;
};

const initTipoDocumentoOptions = async () => {
  renderTipoDocumentoOptions([]);
  setTipoDocumentoEnabled(false);
};

const updateTipoDocumentoByTipo = async (tipoValue) => {
  const requestId = ++tipoDocumentoRequestSequence;
  const tipo = String(tipoValue || "").trim().toUpperCase();

  if (tipo !== "RECEITA" && tipo !== "DESPESA") {
    renderTipoDocumentoOptions([]);
    setTipoDocumentoEnabled(false);
    return;
  }

  renderTipoDocumentoOptions([]);
  setTipoDocumentoEnabled(false);
  setTipoDocumentoLoadingState(true);

  try {
    const documentTypes = await loadTipoDocumentoOptions(tipo);
    if (requestId !== tipoDocumentoRequestSequence) {
      return;
    }
    renderTipoDocumentoOptions(documentTypes);
    setTipoDocumentoEnabled(documentTypes.length > 0);
    resetNativeSelect(tipoDocumentoSelect);
    customTipoDocumento?.syncFromSelect?.();
  } catch (error) {
    if (requestId !== tipoDocumentoRequestSequence) {
      return;
    }
    const fallback = DEFAULT_TIPO_DOCUMENTO_OPTIONS_BY_TIPO[tipo] || [];
    renderTipoDocumentoOptions(fallback);
    setTipoDocumentoEnabled(fallback.length > 0);
    resetNativeSelect(tipoDocumentoSelect);
    customTipoDocumento?.syncFromSelect?.();
  } finally {
    if (requestId === tipoDocumentoRequestSequence) {
      setTipoDocumentoLoadingState(false);
    }
  }
};

const updateDescricaoByTipo = async (tipoValue) => {
  const requestId = ++descricaoRequestSequence;
  const tipo = String(tipoValue || "").trim().toUpperCase();

  if (tipo !== "RECEITA" && tipo !== "DESPESA") {
    renderDescricaoOptions([]);
    setDescricaoEnabled(false);
    return;
  }

  renderDescricaoOptions([]);
  setDescricaoEnabled(false);
  setDescricaoLoadingState(true);

  try {
    const descriptions = await loadDescricaoOptions(tipo);
    if (requestId !== descricaoRequestSequence) {
      return;
    }
    renderDescricaoOptions(descriptions);
    setDescricaoEnabled(descriptions.length > 0);
    resetNativeSelect(descricaoSelect);
    customDescricao?.syncFromSelect?.();
  } catch (error) {
    if (requestId !== descricaoRequestSequence) {
      return;
    }
    renderDescricaoOptions([]);
    setDescricaoEnabled(false);
    showConfirm(false, "Nao foi possivel carregar as descricoes. Recarregue a pagina.");
  } finally {
    if (requestId === descricaoRequestSequence) {
      setDescricaoLoadingState(false);
    }
  }
};

void initTipoDocumentoOptions();

if (typeSelect) {
  typeSelect.addEventListener("change", () => {
    if (fileInput) {
      fileInput.setCustomValidity("");
    }
    void updateDescricaoByTipo(typeSelect.value);
    void updateTipoDocumentoByTipo(typeSelect.value);
  });
  void preloadDescricaoOptions();
  void updateDescricaoByTipo(typeSelect.value);
  void updateTipoDocumentoByTipo(typeSelect.value);
}

if (descricaoSelect) {
  descricaoSelect.addEventListener("change", () => {
    if (fileInput) {
      fileInput.setCustomValidity("");
    }
  });
}

const updateReceiptGrid = (files) => {
  if (!receiptSelected) return;
  const list = Array.isArray(files) ? files : [];
  const pdfCount = list.filter(isPdfFile).length;
  receiptSelected.classList.toggle("is-grid", pdfCount > 1);
};

const updateReceiptHint = (files) => {
  if (!fileHint) return;
  const list = Array.isArray(files) ? files : [];
  if (list.length === 0) {
    fileHint.textContent = "Nenhum arquivo selecionado";
    if (fileStatus) fileStatus.classList.remove("is-ready");
    if (receiptSelected) {
      receiptSelected.classList.remove("is-grid");
      receiptSelected.innerHTML = "";
    }
    return;
  }
  fileHint.textContent = "";
  if (fileStatus) fileStatus.classList.add("is-ready");

  if (receiptSelected) {
    receiptSelected.innerHTML = "";
    const pdfFiles = list.filter(isPdfFile);
    updateReceiptGrid(pdfFiles);
    pdfFiles.forEach((file) => {
      const card = document.createElement("div");
      card.className = "receipt-file-card";

      const icon = document.createElement("div");
      icon.className = "receipt-file-icon";
      icon.textContent = "PDF";

      const name = document.createElement("div");
      name.className = "receipt-file-name";
      name.textContent = file.name || "Arquivo.pdf";

      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "receipt-file-remove";
      remove.textContent = "×";
      remove.setAttribute("aria-label", "Remover PDF selecionado");
      remove.addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();
        const key = receiptFileKey(file);
        const kept = retainedReceiptFiles.filter((entry) => receiptFileKey(entry) !== key);
        retainedReceiptFiles = kept;
        setReceiptFiles(kept);
      });

      card.appendChild(icon);
      card.appendChild(name);
      card.appendChild(remove);
      receiptSelected.appendChild(card);
    });
  }
};

const receiptFileKey = (file) => {
  if (!file) return "";
  return `${file.name}::${file.size}::${file.lastModified}`;
};

const mergeReceiptFiles = (existing, incoming) => {
  const merged = [];
  const seen = new Set();
  const pushUnique = (file) => {
    if (!(file instanceof File)) return;
    const key = receiptFileKey(file);
    if (!key || seen.has(key)) return;
    seen.add(key);
    merged.push(file);
  };

  (existing || []).forEach(pushUnique);
  (incoming || []).forEach(pushUnique);
  return merged;
};

const setReceiptFiles = (files) => {
  if (!fileInput) return;
  const dataTransfer = new DataTransfer();
  (files || []).forEach((file) => dataTransfer.items.add(file));
  settingReceiptFilesProgrammatically = true;
  fileInput.files = dataTransfer.files;
  fileInput.dispatchEvent(new Event("change", { bubbles: true }));
  settingReceiptFilesProgrammatically = false;
};

if (fileInput) {
  retainedReceiptFiles = fileInput.files ? Array.from(fileInput.files) : [];
  updateReceiptHint(retainedReceiptFiles);

  fileInput.addEventListener("change", () => {
    fileInput.setCustomValidity("");
    const picked = fileInput.files ? Array.from(fileInput.files) : [];

    // When we set `fileInput.files` ourselves (DataTransfer), just sync the UI/state.
    if (settingReceiptFilesProgrammatically) {
      retainedReceiptFiles = picked;
      updateReceiptHint(picked);
      return;
    }

    // Native file picker replaces FileList; merge new picks into retained list.
    const merged = mergeReceiptFiles(retainedReceiptFiles, picked);
    setReceiptFiles(merged);
  });
}

if (fileInput) {
  const setDroppedFiles = (files) => {
    if (!files || files.length === 0) return;
    const picked = Array.from(files);
    const merged = mergeReceiptFiles(retainedReceiptFiles, picked);
    setReceiptFiles(merged);
  };

  let dragDepth = 0;

  document.addEventListener("dragenter", (event) => {
    event.preventDefault();
    dragDepth += 1;
    if (dropOverlay) dropOverlay.classList.add("is-visible");
  });

  document.addEventListener("dragover", (event) => {
    event.preventDefault();
    if (dropOverlay) dropOverlay.classList.add("is-visible");
  });

  document.addEventListener("drop", (event) => {
    event.preventDefault();
    dragDepth = 0;
    if (dropOverlay) dropOverlay.classList.remove("is-visible");
    const files = event.dataTransfer && event.dataTransfer.files ? event.dataTransfer.files : null;
    setDroppedFiles(files);
  });

  document.addEventListener("dragleave", (event) => {
    dragDepth = Math.max(0, dragDepth - 1);
    if (dragDepth === 0 && dropOverlay) {
      dropOverlay.classList.remove("is-visible");
    }
  });

  document.addEventListener("dragend", () => {
    dragDepth = 0;
    if (dropOverlay) dropOverlay.classList.remove("is-visible");
  });
}

if (moneyInput) {
  const formatMoney = (value) => {
    const digits = value.replace(/\D/g, "");
    const cents = Math.min(Number(digits || 0), MAX_VALOR_CENTS);
    const number = cents / 100;
    return number.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  };

  const updateMoney = () => {
    moneyInput.value = formatMoney(moneyInput.value);
    moneyInput.setCustomValidity("");
  };

  moneyInput.addEventListener("input", updateMoney);
  moneyInput.addEventListener("blur", updateMoney);
  updateMoney();
}

if (dateInput) {
  const formatDate = (value) => {
    const digits = (value || "").replace(/\D/g, "").slice(0, 8);
    const parts = [];
    if (digits.length >= 1) parts.push(digits.slice(0, 2));
    if (digits.length >= 3) parts.push(digits.slice(2, 4));
    if (digits.length >= 5) parts.push(digits.slice(4, 8));
    return parts.join("/");
  };

  if (window.flatpickr) {
    const maxDate = todayMidnight();

    const ensureYearDropdown = (instance) => {
      const container = instance?.calendarContainer;
      if (!container) return;

      const currentMonth = container.querySelector(".flatpickr-current-month");
      const numWrapper = container.querySelector(".numInputWrapper");
      const yearInput = container.querySelector(".cur-year");
      if (!currentMonth || !yearInput) return;

      const minYear = 2000;
      const maxYear = maxDate.getFullYear();

      // Remove legacy injected native dropdown if present.
      const legacySelect = currentMonth.querySelector("select.year-dropdown");
      if (legacySelect instanceof HTMLElement) {
        legacySelect.remove();
      }

      let trigger = currentMonth.querySelector("button.sc-year-trigger");
      if (!(trigger instanceof HTMLButtonElement)) {
        trigger = document.createElement("button");
        trigger.type = "button";
        trigger.className = "sc-year-trigger";
        trigger.setAttribute("aria-haspopup", "listbox");
        trigger.setAttribute("aria-expanded", "false");
        currentMonth.appendChild(trigger);
      }

      let menu = container.querySelector("div.sc-year-menu");
      if (!(menu instanceof HTMLDivElement)) {
        menu = document.createElement("div");
        menu.className = "sc-year-menu";
        menu.setAttribute("role", "listbox");
        menu.hidden = true;
        container.appendChild(menu);
      }

      trigger.textContent = String(instance.currentYear);

      if (menu.childElementCount === 0) {
        for (let year = maxYear; year >= minYear; year -= 1) {
          const option = document.createElement("button");
          option.type = "button";
          option.className = "sc-year-option";
          option.setAttribute("role", "option");
          option.dataset.year = String(year);
          option.textContent = String(year);
          option.addEventListener("click", (event) => {
            event.preventDefault();
            event.stopPropagation();
            instance.changeYear(year);
            trigger.textContent = String(year);
            menu.hidden = true;
            trigger.setAttribute("aria-expanded", "false");
            ensureMonthDropdown(instance);
            ensureYearDropdown(instance);
          });
          menu.appendChild(option);
        }
      }

      const positionMenu = () => {
        const rect = trigger.getBoundingClientRect();
        const calendarRect = container.getBoundingClientRect();
        const top = rect.bottom - calendarRect.top + 8;
        const left = rect.left - calendarRect.left;
        menu.style.top = `${Math.max(44, top)}px`;
        menu.style.left = `${Math.max(12, left)}px`;
      };

      const updateSelection = () => {
        menu.querySelectorAll(".sc-year-option").forEach((node) => {
          if (!(node instanceof HTMLElement)) return;
          const isActive = node.dataset.year === String(instance.currentYear);
          node.classList.toggle("is-active", isActive);
          node.setAttribute("aria-selected", isActive ? "true" : "false");
        });
      };

      updateSelection();

      trigger.onclick = (event) => {
        event.preventDefault();
        event.stopPropagation();
        positionMenu();
        const willOpen = menu.hidden;
        menu.hidden = !willOpen;
        trigger.setAttribute("aria-expanded", String(willOpen));
        if (willOpen) updateSelection();
      };

      if (!yearMenuCloseHandlerBound) {
        yearMenuCloseHandlerBound = true;
        document.addEventListener(
          "mousedown",
          (event) => {
            const target = event.target;
            if (!(target instanceof Node)) return;
            const openMenu = document.querySelector("div.sc-year-menu");
            if (!(openMenu instanceof HTMLDivElement) || openMenu.hidden) return;
            const openTrigger = document.querySelector("button.sc-year-trigger");
            if (openTrigger && openTrigger.contains(target)) return;
            if (openMenu.contains(target)) return;
            openMenu.hidden = true;
            if (openTrigger instanceof HTMLButtonElement) {
              openTrigger.setAttribute("aria-expanded", "false");
            }
          },
          { capture: true },
        );
      }

      if (numWrapper) {
        numWrapper.style.display = "none";
      }
      yearInput.style.display = "none";
    };

    const ensureMonthDropdown = (instance) => {
      const container = instance?.calendarContainer;
      if (!container) return;

      const currentMonth = container.querySelector(".flatpickr-current-month");
      if (!currentMonth) return;

      // Hide Flatpickr's native month label/select to avoid duplicated month text.
      const nativeCurMonth = currentMonth.querySelector(".cur-month");
      if (nativeCurMonth instanceof HTMLElement) {
        nativeCurMonth.style.display = "none";
      }

      // Flatpickr may render a native <select> for month; native option colors are OS-controlled.
      const nativeMonthSelect = currentMonth.querySelector(".flatpickr-monthDropdown-months");
      if (nativeMonthSelect instanceof HTMLElement) {
        nativeMonthSelect.style.display = "none";
      }

      let trigger = container.querySelector("button.sc-month-trigger");
      if (!(trigger instanceof HTMLButtonElement)) {
        trigger = document.createElement("button");
        trigger.type = "button";
        trigger.className = "sc-month-trigger";
        trigger.setAttribute("aria-haspopup", "listbox");
        trigger.setAttribute("aria-expanded", "false");
        // Place before year dropdown (it gets injected into currentMonth).
        currentMonth.insertBefore(trigger, currentMonth.firstChild);
      }

      let menu = container.querySelector("div.sc-month-menu");
      if (!(menu instanceof HTMLDivElement)) {
        menu = document.createElement("div");
        menu.className = "sc-month-menu";
        menu.setAttribute("role", "listbox");
        menu.hidden = true;
        container.appendChild(menu);
      }

      const months = [
        "Janeiro",
        "Fevereiro",
        "Março",
        "Abril",
        "Maio",
        "Junho",
        "Julho",
        "Agosto",
        "Setembro",
        "Outubro",
        "Novembro",
        "Dezembro",
      ];

      trigger.textContent = months[instance.currentMonth] || "";

      if (menu.childElementCount === 0) {
        months.forEach((label, index) => {
          const option = document.createElement("button");
          option.type = "button";
          option.className = "sc-month-option";
          option.setAttribute("role", "option");
          option.dataset.monthIndex = String(index);
          option.textContent = label;
          option.addEventListener("click", (event) => {
            event.preventDefault();
            event.stopPropagation();
            if (option.disabled) {
              return;
            }
            instance.changeMonth(index - instance.currentMonth);
            trigger.textContent = label;
            menu.hidden = true;
            trigger.setAttribute("aria-expanded", "false");
          });
          menu.appendChild(option);
        });
      }

      const positionMenu = () => {
        const rect = trigger.getBoundingClientRect();
        const calendarRect = container.getBoundingClientRect();
        const top = rect.bottom - calendarRect.top + 8;
        const left = rect.left - calendarRect.left;
        menu.style.top = `${Math.max(44, top)}px`;
        menu.style.left = `${Math.max(12, left)}px`;
      };

      const updateSelection = () => {
        const maxYear = maxDate.getFullYear();
        const isCurrentYear = instance.currentYear === maxYear;
        const maxMonth = maxDate.getMonth();

        // Clamp month when switching to current year (avoid selecting future months).
        if (isCurrentYear && instance.currentMonth > maxMonth) {
          instance.changeMonth(maxMonth - instance.currentMonth);
          trigger.textContent = months[maxMonth] || trigger.textContent;
        }

        menu.querySelectorAll(".sc-month-option").forEach((node) => {
          if (!(node instanceof HTMLElement)) return;
          const isActive = node.dataset.monthIndex === String(instance.currentMonth);
          node.classList.toggle("is-active", isActive);
          node.setAttribute("aria-selected", isActive ? "true" : "false");

          if (node instanceof HTMLButtonElement) {
            const monthIndex = Number(node.dataset.monthIndex);
            const isFutureMonth = isCurrentYear && monthIndex > maxMonth;
            node.hidden = isFutureMonth;
            node.disabled = false;
            node.classList.remove("is-disabled");
            node.removeAttribute("aria-disabled");
          }
        });
      };

      updateSelection();

      trigger.onclick = (event) => {
        event.preventDefault();
        event.stopPropagation();
        positionMenu();
        const willOpen = menu.hidden;
        menu.hidden = !willOpen;
        trigger.setAttribute("aria-expanded", String(willOpen));
        if (willOpen) {
          updateSelection();
        }
      };

      if (!monthMenuCloseHandlerBound) {
        monthMenuCloseHandlerBound = true;
        document.addEventListener(
          "mousedown",
          (event) => {
            const target = event.target;
            if (!(target instanceof Node)) return;
            const openMenu = document.querySelector("div.sc-month-menu");
            if (!(openMenu instanceof HTMLDivElement) || openMenu.hidden) return;
            const openTrigger = document.querySelector("button.sc-month-trigger");
            if (openTrigger && openTrigger.contains(target)) return;
            if (openMenu.contains(target)) return;
            openMenu.hidden = true;
            if (openTrigger instanceof HTMLButtonElement) {
              openTrigger.setAttribute("aria-expanded", "false");
            }
          },
          { capture: true },
        );
      }
    };

    window.flatpickr(dateInput, {
      dateFormat: "d/m/Y",
      allowInput: true,
      clickOpens: true,
      maxDate,
      monthSelectorType: "static",
      onReady: (_, __, instance) => {
        ensureMonthDropdown(instance);
        ensureYearDropdown(instance);
        dateInput.setCustomValidity("");
      },
      onMonthChange: (_, __, instance) => {
        ensureMonthDropdown(instance);
        ensureYearDropdown(instance);
      },
      onYearChange: (_, __, instance) => {
        ensureMonthDropdown(instance);
        ensureYearDropdown(instance);
      },
      onOpen: (_, __, instance) => {
        ensureMonthDropdown(instance);
        ensureYearDropdown(instance);
      },
      onChange: () => {
        dateInput.setCustomValidity("");
      },
    });
  }
  dateInput.addEventListener("input", () => {
    dateInput.value = formatDate(dateInput.value);
    dateInput.setCustomValidity("");
  });
  dateInput.addEventListener("blur", () => {
    dateInput.value = formatDate(dateInput.value);
  });
}

if (razaoSocialNomeInput) {
  const applyRazaoMask = () => {
    razaoSocialNomeInput.value = sanitizeRazaoSocial(razaoSocialNomeInput.value);
    razaoSocialNomeInput.setCustomValidity("");
  };
  razaoSocialNomeInput.addEventListener("input", applyRazaoMask);
  razaoSocialNomeInput.addEventListener("blur", applyRazaoMask);
}

if (numeroDocumentoInput) {
  const applyNumeroDocumentoMask = () => {
    numeroDocumentoInput.value = sanitizeNumeroDocumento(numeroDocumentoInput.value);
    numeroDocumentoInput.setCustomValidity("");
  };
  numeroDocumentoInput.addEventListener("input", applyNumeroDocumentoMask);
  numeroDocumentoInput.addEventListener("blur", applyNumeroDocumentoMask);
}

if (cnpjCpfInput) {
  const applyCpfCnpjMask = () => {
    cnpjCpfInput.value = formatCpfCnpj(cnpjCpfInput.value);
  };
  cnpjCpfInput.addEventListener("input", applyCpfCnpjMask);
  cnpjCpfInput.addEventListener("blur", applyCpfCnpjMask);
}

if (form) {
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    let hasError = false;

    if (moneyInput) {
      moneyInput.setCustomValidity("");
      const normalized = moneyInput.value || "";
      if (!normalized.trim()) {
        moneyInput.setCustomValidity("Informe um valor maior que zero.");
        hasError = true;
      }
      const digits = moneyInput.value.replace(/\D/g, "");
      const number = Number(digits || 0) / 100;
      if (number <= 0) {
        moneyInput.setCustomValidity("O valor deve ser maior que zero.");
        hasError = true;
      } else if (number > 10000000) {
        moneyInput.setCustomValidity("O valor nao pode ultrapassar R$ 10.000.000,00.");
        hasError = true;
      }
    }

    if (dateInput) {
      dateInput.setCustomValidity("");
      const date = parseDate(dateInput.value);

      if (date && date > todayMidnight()) {
        dateInput.setCustomValidity("A data não pode ser maior que a data atual.");
        hasError = true;
      }
    }

    if (fileInput) {
      fileInput.setCustomValidity("");
      const files = fileInput.files ? Array.from(fileInput.files) : [];
      if (files.length > 0) {
        const invalid = files.find(
          (file) =>
            file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf"),
        );
        if (invalid) {
          fileInput.setCustomValidity("Envie somente arquivos PDF.");
          hasError = true;
        }
      }
      if (requiresAttachmentBySelection(typeSelect?.value, descricaoSelect?.value) && files.length === 0) {
        fileInput.setCustomValidity(
          "Conta DC, Conta FEFC e Conta FP exigem ao menos um anexo.",
        );
        hasError = true;
      }
    }

    if (typeSelect) {
      typeSelect.setCustomValidity("");
      if (!typeSelect.value) {
        typeSelect.setCustomValidity("Selecione o tipo de lançamento.");
        if (customType?.trigger) {
          customType.trigger.classList.add("is-invalid");
          customType.open();
          customType.trigger.focus();
        }
        hasError = true;
      }
    }

    if (descricaoSelect && !descricaoSelect.disabled) {
      descricaoSelect.setCustomValidity("");
      if (!descricaoSelect.value) {
        descricaoSelect.setCustomValidity("Selecione a descrição.");
        if (customDescricao?.trigger) {
          customDescricao.trigger.classList.add("is-invalid");
          customDescricao.open();
          customDescricao.trigger.focus();
        }
        hasError = true;
      }
    }

    if (razaoSocialNomeInput) {
      razaoSocialNomeInput.setCustomValidity("");
      if (razaoSocialNomeInput.value.length > MAX_RAZAO_SOCIAL_LENGTH) {
        razaoSocialNomeInput.setCustomValidity(
          "Razao social ou nome deve ter no maximo 150 caracteres.",
        );
        hasError = true;
      }
    }

    if (numeroDocumentoInput) {
      numeroDocumentoInput.setCustomValidity("");
      const numeroDocumento = String(numeroDocumentoInput.value || "");
      if (numeroDocumento.length > MAX_NUMERO_DOCUMENTO_LENGTH) {
        numeroDocumentoInput.setCustomValidity(
          "Numero do documento deve ter no maximo 50 caracteres.",
        );
        hasError = true;
      } else if (numeroDocumento && !/^\d+$/.test(numeroDocumento)) {
        numeroDocumentoInput.setCustomValidity(
          "Numero do documento deve conter apenas numeros.",
        );
        hasError = true;
      }
    }

    if (tipoDocumentoSelect && !tipoDocumentoSelect.disabled) {
      tipoDocumentoSelect.setCustomValidity("");
      if (!tipoDocumentoSelect.value) {
        tipoDocumentoSelect.setCustomValidity("Selecione o tipo de documento.");
        if (customTipoDocumento?.trigger) {
          customTipoDocumento.trigger.classList.add("is-invalid");
          customTipoDocumento.open();
          customTipoDocumento.trigger.focus();
        }
        hasError = true;
      }
    }

    if (hasError) {
      if (moneyInput) moneyInput.reportValidity();
      if (dateInput) dateInput.reportValidity();
      if (fileInput) fileInput.reportValidity();
      if (razaoSocialNomeInput) razaoSocialNomeInput.reportValidity();
      if (numeroDocumentoInput) numeroDocumentoInput.reportValidity();
      if (typeSelect) typeSelect.reportValidity();
      if (descricaoSelect && !descricaoSelect.disabled) descricaoSelect.reportValidity();
      if (tipoDocumentoSelect && !tipoDocumentoSelect.disabled) tipoDocumentoSelect.reportValidity();
      return;
    }

    const files = fileInput.files ? Array.from(fileInput.files) : [];
    const dataIso = toIsoLocalDate(dateInput.value);
    const tipoSelecionado = String(typeSelect?.value || "").trim().toUpperCase();
    if (tipoSelecionado !== "RECEITA" && tipoSelecionado !== "DESPESA") {
      if (typeSelect) {
        typeSelect.setCustomValidity("Selecione um tipo valido.");
        typeSelect.reportValidity();
      }
      return;
    }
    const roleSelecionada = String(roleSelect?.value || "").trim().toUpperCase();
    if (!roleSelect?.disabled && !roleSelecionada) {
      if (roleSelect) {
        roleSelect.setCustomValidity("Selecione o candidato.");
        roleSelect.reportValidity();
      }
      if (customRole) {
        customRole.open();
        customRole.trigger.focus();
      }
      return;
    }
    const tipo = tipoSelecionado;
    const accessToken = localStorage.getItem("sc_access_token");

    if (!accessToken) {
      window.location.href = "/login";
      return;
    }

    try {
      const arquivosPdf = await filesToBase64(files);
      const nomesArquivos = filesToNames(files);

      const payload = {
        valor: moneyToDecimal(moneyInput.value),
        data: dataIso,
        horarioCriacao: nowAsLocalDateTime(),
        arquivosPdf,
        nomesArquivos,
        tipo,
        role: roleSelecionada || null,
        descricao: descricaoSelect?.value || null,
        tipoDocumento: tipoDocumentoSelect?.value || null,
        numeroDocumento: numeroDocumentoInput?.value || null,
        razaoSocialNome: razaoSocialNomeInput?.value || null,
        cnpjCpf: cnpjCpfInput?.value || null,
        observacao: observacaoInput?.value || null,
      };

      const postWithToken = async (token) =>
        fetch("/api/v1/itens", {
          method: "POST",
          credentials: "same-origin",
          redirect: "manual",
          headers: {
            "Content-Type": "application/json",
            "X-CSRF-TOKEN": token,
            Authorization: `Bearer ${accessToken}`,
          },
          body: JSON.stringify(payload),
        });

      let token = await ensureCsrfToken();
      let response = await postWithToken(token);

      const isRedirect =
        response.type === "opaqueredirect" ||
        response.redirected ||
        (typeof response.status === "number" && response.status >= 300 && response.status < 400);
      if (isRedirect) {
        window.location.href = "/login";
        throw new Error("Sessão expirada. Faça login novamente.");
      }

      if (response.status === 401) {
        window.location.href = "/login";
        throw new Error("Sessão expirada. Faça login novamente.");
      }

      if (response.status === 403) {
        token = await ensureCsrfToken(true);
        response = await postWithToken(token);
      }

      const isRedirectAfterRetry =
        response.type === "opaqueredirect" ||
        response.redirected ||
        (typeof response.status === "number" && response.status >= 300 && response.status < 400);
      if (isRedirectAfterRetry) {
        window.location.href = "/login";
        throw new Error("Sessão expirada. Faça login novamente.");
      }

      if (!response.ok) {
        const message = await extractErrorMessage(
          response,
          `Erro ${response.status} ao enviar comprovante.`,
        );
        showConfirm(false, message);
        return;
      }

      showConfirm(true, "Seu comprovante foi salvo com sucesso.");
      csrfToken = null;
    } catch (error) {
      showConfirm(false, "Erro ao enviar comprovante. Tente novamente.");
    }
  });
}

if (confirmOverlay && confirmClose) {
  confirmClose.addEventListener("click", () => {
    confirmOverlay.classList.remove("is-visible");
    confirmOverlay.setAttribute("aria-hidden", "true");
    if (form && lastSubmitOk) {
      form.reset();
    }
    if (fileInput && lastSubmitOk) {
      retainedReceiptFiles = [];
      fileInput.value = "";
      updateReceiptHint([]);
    }
    if (moneyInput && lastSubmitOk) {
      moneyInput.setCustomValidity("");
      moneyInput.value = "R$ 0,00";
    }
    if (dateInput && lastSubmitOk) {
      dateInput.setCustomValidity("");
      dateInput.value = "";
    }
    if (typeSelect && lastSubmitOk) {
      typeSelect.setCustomValidity("");
      typeSelect.selectedIndex = 0;
      if (customType?.trigger) {
        customType.trigger.classList.remove("is-invalid");
      }
      customType?.close?.();
      customType?.syncFromSelect?.();
      typeSelect.dispatchEvent(new Event("change", { bubbles: true }));
    }
    if (descricaoSelect && lastSubmitOk) {
      descricaoSelect.setCustomValidity("");
      descricaoSelect.selectedIndex = 0;
      if (customDescricao?.trigger) {
        customDescricao.trigger.classList.remove("is-invalid");
      }
      customDescricao?.close?.();
      customDescricao?.syncFromSelect?.();
    }
    if (tipoDocumentoSelect && lastSubmitOk) {
      tipoDocumentoSelect.setCustomValidity("");
      tipoDocumentoSelect.selectedIndex = 0;
      if (customTipoDocumento?.trigger) {
        customTipoDocumento.trigger.classList.remove("is-invalid");
      }
      customTipoDocumento?.close?.();
      customTipoDocumento?.syncFromSelect?.();
    }
    if (razaoSocialNomeInput && lastSubmitOk) {
      razaoSocialNomeInput.setCustomValidity("");
      razaoSocialNomeInput.value = "";
    }
    if (numeroDocumentoInput && lastSubmitOk) {
      numeroDocumentoInput.setCustomValidity("");
      numeroDocumentoInput.value = "";
    }
    if (cnpjCpfInput && lastSubmitOk) {
      cnpjCpfInput.setCustomValidity("");
      cnpjCpfInput.value = "";
    }
    if (observacaoInput && lastSubmitOk) {
      observacaoInput.setCustomValidity("");
      observacaoInput.value = "";
    }
    if (lastSubmitOk) {
      csrfToken = null;
      loadRoleOptions().catch(() => {
        renderRoleOptions([]);
      });
    }
  });
}

loadRoleOptions().catch(() => {
  renderRoleOptions([]);
});

