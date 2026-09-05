const state = {
  items: [],
  itemChecks: new Map(),
  pendingDeleteId: null,
  csrfToken: null,
  availableRoles: [],
  selectedRole: "",
  userRoles: [],
  userRolesReady: false,
  pagination: {
    page: 1,
    pageSize: 10,
    totalItems: 0,
    totalPages: 1,
    hasNext: false,
    hasPrevious: false,
    nextCursor: null,
    previousCursor: null,
    pageRequests: new Map([[1, { cursor: null, direction: "NEXT" }]]),
  },
};

const filterDateInput = document.querySelector(".filter-date-range");
const filterDescricaoTrigger = document.querySelector(".filter-descricao-trigger");
const filterDescricaoMenu = document.querySelector(".filter-descricao-menu");
let filterDescricaoValue = "";
const filterRazaoInput = document.querySelector(".filter-razao-input");
const filterTypeTrigger = document.querySelector(".filter-type-trigger");
const filterTypeMenu = document.querySelector(".filter-type-menu");
const filterTypeOptions = document.querySelectorAll(".filter-type-option");
let filterTypeValue = "";
const filterClear = document.querySelector(".filter-clear");
const filterRazaoToggle = document.querySelector(".filter-toggle");
const filterExtraField = document.querySelector("[data-filter-extra]");
const technicalRoles = new Set(["ADMIN", "CONTABIL", "ESTAGIARIO", "MANAGER", "SUPPORT", "CANDIDATO"]);
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
          resetPagination();
          try {
            await loadItems();
          } catch (error) {
            showListState(
              error instanceof Error
                ? error.message
                : "Erro ao carregar comprovantes do candidato selecionado."
            );
          }
        },
      })
    : null;
const listState = document.getElementById("list-state");
const itemsList = document.getElementById("items-list");
const paginationContainers = Array.from(document.querySelectorAll("[data-pagination]"));
const itemCardTemplate = document.getElementById("item-card-template");
const confirmOverlay = document.querySelector(".confirm-overlay");
const confirmCancel = document.querySelector(".confirm-cancel");
const confirmDelete = document.querySelector(".confirm-delete");
const uploadOverlay = document.querySelector(".upload-overlay");
const uploadClose = document.querySelector(".upload-close");
const uploadFiles = document.getElementById("upload-files");
const uploadInput = document.querySelector(".upload-input");
const uploadSave = document.querySelector(".upload-save");
const uploadEdit = document.querySelector(".upload-edit");
const uploadDrop = document.querySelector("[data-upload-drop]");
const uploadSelected = document.getElementById("upload-selected");
const observacaoOverlay = document.querySelector(".observacao-overlay");
const observacaoClose = document.querySelector(".observacao-close");
const observacaoContent = document.getElementById("observacao-content");
const observacaoEdit = document.querySelector(".observacao-edit");
const observacaoSave = document.querySelector(".observacao-save");
const historicoOverlay = document.querySelector(".historico-overlay");
const historicoClose = document.querySelector(".historico-close");
const historicoContent = document.getElementById("historico-content");
const itemEditOverlay = document.querySelector(".item-edit-overlay");
const itemEditForm = document.getElementById("item-edit-form");
const itemEditHistory = document.querySelector(".item-edit-history");
const itemEditClose = document.querySelector(".item-edit-close");
const itemEditValor = document.getElementById("item-edit-valor");
const itemEditTipo = document.getElementById("item-edit-tipo");
const itemEditData = document.getElementById("item-edit-data");
const itemEditDescricao = document.getElementById("item-edit-descricao");
const itemEditRazao = document.getElementById("item-edit-razao");
const itemEditCnpjCpf = document.getElementById("item-edit-cnpj-cpf");
const itemEditObservacao = document.getElementById("item-edit-observacao");
const itemEditError = document.getElementById("item-edit-error");
const itemEditSave = document.querySelector(".item-edit-save");
const pagamentoOverlay = document.querySelector(".pagamento-overlay");
const pagamentoClose = document.querySelector(".pagamento-close");
const pagamentoTitle = document.getElementById("pagamento-title");
const pagamentoSubtitle = document.getElementById("pagamento-subtitle");
const pagamentoQuantidade = document.getElementById("pagamento-quantidade");
const pagamentoParcelas = document.getElementById("pagamento-parcelas");
const pagamentoTotalPago = document.getElementById("pagamento-total-pago");
const pagamentoValidation = document.getElementById("pagamento-validation");
const pagamentoSave = document.querySelector(".pagamento-save");
const pagamentoFormaInputs = document.querySelectorAll('input[name="pagamento-forma"]');
const pagamentoSuccessOverlay = document.querySelector(".pagamento-success-overlay");
const pagamentoSuccessClose = document.querySelector(".pagamento-success-close");
const MAX_RECEIPT_SIZE_BYTES = 20 * 1024 * 1024;
const MAX_INSTALLMENT_VALUE = 5_000_000;
const PAGAMENTO_CONTAS_ORIGEM = new Set(["CONTA_DC", "CONTA_FEFC", "CONTA_FP"]);
const PAGAMENTO_CONTAS_ORIGEM_LABELS = {
  CONTA_DC: "CONTA DC",
  CONTA_FEFC: "CONTA FEFC",
  CONTA_FP: "CONTA FP",
};
const PDF_ONLY_MESSAGE = "Envie somente arquivos PDF.";
const MAX_RECEIPT_SIZE_MESSAGE = "Cada comprovante deve ter no maximo 20 MB.";
let pendingUploadItemId = null;
let pendingObservacaoItemId = null;
let pendingHistoricoItemId = null;
let pendingItemEditId = null;
let itemEditDatePicker = null;
let itemEditDescricaoRequestSequence = 0;
let pendingPagamentoItemId = null;
let uploadIsEditing = false;
let pendingDeleteArquivoIds = new Set();
let filterDatePicker = null;
let dateFilterReady = false;
let dateFilterInitPromise = null;
let flatpickrWarmupPromise = null;
let flatpickrWarmupScheduled = false;
let lastAppliedDateRangeValue = "";
let monthMenuCloseHandlerBound = false;
let yearMenuCloseHandlerBound = false;
let observacaoIsEditing = false;
let retainedUploadFiles = [];
let settingUploadFilesProgrammatically = false;
let uploadErrorEntries = [];
let razaoFilterDebounceTimer = null;
let loadItemsRequestSequence = 0;
let descricaoOptionsRenderSequence = 0;
let pagamentoState = null;
const descricaoOptionsCache = new Map();

const RECEITA_DESCRICOES = ["CONTA DC", "CONTA FEFC", "CONTA FP", "ESTIMÁVEL"];

const DESPESA_DESCRICOES = [
  "Publicidade por materiais impressos",
  "Publicidade na internet",
  "Publicidade por carro de som",
  "Produ\u00E7\u00E3o de programas de r\u00E1dio, TV ou v\u00EDdeo",
  "Impulsionamento de conte\u00FAdo",
  "Servi\u00E7os prestados por terceiros",
  "Servi\u00E7os advocat\u00EDcios",
  "Servi\u00E7os cont\u00E1beis",
  "Atividades de milit\u00E2ncia e mobiliza\u00E7\u00E3o de rua",
  "Remunera\u00E7\u00E3o de pessoal",
  "Aluguel de im\u00F3veis",
  "Aluguel de ve\u00EDculos",
  "Combust\u00EDveis e lubrificantes",
  "Energia el\u00E9trica",
  "\u00C1gua",
  "Internet",
  "Telefone",
  "Material de expediente",
  "Material de campanha (n\u00E3o publicit\u00E1rio)",
  "Alimenta\u00E7\u00E3o",
  "Transporte ou deslocamento",
  "Hospedagem",
  "Organiza\u00E7\u00E3o de eventos",
  "Produ\u00E7\u00E3o de jingles, vinhetas e slogans",
  "Produ\u00E7\u00E3o de material gr\u00E1fico",
  "Cria\u00E7\u00E3o e inclus\u00E3o de p\u00E1ginas na internet",
  "Manuten\u00E7\u00E3o de sites",
  "Softwares e ferramentas digitais",
  "Taxas banc\u00E1rias",
  "Encargos financeiros",
  "Multas eleitorais",
  "Doa\u00E7\u00F5es a outros candidatos/partidos",
  "Baixa de estim\u00E1veis em dinheiro",
  "Outras despesas",
];

const EXTRATO_BANCARIO_TYPE = "EXTRATO_BANCARIO";
const EXTRATO_BANCARIO_DESCRICOES = ["CONTA FEFC", "CONTA FP", "CONTA DC"];
const MAX_ITEM_EDIT_VALOR_CENTS = 1_000_000_000;
const ITEM_EDIT_DATE_LOCALE = {
  firstDayOfWeek: 0,
  weekdays: {
    shorthand: ["Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"],
    longhand: ["Domingo", "Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado"],
  },
  months: {
    shorthand: ["Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez"],
    longhand: ["Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho", "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"],
  },
};

const getAccessToken = () => localStorage.getItem("sc_access_token");

const normalizeRoles = (roles) =>
  Array.isArray(roles)
    ? roles.map((role) => String(role || "").trim().toUpperCase()).filter(Boolean)
    : [];

const sameRoles = (left, right) =>
  left.length === right.length && left.every((role, index) => role === right[index]);

const loadCurrentUserRoles = async () => {
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
    if (response.status === 401) {
      window.location.href = "/login";
      return [];
    }
    if (!response.ok) return [];
    return normalizeRoles(await response.json());
  } catch (error) {
    return [];
  }
};

const applyCurrentUserRoles = (roles) => {
  const normalizedRoles = normalizeRoles(roles);
  const previousRoles = state.userRoles;
  const shouldRerender =
    state.items.length > 0 &&
    (!state.userRolesReady || !sameRoles(previousRoles, normalizedRoles));
  state.userRoles = normalizedRoles;
  state.userRolesReady = true;
  if (shouldRerender) {
    renderItems();
  }
};

const loadAndApplyCurrentUserRoles = async () => {
  try {
    applyCurrentUserRoles(await loadCurrentUserRoles());
  } catch (error) {
    applyCurrentUserRoles([]);
  }
};

const isContabilUser = () =>
  state.userRoles.includes("CONTABIL") || state.userRoles.includes("ESTAGIARIO");
const isItemEditReadOnlyUser = () => isContabilUser();

const formatLocalDateIso = (date) => {
  if (!(date instanceof Date)) return "";
  const yyyy = String(date.getFullYear());
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
};

const buildApiTipo = () => {
  const type = String(filterTypeValue || "").trim();
  return type ? type.toUpperCase() : "";
};

const getNormalizedDateFilterValue = () =>
  filterDateInput ? formatDateRangeInput(filterDateInput.value || "").trim() : "";

const buildListQuery = (cursor = null, direction = "NEXT") => {
  const params = new URLSearchParams({
    page: String(state.pagination.page),
    pageSize: String(state.pagination.pageSize),
  });

  if (state.selectedRole) {
    params.set("role", state.selectedRole);
  }

  const tipo = buildApiTipo();
  if (tipo) {
    params.set("tipo", tipo);
  }

  const { start, end } = parseDateRange(filterDateInput?.value || "");
  if (start) {
    params.set("dataInicio", formatLocalDateIso(start));
  }
  if (end) {
    params.set("dataFim", formatLocalDateIso(end));
  }

  const descricao = String(filterDescricaoValue || "").trim();
  if (descricao) {
    params.set("descricao", descricao);
  }

  const razao = String(filterRazaoInput?.value || "").trim();
  if (razao) {
    params.set("razao", razao);
  }

  if (cursor) {
    params.set("cursor", cursor);
    params.set("direction", direction);
  }

  return `?${params.toString()}`;
};

const debounce = (callback, delayMs) => (...args) => {
  if (razaoFilterDebounceTimer) {
    window.clearTimeout(razaoFilterDebounceTimer);
  }
  razaoFilterDebounceTimer = window.setTimeout(() => {
    razaoFilterDebounceTimer = null;
    callback(...args);
  }, delayMs);
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
  state.availableRoles = [];
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
  state.availableRoles = orderedRoles;
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

const setUploadSaveVisible = (visible) => {
  if (!uploadSave) return;
  setButtonVisibleSmooth(uploadSave, visible);
};

const updateUploadSaveVisibility = () => {
  const hasNewFiles = uploadInput?.files && Array.from(uploadInput.files).some(isPdfFile);
  const hasDeletes = pendingDeleteArquivoIds.size > 0;
  setUploadSaveVisible(Boolean(uploadIsEditing || hasNewFiles || hasDeletes));
};

const smoothHideTimers = new WeakMap();
const SMOOTH_HIDE_MS = 100;
const ITEM_REMOVE_ANIMATION_MS = 320;

const setButtonVisibleSmooth = (button, visible) => {
  if (!(button instanceof HTMLButtonElement)) return;
  const previousTimer = smoothHideTimers.get(button);
  if (previousTimer) {
    window.clearTimeout(previousTimer);
    smoothHideTimers.delete(button);
  }

  if (visible) {
    button.hidden = false;
    button.removeAttribute("hidden");
    button.classList.remove("is-hiding");
    return;
  }

  if (button.hidden) {
    button.setAttribute("hidden", "");
    button.classList.remove("is-hiding");
    return;
  }

  button.classList.add("is-hiding");
  const timer = window.setTimeout(() => {
    button.hidden = true;
    button.setAttribute("hidden", "");
    button.classList.remove("is-hiding");
    smoothHideTimers.delete(button);
  }, SMOOTH_HIDE_MS);
  smoothHideTimers.set(button, timer);
};

const isPdfFile = (file) => {
  if (!file) return false;
  const name = String(file.name || "").toLowerCase();
  return file.type === "application/pdf" || name.endsWith(".pdf");
};

const extractErrorMessage = async (response, fallbackMessage) => {
  const effectiveFallback =
    response?.status === 413 ? MAX_RECEIPT_SIZE_MESSAGE : fallbackMessage;
  try {
    const payload = await response.json();
    if (payload && typeof payload === "object") {
      return payload.message || payload.error || effectiveFallback;
    }
  } catch (error) {
    // Keep fallback message when payload is not JSON.
  }
  return effectiveFallback;
};

const formatCurrency = (value) =>
  Number(value || 0).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

const toInstallmentCents = (value) => Math.round(Number(value || 0) * 100);

const calculateInstallmentValues = (value, count) => {
  const quantidade = Math.max(Number(count || 1), 1);
  const totalCents = toInstallmentCents(value);
  const base = Math.trunc(totalCents / quantidade);
  const remainder = totalCents % quantidade;
  return Array.from({ length: quantidade }, (_, index) => (base + (index < remainder ? 1 : 0)) / 100);
};

const extractDisplayFileName = (value) => {
  const text = String(value || "").trim();
  if (!text) return "";
  const parts = text.split(/[\\/]/);
  return parts[parts.length - 1] || text;
};

const formatDate = (isoDate) => {
  const date = new Date(`${isoDate}T00:00:00`);
  return date.toLocaleDateString("pt-BR");
};

const formatTime = (isoDateTime) => {
  const date = new Date(isoDateTime);
  return date.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
};

const formatDateTime = (isoDateTime) => {
  const date = new Date(isoDateTime);
  if (!Number.isFinite(date.getTime())) {
    return "-";
  }
  const datePart = date.toLocaleDateString("pt-BR");
  const timePart = date.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
  return `${datePart} ${timePart}`;
};

const formatDescricao = (value) => {
  const key = String(value || "").trim().toUpperCase();
  const labels = {
    ALUGUEL: "Aluguel",
    ENERGIA: "Energia el\u00E9trica",
    AGUA: "\u00C1gua",
    SERVICOS: "Servi\u00E7os",
    IMPOSTOS: "Impostos",
    MATERIAIS: "Materiais",
    OUTROS: "Outros",
  };
  return labels[key] || (value ? String(value) : "-");
};

const getFilterDescricaoOptions = () =>
  Array.from(filterDescricaoMenu?.querySelectorAll(".filter-descricao-option") || []);

const closeFilterDescricaoMenu = () => {
  if (!filterDescricaoMenu || !filterDescricaoTrigger) return;
  filterDescricaoMenu.hidden = true;
  filterDescricaoTrigger.setAttribute("aria-expanded", "false");
};

const getFallbackDescricaoOptionsByTipo = (tipoValue) => {
  const tipo = String(tipoValue || "").trim().toLowerCase();
  if (tipo === "receita") {
    return [...RECEITA_DESCRICOES];
  }
  if (tipo === "despesa") {
    return [...DESPESA_DESCRICOES];
  }
  return [...new Set([...RECEITA_DESCRICOES, ...DESPESA_DESCRICOES])].sort((a, b) =>
    a.localeCompare(b, "pt-BR", { sensitivity: "base" }),
  );
};

const normalizeDescricaoOptions = (options) =>
  [
    ...new Set(
      (Array.isArray(options) ? options : [])
        .map((option) => String(option || "").trim())
        .filter(Boolean),
    ),
  ].sort((a, b) => a.localeCompare(b, "pt-BR", { sensitivity: "base" }));

const getDescricaoApiTipo = (tipoValue) => {
  const tipo = String(tipoValue || "").trim().toLowerCase();
  if (tipo === "receita") {
    return "RECEITA";
  }
  if (tipo === "despesa") {
    return "DESPESA";
  }
  return "";
};

const fetchDescricaoOptionsByApiTipo = async (apiTipo) => {
  if (!apiTipo) {
    return [];
  }

  if (descricaoOptionsCache.has(apiTipo)) {
    return descricaoOptionsCache.get(apiTipo);
  }

  const response = await fetch(`/api/v1/itens/descricoes?tipo=${encodeURIComponent(apiTipo)}`, {
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
    },
  });

  if (!response.ok) {
    throw new Error("Falha ao carregar descrições do filtro.");
  }

  const payload = await response.json();
  const normalized = normalizeDescricaoOptions(payload);
  descricaoOptionsCache.set(apiTipo, normalized);
  return normalized;
};

const resolveDescricaoOptionsByTipo = async (tipoValue) => {
  const apiTipo = getDescricaoApiTipo(tipoValue);
  if (apiTipo) {
    return fetchDescricaoOptionsByApiTipo(apiTipo);
  }

  const [receita, despesa] = await Promise.all([
    fetchDescricaoOptionsByApiTipo("RECEITA"),
    fetchDescricaoOptionsByApiTipo("DESPESA"),
  ]);
  return normalizeDescricaoOptions([...receita, ...despesa]);
};

const renderFilterDescricaoOptions = (
  tipoValue,
  options = getFallbackDescricaoOptionsByTipo(tipoValue),
) => {
  if (!filterDescricaoMenu || !filterDescricaoTrigger) return;

  const shouldKeepSelection = options.some((option) => option === filterDescricaoValue);
  if (!shouldKeepSelection) {
    filterDescricaoValue = "";
  }

  filterDescricaoMenu.innerHTML = "";

  const createOption = (value, label, active) => {
    const button = document.createElement("button");
    button.className = `filter-descricao-option${active ? " is-active" : ""}`;
    button.type = "button";
    button.setAttribute("role", "option");
    button.dataset.value = value;
    button.textContent = label;
    return button;
  };

  filterDescricaoMenu.appendChild(createOption("", "Todas", filterDescricaoValue === ""));
  options.forEach((option) => {
    filterDescricaoMenu.appendChild(
      createOption(option, option, String(option) === String(filterDescricaoValue)),
    );
  });

  filterDescricaoTrigger.textContent = filterDescricaoValue || "Todas";
};

const syncFilterDescricaoOptions = async (tipoValue) => {
  const requestedTipo = String(tipoValue || "").trim().toLowerCase();
  const renderSequence = ++descricaoOptionsRenderSequence;
  renderFilterDescricaoOptions(tipoValue);

  try {
    const options = await resolveDescricaoOptionsByTipo(tipoValue);
    if (renderSequence !== descricaoOptionsRenderSequence) {
      return;
    }
    if (String(filterTypeValue || "").trim().toLowerCase() !== requestedTipo) {
      return;
    }
    renderFilterDescricaoOptions(tipoValue, options);
  } catch (error) {
    console.warn("Nao foi possivel sincronizar descricoes do filtro.", error);
  }
};

const formatText = (value) => (value ? String(value) : "-");

const normalizeText = (value) => String(value || "").toLowerCase();

const parseDateRange = (value) => {
  const text = String(value || "").trim();
  if (!text) {
    return { start: null, end: null };
  }

  const [startRaw, endRaw] = text.split(" - ").map((part) => part.trim());
  const parse = (dateValue) => {
    const [day, month, year] = String(dateValue || "")
      .split("/")
      .map((part) => Number(part));
    if (!day || !month || !year) {
      return null;
    }
    const date = new Date(year, month - 1, day);
    if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
      return null;
    }
    date.setHours(0, 0, 0, 0);
    return date;
  };

  const start = parse(startRaw);
  const end = endRaw ? parse(endRaw) : start;
  return { start, end };
};

const todayMidnight = () => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return today;
};

const formatDatePt = (date) => {
  if (!(date instanceof Date)) return "";
  const dd = String(date.getDate()).padStart(2, "0");
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const yyyy = String(date.getFullYear());
  return `${dd}/${mm}/${yyyy}`;
};

const formatDateDigits = (digits) => {
  const value = String(digits || "").slice(0, 8);
  if (value.length <= 2) {
    return value;
  }
  if (value.length <= 4) {
    return `${value.slice(0, 2)}/${value.slice(2)}`;
  }
  return `${value.slice(0, 2)}/${value.slice(2, 4)}/${value.slice(4)}`;
};

const formatDateRangeInput = (value) => {
  const digits = String(value || "").replace(/\D/g, "").slice(0, 16);
  const startDigits = digits.slice(0, 8);
  const endDigits = digits.slice(8, 16);
  const start = formatDateDigits(startDigits);
  const end = formatDateDigits(endDigits);

  if (!endDigits) {
    return start;
  }
  return `${start} - ${end}`;
};

const scheduleAfterFirstRender = (callback) => {
  const runWhenIdle = () => {
    if (typeof window.requestIdleCallback === "function") {
      window.requestIdleCallback(() => {
        callback();
      }, { timeout: 1200 });
      return;
    }
    window.setTimeout(() => {
      callback();
    }, 0);
  };

  window.requestAnimationFrame(() => {
    window.requestAnimationFrame(() => {
      runWhenIdle();
    });
  });
};

const loadScript = (src) =>
  new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[data-src="${src}"]`);
    if (existing) {
      if (existing.dataset.loaded === "true") {
        resolve();
        return;
      }
      existing.addEventListener("load", () => resolve(), { once: true });
      existing.addEventListener("error", () => reject(new Error(`Falha ao carregar ${src}`)), {
        once: true,
      });
      return;
    }

    const script = document.createElement("script");
    script.src = src;
    script.async = true;
    script.dataset.src = src;
    script.addEventListener(
      "load",
      () => {
        script.dataset.loaded = "true";
        resolve();
      },
      { once: true },
    );
    script.addEventListener("error", () => reject(new Error(`Falha ao carregar ${src}`)), {
      once: true,
    });
    document.head.appendChild(script);
  });

const FLATPICKR_SCRIPT_SRC = "assets/vendor/flatpickr/flatpickr.min.js";

const warmupFlatpickrAssets = async () => {
  if (flatpickrWarmupPromise) {
    return flatpickrWarmupPromise;
  }

  flatpickrWarmupPromise = Promise.allSettled([
    window.flatpickr ? Promise.resolve() : loadScript(FLATPICKR_SCRIPT_SRC),
  ]).then(() => {
    const ready = Boolean(window.flatpickr);
    if (!ready) {
      flatpickrWarmupPromise = null;
    }
    return ready;
  });

  return flatpickrWarmupPromise;
};

const scheduleFlatpickrWarmup = () => {
  if (flatpickrWarmupScheduled) {
    return;
  }
  flatpickrWarmupScheduled = true;
  scheduleAfterFirstRender(() => {
    void warmupFlatpickrAssets();
  });
};

const ensureFlatpickrReady = async () => {
  if (window.flatpickr) {
    return true;
  }

  try {
    await warmupFlatpickrAssets();
    return Boolean(window.flatpickr);
  } catch (error) {
    return false;
  }
};

const initDateFilter = async () => {
  if (!filterDateInput || dateFilterReady) {
    return;
  }

  if (dateFilterInitPromise) {
    return dateFilterInitPromise;
  }

  dateFilterInitPromise = (async () => {
    const flatpickrReady = await ensureFlatpickrReady();
    if (!flatpickrReady || !window.flatpickr) {
      return;
    }

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
      "Mar\u00E7o",
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

    filterDatePicker = window.flatpickr(filterDateInput, {
      mode: "range",
      dateFormat: "d/m/Y",
      allowInput: true,
      clickOpens: true,
      maxDate,
      monthSelectorType: "static",
      locale: {
        rangeSeparator: " - ",
      },
      onReady: (_, __, instance) => {
        ensureMonthDropdown(instance);
        ensureYearDropdown(instance);
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
      onValueUpdate: (_, dateText) => {
        filterDateInput.value = dateText;
      },
      onChange: () => {
        const normalizedValue = getNormalizedDateFilterValue();
        if (normalizedValue === lastAppliedDateRangeValue) {
          return;
        }
        lastAppliedDateRangeValue = normalizedValue;
        void applyFilters();
      },
    });

    dateFilterReady = true;
  })().finally(() => {
    dateFilterInitPromise = null;
  });

  return dateFilterInitPromise;
};

const showListState = (message) => {
  if (!listState) return;
  listState.hidden = false;
  listState.textContent = message;
};

const hideListState = () => {
  if (!listState) return;
  listState.hidden = true;
  listState.textContent = "";
};

const isItemChecked = (itemId) => state.itemChecks.get(String(itemId)) === true;
const CHECKED_ITEM_DELETE_BLOCKED_MESSAGE =
  "Comprovantes verificados nao podem ser excluidos. Desmarque o check antes de excluir.";
const SUPPORT_UNCHECK_BLOCKED_MESSAGE =
  "Usuarios SUPPORT nao podem desmarcar comprovantes verificados.";

const setItemChecked = (itemId, checked) => {
  const itemKey = String(itemId || "");
  if (!itemKey) return;
  state.itemChecks.set(itemKey, checked === true);
};

const isCandidatoUser = () => state.userRoles.includes("CANDIDATO");
const isSupportUser = () => state.userRoles.includes("SUPPORT");

const setCheckButtonState = (button, checked) => {
  if (!(button instanceof HTMLButtonElement)) return;
  const supportLocked = isSupportUser() && checked === true;
  button.classList.toggle("is-checked", checked === true);
  button.classList.toggle("is-locked", supportLocked);
  button.disabled = supportLocked;
  button.setAttribute("aria-disabled", String(supportLocked));
  button.setAttribute("aria-pressed", String(checked === true));
  button.setAttribute(
    "aria-label",
    supportLocked
      ? SUPPORT_UNCHECK_BLOCKED_MESSAGE
      : checked
        ? "Desmarcar comprovante"
        : "Marcar comprovante como verificado",
  );
  if (supportLocked) {
    button.title = SUPPORT_UNCHECK_BLOCKED_MESSAGE;
  } else {
    button.removeAttribute("title");
  }
};

const setDeleteButtonLocked = (button, locked) => {
  if (!(button instanceof HTMLButtonElement)) return;
  button.disabled = locked === true;
  button.classList.toggle("is-disabled", locked === true);
  button.setAttribute(
    "aria-label",
    locked ? CHECKED_ITEM_DELETE_BLOCKED_MESSAGE : "Excluir comprovante",
  );
  if (locked) {
    button.title = CHECKED_ITEM_DELETE_BLOCKED_MESSAGE;
  } else {
    button.removeAttribute("title");
  }
};

const syncItemCheckedState = (itemId, checked) => {
  setItemChecked(itemId, checked);
  const index = state.items.findIndex((entry) => String(entry.id) === String(itemId));
  if (index >= 0) {
    state.items[index].verificado = checked === true;
  }
  if (itemsList) {
    const card = itemsList.querySelector(`.item-card[data-id="${itemId}"]`);
    if (card instanceof HTMLElement) {
      card.classList.toggle("is-checked", checked === true);
      setCheckButtonState(card.querySelector(".item-check-toggle"), checked === true);
      setDeleteButtonLocked(card.querySelector(".delete-item"), checked === true);
    }
  }
};

const createItemCard = (item) => {
  const node = itemCardTemplate.content.cloneNode(true);
  const article = node.querySelector(".item-card");
  article.dataset.id = item.id;
  article.dataset.tipo = String(item.tipo || "").toLowerCase();
  article.dataset.data = item.data;
  article.classList.toggle("is-checked", isItemChecked(item.id));

  node.querySelector('[data-field="valor"]').textContent = formatCurrency(item.valor);
  node.querySelector('[data-field="tipo"]').textContent =
    item.tipo === "RECEITA" ? "Receita" : "Despesa";
  node.querySelector('[data-field="data"]').textContent = formatDate(item.data);
  node.querySelector('[data-field="horario"]').textContent = formatDateTime(item.horarioCriacao);
  node.querySelector('[data-field="descricao"]').textContent = formatDescricao(item.descricao);
  const razaoText = formatText(item.razaoSocialNome);
  const razaoNode = node.querySelector('[data-field="razaoSocialNome"]');
  const razaoContainer = node.querySelector(".item-info--razao");
  if (razaoNode) {
    razaoNode.textContent = razaoText;
  }
  if (razaoContainer) {
    const length = razaoText.replace(/\s+/g, " ").trim().length;
    razaoContainer.classList.toggle("is-long", length >= 28);
  }
  node.querySelector('[data-field="cnpjCpf"]').textContent = formatText(item.cnpjCpf);

  const downloadLink = node.querySelector(".item-download");
  const hasArquivos =
    Boolean(item.temArquivos) ||
    (Array.isArray(item.arquivosPdf) && item.arquivosPdf.length > 0) ||
    (item.caminhoArquivoPdf && String(item.caminhoArquivoPdf).trim().length > 0);
  const arquivoUrl = item.id ? `/api/v1/itens/${item.id}/arquivos/download` : null;
  if (arquivoUrl && hasArquivos) {
    downloadLink.href = arquivoUrl;
    downloadLink.download = "";
    downloadLink.classList.remove("is-disabled");
    downloadLink.removeAttribute("aria-disabled");
  } else {
    downloadLink.removeAttribute("href");
    downloadLink.classList.add("is-disabled");
    downloadLink.setAttribute("aria-disabled", "true");
  }

  const checkButton = node.querySelector(".item-check-toggle");
  if (checkButton instanceof HTMLButtonElement) {
    if (!state.userRolesReady || isCandidatoUser()) {
      checkButton.remove();
    } else {
      const checked = isItemChecked(item.id);
      setCheckButtonState(checkButton, checked);
    }
  }

  const deleteButton = node.querySelector(".delete-item");
  if (!state.userRolesReady && deleteButton instanceof HTMLElement) {
    deleteButton.remove();
  } else if (isContabilUser() && deleteButton instanceof HTMLElement) {
    deleteButton.remove();
  } else {
    setDeleteButtonLocked(deleteButton, isItemChecked(item.id));
  }

  if (item.tipo !== "DESPESA") {
    node.querySelector(".item-pagamento")?.remove();
  }

  return node;
};

const updateDownloadButton = (itemId, hasArquivos) => {
  const item = state.items.find((entry) => String(entry.id) === String(itemId));
  if (item) {
    item.temArquivos = Boolean(hasArquivos);
  }
  if (!itemsList) return;
  const card = itemsList.querySelector(`.item-card[data-id="${itemId}"]`);
  if (!card) return;
  const downloadLink = card.querySelector(".item-download");
  if (!downloadLink) return;
  if (hasArquivos) {
    downloadLink.href = `/api/v1/itens/${itemId}/arquivos/download`;
    downloadLink.download = "";
    downloadLink.classList.remove("is-disabled");
    downloadLink.removeAttribute("aria-disabled");
  } else {
    downloadLink.removeAttribute("href");
    downloadLink.classList.add("is-disabled");
    downloadLink.setAttribute("aria-disabled", "true");
  }
};

const resetPagination = () => {
  state.pagination.page = 1;
  state.pagination.totalItems = 0;
  state.pagination.totalPages = 1;
  state.pagination.hasNext = false;
  state.pagination.hasPrevious = false;
  state.pagination.nextCursor = null;
  state.pagination.previousCursor = null;
  state.pagination.pageRequests = new Map([[1, { cursor: null, direction: "NEXT" }]]);
};

const createPaginationButton = ({
  label,
  page,
  cursor = null,
  direction = "NEXT",
  current = false,
  disabled = false,
}) => {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "pagination-btn";
  button.textContent = label;
  button.dataset.page = String(page);
  button.dataset.cursor = cursor || "";
  button.dataset.direction = direction;
  button.disabled = disabled || current;
  if (current) {
    button.classList.add("is-current");
    button.setAttribute("aria-current", "page");
  } else {
    button.setAttribute("aria-label", `Ir para página ${page}`);
  }
  return button;
};

const createPaginationEllipsis = () => {
  const ellipsis = document.createElement("span");
  ellipsis.className = "pagination-ellipsis";
  ellipsis.setAttribute("aria-hidden", "true");
  ellipsis.textContent = "…";
  return ellipsis;
};

const getVisiblePageNumbers = () => {
  const totalPages = Math.max(1, state.pagination.totalPages);
  if (totalPages <= 7) {
    return Array.from({ length: totalPages }, (_, index) => index + 1);
  }

  const currentPage = state.pagination.page;
  return Array.from(
    new Set([
      1,
      2,
      3,
      currentPage,
      totalPages - 2,
      totalPages - 1,
      totalPages,
    ]),
  ).sort((left, right) => left - right);
};

const navigateToPaginationPage = async (page) => {
  const knownRequest = state.pagination.pageRequests.get(page);
  if (knownRequest) {
    state.pagination.page = page;
    await loadItems(knownRequest.cursor, knownRequest.direction);
    return;
  }

  while (state.pagination.page < page) {
    const cursor = state.pagination.nextCursor;
    if (!state.pagination.hasNext || !cursor) {
      throw new Error("Não foi possível acessar a página selecionada.");
    }
    state.pagination.page += 1;
    await loadItems(cursor, "NEXT");
  }
};

const renderPagination = () => {
  if (paginationContainers.length === 0) return;

  if (state.items.length === 0) {
    paginationContainers.forEach((container) => {
      container.hidden = true;
      container.innerHTML = "";
    });
    return;
  }

  const hasPrevious = state.pagination.hasPrevious && Boolean(state.pagination.previousCursor);
  const hasNext = state.pagination.hasNext && Boolean(state.pagination.nextCursor);
  if (!hasPrevious && !hasNext) {
    paginationContainers.forEach((container) => {
      container.hidden = true;
      container.innerHTML = "";
    });
    return;
  }

  const pageNumbers = getVisiblePageNumbers();
  paginationContainers.forEach((container) => {
    container.hidden = false;
    container.innerHTML = "";
    container.appendChild(
      createPaginationButton({
        label: "‹",
        page: Math.max(1, state.pagination.page - 1),
        cursor: state.pagination.previousCursor,
        direction: "PREVIOUS",
        disabled: !hasPrevious,
      }),
    );

    let previousPage = 0;
    pageNumbers.forEach((page) => {
      if (page - previousPage > 1) {
        container.appendChild(createPaginationEllipsis());
      }
      const request = state.pagination.pageRequests.get(page);
      container.appendChild(
        createPaginationButton({
          label: String(page),
          page,
          cursor: request?.cursor,
          direction: request?.direction,
          current: page === state.pagination.page,
        }),
      );
      previousPage = page;
    });

    container.appendChild(
      createPaginationButton({
        label: "›",
        page: state.pagination.page + 1,
        cursor: state.pagination.nextCursor,
        direction: "NEXT",
        disabled: !hasNext,
      }),
    );
  });
};

const renderItems = () => {
  if (!itemsList) return;
  itemsList.innerHTML = "";
  if (state.items.length === 0) {
    showListState("Nenhum comprovante encontrado.");
    paginationContainers.forEach((container) => {
      container.hidden = true;
      container.innerHTML = "";
    });
    return;
  }
  hideListState();

  const fragment = document.createDocumentFragment();
  state.items.forEach((item) => {
    fragment.appendChild(createItemCard(item));
  });
  itemsList.appendChild(fragment);

  renderPagination();
};

const applyFilters = async (resetPage = true) => {
  if (resetPage) {
    resetPagination();
  }
  try {
    await loadItems();
  } catch (error) {
    showListState(
      error instanceof Error ? error.message : "Erro ao carregar comprovantes. Tente novamente.",
    );
  }
};

const waitForAuthReady = async () => {
  if (!window.SCAuth?.waitUntilReady) {
    return;
  }
  try {
    await window.SCAuth.waitUntilReady();
  } catch (error) {
    // If bootstrap fails, the request flow still gets a chance to handle auth errors normally.
  }
};

const ensureCsrfToken = async (forceRefresh = false) => {
  if (!forceRefresh && state.csrfToken) return state.csrfToken;
  if (forceRefresh) {
    await waitForAuthReady();
  }
  if (window.SCAuth?.ensureCsrfToken) {
    state.csrfToken = await window.SCAuth.ensureCsrfToken(forceRefresh);
    if (!state.csrfToken) {
      throw new Error("Token CSRF ausente.");
    }
    return state.csrfToken;
  }
  const response = await fetch("/api/v1/auth/csrf", {
    method: "GET",
    credentials: "same-origin",
    headers: {
      Authorization: `Bearer ${getAccessToken()}`,
    },
  });
  if (!response.ok) {
    throw new Error("Falha ao obter token CSRF.");
  }
  const data = await response.json();
  state.csrfToken = data.token || null;
  if (!state.csrfToken) {
    throw new Error("Token CSRF ausente.");
  }
  return state.csrfToken;
};

const openUploadModal = async (id) => {
  pendingUploadItemId = id;
  if (!uploadOverlay) return;
  hideListState();
  uploadIsEditing = false;
  pendingDeleteArquivoIds = new Set();
  retainedUploadFiles = [];
  clearUploadErrorEntries();
  if (uploadInput) {
    uploadInput.value = "";
  }
  if (uploadSelected) {
    uploadSelected.innerHTML = "";
    uploadSelected.classList.remove("is-grid");
  }
  if (uploadFiles) uploadFiles.classList.remove("is-editing");
  updateUploadSaveVisibility();
  uploadOverlay.classList.add("is-visible");
  uploadOverlay.setAttribute("aria-hidden", "false");
  await loadItemArquivos(id);
};

const closeUploadModal = () => {
  pendingUploadItemId = null;
  if (!uploadOverlay) return;
  uploadOverlay.classList.remove("is-visible");
  uploadOverlay.setAttribute("aria-hidden", "true");
  retainedUploadFiles = [];
  clearUploadErrorEntries();
  if (uploadInput) {
    uploadInput.value = "";
  }
  if (uploadSelected) {
    uploadSelected.innerHTML = "";
    uploadSelected.classList.remove("is-grid");
  }
  if (uploadDrop) {
    uploadDrop.classList.remove("is-active");
  }
  uploadIsEditing = false;
  pendingDeleteArquivoIds = new Set();
  if (uploadFiles) uploadFiles.classList.remove("is-editing");
  updateUploadSaveVisibility();
};

const openObservacaoModal = async (id) => {
  pendingObservacaoItemId = id;
  if (!observacaoOverlay || !observacaoContent) return;
  observacaoContent.value = "Carregando...";
  observacaoContent.readOnly = true;
  observacaoIsEditing = false;
  setObservacaoSaveVisible(false);
  observacaoOverlay.classList.add("is-visible");
  observacaoOverlay.setAttribute("aria-hidden", "false");
  try {
    const item = await fetchItemById(id);
    if (pendingObservacaoItemId !== id || !observacaoContent) return;
    observacaoContent.value = item?.observacao ? String(item.observacao) : "";
  } catch (error) {
    if (pendingObservacaoItemId !== id) return;
    closeObservacaoModal();
    showListState(error instanceof Error ? error.message : "Falha ao carregar observação.");
  }
};

const setItemEditError = (message = "") => {
  if (!itemEditError) return;
  itemEditError.textContent = message;
  itemEditError.hidden = !message;
};

const bindItemEditCustomSelect = (selectElement) => {
  if (!(selectElement instanceof HTMLSelectElement)) return null;
  const wrapper = selectElement.closest("[data-item-edit-custom-select]");
  const trigger = wrapper?.querySelector(".custom-select-trigger");
  const menu = wrapper?.querySelector(".custom-select-menu");
  if (!(wrapper instanceof HTMLElement) || !(trigger instanceof HTMLButtonElement) || !(menu instanceof HTMLElement)) {
    return null;
  }

  const close = () => {
    menu.hidden = true;
    trigger.setAttribute("aria-expanded", "false");
  };

  const syncFromSelect = () => {
    const selected = selectElement.selectedOptions?.[0];
    trigger.textContent = selected ? String(selected.textContent || "").trim() : "Selecione";
    menu.querySelectorAll(".custom-select-option").forEach((option) => {
      if (!(option instanceof HTMLElement)) return;
      option.classList.toggle("is-active", option.dataset.value === selectElement.value);
    });
  };

  trigger.addEventListener("click", () => {
    if (trigger.disabled) return;
    const willOpen = menu.hidden;
    close();
    if (willOpen) {
      menu.hidden = false;
      trigger.setAttribute("aria-expanded", "true");
    }
  });

  menu.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const option = target.closest(".custom-select-option");
    if (!(option instanceof HTMLElement)) return;
    selectElement.value = option.dataset.value || "";
    selectElement.dispatchEvent(new Event("change", { bubbles: true }));
    syncFromSelect();
    close();
  });

  document.addEventListener(
    "mousedown",
    (event) => {
      const target = event.target;
      if (!(target instanceof Node)) return;
      if (wrapper.contains(target)) return;
      close();
    },
    { capture: true },
  );
  selectElement.addEventListener("change", syncFromSelect);
  syncFromSelect();
  return { trigger, menu, close, syncFromSelect };
};

const itemEditTipoDropdown = bindItemEditCustomSelect(itemEditTipo);
const itemEditDescricaoDropdown = bindItemEditCustomSelect(itemEditDescricao);

const parseItemEditDate = (value) => {
  const [day, month, year] = String(value || "")
    .split("/")
    .map((part) => Number(part));
  if (!day || !month || !year) return null;
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }
  date.setHours(0, 0, 0, 0);
  return date;
};

const itemEditDateToIso = (value) => {
  const date = parseItemEditDate(value);
  return date ? formatLocalDateIso(date) : "";
};

const formatItemEditDate = (value) => formatDateDigits(String(value || "").replace(/\D/g, ""));

const formatItemEditDateFromIso = (value) => {
  const [year, month, day] = String(value || "").split("-");
  return year && month && day ? `${day}/${month}/${year}` : "";
};

const formatItemEditMoney = (value) => {
  const digits = String(value || "").replace(/\D/g, "");
  const cents = Math.min(Number(digits || 0), MAX_ITEM_EDIT_VALOR_CENTS);
  return (cents / 100).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
};

const formatItemEditMoneyFromValue = (value) => {
  const cents = Math.min(
    Math.max(Math.round(Number(value || 0) * 100), 0),
    MAX_ITEM_EDIT_VALOR_CENTS,
  );
  return formatItemEditMoney(String(cents));
};

const itemEditMoneyToDecimal = (value) => Number(String(value || "").replace(/\D/g, "") || 0) / 100;

const setItemEditReadOnly = (readOnly) => {
  const fields = [
    itemEditValor,
    itemEditTipo,
    itemEditData,
    itemEditDescricao,
    itemEditRazao,
    itemEditCnpjCpf,
    itemEditObservacao,
  ];
  fields.forEach((field) => {
    if (field) field.disabled = readOnly;
  });
  itemEditTipoDropdown?.close();
  itemEditDescricaoDropdown?.close();
  if (readOnly) itemEditDatePicker?.close();
  if (itemEditTipoDropdown) itemEditTipoDropdown.trigger.disabled = readOnly;
  if (itemEditDescricaoDropdown) itemEditDescricaoDropdown.trigger.disabled = readOnly;
  if (itemEditForm) itemEditForm.classList.toggle("is-read-only", readOnly);
  if (itemEditSave) {
    itemEditSave.disabled = readOnly;
    itemEditSave.textContent = readOnly ? "Somente leitura" : "Salvar";
  }
};

const initItemEditDatePicker = async () => {
  if (!itemEditData || itemEditDatePicker) return;
  if (!(await ensureFlatpickrReady()) || !window.flatpickr) return;
  itemEditDatePicker = window.flatpickr(itemEditData, {
    dateFormat: "d/m/Y",
    allowInput: true,
    clickOpens: true,
    maxDate: todayMidnight(),
    monthSelectorType: "static",
    locale: ITEM_EDIT_DATE_LOCALE,
    onChange: () => itemEditData.setCustomValidity(""),
  });
};

const renderItemEditDescricaoOptions = (options, selectedValue = "") => {
  if (!(itemEditDescricao instanceof HTMLSelectElement)) return;
  const menu = itemEditDescricaoDropdown?.menu;
  const trigger = itemEditDescricaoDropdown?.trigger;
  const normalized = normalizeDescricaoOptions(options);
  itemEditDescricao.replaceChildren();
  const placeholder = document.createElement("option");
  placeholder.value = "";
  placeholder.textContent = "Selecione";
  placeholder.disabled = true;
  itemEditDescricao.appendChild(placeholder);
  if (menu) menu.replaceChildren();
  normalized.forEach((label) => {
    const option = document.createElement("option");
    option.value = label;
    option.textContent = label;
    itemEditDescricao.appendChild(option);
    if (menu) {
      const button = document.createElement("button");
      button.className = "custom-select-option";
      button.type = "button";
      button.setAttribute("role", "option");
      button.dataset.value = label;
      button.textContent = label;
      menu.appendChild(button);
    }
  });
  const selected = normalized.includes(selectedValue) ? selectedValue : "";
  itemEditDescricao.value = selected;
  itemEditDescricao.disabled = normalized.length === 0;
  itemEditDescricao.required = normalized.length > 0;
  if (trigger) trigger.disabled = normalized.length === 0;
  if (menu) menu.classList.toggle("is-scroll", normalized.length > 10);
  itemEditDescricaoDropdown?.syncFromSelect();
};

const loadItemEditDescricaoOptions = async (apiTipo) => {
  if (descricaoOptionsCache.has(apiTipo)) return descricaoOptionsCache.get(apiTipo);
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return [];
  }
  const response = await fetch(`/api/v1/itens/descricoes?tipo=${encodeURIComponent(apiTipo)}`, {
    credentials: "same-origin",
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (response.status === 401) {
    window.location.href = "/login";
    return [];
  }
  if (!response.ok) throw new Error("Falha ao carregar descrições.");
  const options = normalizeDescricaoOptions(await response.json());
  descricaoOptionsCache.set(apiTipo, options);
  return options;
};

const updateItemEditDescricaoByTipo = async (tipo, selectedValue = "") => {
  const requestSequence = ++itemEditDescricaoRequestSequence;
  const normalizedTipo = String(tipo || "").toUpperCase();
  if (normalizedTipo === EXTRATO_BANCARIO_TYPE) {
    renderItemEditDescricaoOptions(EXTRATO_BANCARIO_DESCRICOES, selectedValue);
    return;
  }
  renderItemEditDescricaoOptions([], "");
  if (normalizedTipo !== "RECEITA" && normalizedTipo !== "DESPESA") return;
  try {
    const options = await loadItemEditDescricaoOptions(normalizedTipo);
    if (requestSequence !== itemEditDescricaoRequestSequence) return;
    renderItemEditDescricaoOptions(options, selectedValue);
  } catch (error) {
    if (requestSequence !== itemEditDescricaoRequestSequence) return;
    setItemEditError(error instanceof Error ? error.message : "Falha ao carregar descrições.");
  }
};

const closeItemEditModal = () => {
  pendingItemEditId = null;
  if (!itemEditOverlay) return;
  itemEditOverlay.classList.remove("is-visible");
  itemEditOverlay.setAttribute("aria-hidden", "true");
  setItemEditError();
};

const openItemEditModal = async (id) => {
  if (
    !itemEditOverlay ||
    !itemEditValor ||
    !itemEditTipo ||
    !itemEditData ||
    !itemEditDescricao ||
    !itemEditRazao ||
    !itemEditCnpjCpf ||
    !itemEditObservacao
  ) {
    return;
  }
  if (!state.userRolesReady) {
    await loadAndApplyCurrentUserRoles();
  }
  const readOnly = isItemEditReadOnlyUser();
  pendingItemEditId = id;
  setItemEditError();
  setItemEditReadOnly(readOnly);
  itemEditOverlay.classList.add("is-visible");
  itemEditOverlay.setAttribute("aria-hidden", "false");
  try {
    const item = await fetchItemById(id);
    if (pendingItemEditId !== id) return;
    itemEditValor.value = formatItemEditMoneyFromValue(item?.valor);
    itemEditTipo.value = item?.tipo === "DESPESA" ? "DESPESA" : "RECEITA";
    itemEditTipoDropdown?.syncFromSelect();
    await updateItemEditDescricaoByTipo(itemEditTipo.value, String(item?.descricao || ""));
    if (pendingItemEditId !== id) return;
    await initItemEditDatePicker();
    const formattedDate = formatItemEditDateFromIso(item?.data);
    itemEditData.value = formattedDate;
    itemEditDatePicker?.setDate(formattedDate, false, "d/m/Y");
    itemEditRazao.value = String(item?.razaoSocialNome || "");
    itemEditCnpjCpf.value = String(item?.cnpjCpf || "");
    itemEditObservacao.value = String(item?.observacao || "");
    setItemEditReadOnly(readOnly);
    if (!readOnly) itemEditValor.focus();
  } catch (error) {
    if (pendingItemEditId !== id) return;
    setItemEditError(error instanceof Error ? error.message : "Falha ao carregar comprovante.");
  }
};

const patchItemDados = async (id, payload) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return null;
  }
  const csrfToken = await ensureCsrfToken(true);
  const response = await fetch(`/api/v1/itens/${id}/dados`, {
    method: "PATCH",
    credentials: "same-origin",
    redirect: "manual",
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      Accept: "application/json",
      "X-CSRF-TOKEN": csrfToken,
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(payload),
  });
  const isRedirect =
    response.type === "opaqueredirect" ||
    (typeof response.status === "number" && response.status >= 300 && response.status < 400) ||
    response.redirected;
  if (isRedirect || response.status === 401) {
    window.location.href = "/login";
    throw new Error("Sessão expirada. Faça login novamente.");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, "Falha ao atualizar comprovante."));
  }
  return response.json();
};

const saveItemEdit = async (event) => {
  event.preventDefault();
  if (isItemEditReadOnlyUser()) return;
  if (
    !pendingItemEditId ||
    !itemEditForm ||
    !itemEditValor ||
    !itemEditTipo ||
    !itemEditData ||
    !itemEditDescricao ||
    !itemEditRazao ||
    !itemEditCnpjCpf ||
    !itemEditObservacao
  ) {
    return;
  }
  const valor = itemEditMoneyToDecimal(itemEditValor.value);
  if (valor <= 0) {
    itemEditValor.setCustomValidity("Informe um valor maior que zero.");
  } else if (valor > 10_000_000) {
    itemEditValor.setCustomValidity("O valor não pode ultrapassar R$ 10.000.000,00.");
  } else {
    itemEditValor.setCustomValidity("");
  }
  const date = parseItemEditDate(itemEditData.value);
  if (!date) {
    itemEditData.setCustomValidity("Informe uma data válida.");
  } else if (date > todayMidnight()) {
    itemEditData.setCustomValidity("A data não pode ser maior que a data atual.");
  } else {
    itemEditData.setCustomValidity("");
  }
  if (!itemEditForm.reportValidity()) return;
  try {
    setItemEditError();
    if (itemEditSave) {
      itemEditSave.disabled = true;
      itemEditSave.textContent = "Salvando...";
    }
    await patchItemDados(pendingItemEditId, {
      valor,
      tipo: itemEditTipo.value === EXTRATO_BANCARIO_TYPE ? "RECEITA" : itemEditTipo.value,
      data: itemEditDateToIso(itemEditData.value),
      descricao: itemEditDescricao.value,
      razaoSocialNome: itemEditRazao.value.trim(),
      cnpjCpf: itemEditCnpjCpf.value.trim(),
      observacao: itemEditObservacao.value.trim(),
    });
    closeItemEditModal();
    await loadItems();
  } catch (error) {
    setItemEditError(error instanceof Error ? error.message : "Falha ao atualizar comprovante.");
  } finally {
    if (itemEditSave) {
      itemEditSave.disabled = false;
      itemEditSave.textContent = "Salvar";
    }
  }
};

const closeObservacaoModal = () => {
  pendingObservacaoItemId = null;
  if (!observacaoOverlay) return;
  observacaoOverlay.classList.remove("is-visible");
  observacaoOverlay.setAttribute("aria-hidden", "true");
  if (observacaoContent) {
    observacaoContent.value = "";
    observacaoContent.readOnly = true;
  }
  observacaoIsEditing = false;
  setObservacaoSaveVisible(false);
};

const patchObservacao = async (id, observacao) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return null;
  }

  const csrfToken = await ensureCsrfToken(true);
  const response = await fetch(`/api/v1/itens/${id}/observacao`, {
    method: "PATCH",
    credentials: "same-origin",
    // Avoid redirect loops (e.g. auth redirect) turning into ERR_TOO_MANY_REDIRECTS.
    redirect: "manual",
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      Accept: "application/json",
      "X-CSRF-TOKEN": csrfToken,
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ observacao }),
  });

  const isRedirect =
    response.type === "opaqueredirect" ||
    (typeof response.status === "number" && response.status >= 300 && response.status < 400) ||
    response.redirected;
  if (isRedirect) {
    window.location.href = "/login";
    throw new Error("Sessão expirada. Faça login novamente.");
  }

  if (!response.ok) {
    const message = await extractErrorMessage(response, "Falha ao salvar observação.");
    throw new Error(message);
  }

  return response.json();
};

const patchVerificacao = async (id, verificado) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return null;
  }

  const executePatch = async (csrfToken) =>
    fetch(`/api/v1/itens/${id}/verificacao`, {
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

  let csrfToken = await ensureCsrfToken(true);
  let response = await executePatch(csrfToken);
  if (response.status === 403) {
    csrfToken = await ensureCsrfToken(true);
    response = await executePatch(csrfToken);
  }

  const isRedirect =
    response.type === "opaqueredirect" ||
    (typeof response.status === "number" && response.status >= 300 && response.status < 400) ||
    response.redirected;
  if (isRedirect) {
    window.location.href = "/login";
    throw new Error("Sessão expirada. Faça login novamente.");
  }

  if (!response.ok) {
    const message = await extractErrorMessage(response, "Falha ao atualizar verificação.");
    throw new Error(message);
  }

  return response.json();
};

const setObservacaoSaveVisible = (visible) => {
  if (!observacaoSave) return;
  setButtonVisibleSmooth(observacaoSave, visible);
};

const startObservacaoEdit = () => {
  if (!observacaoContent) return;
  observacaoIsEditing = true;
  observacaoContent.readOnly = false;
  observacaoContent.focus();
  // Put cursor at end.
  const len = observacaoContent.value.length;
  observacaoContent.setSelectionRange(len, len);
  setObservacaoSaveVisible(true);
};

const saveObservacao = async () => {
  if (!pendingObservacaoItemId || !observacaoContent) return;
  const value = String(observacaoContent.value || "").trim();
  try {
    if (observacaoSave) {
      observacaoSave.disabled = true;
      observacaoSave.textContent = "Salvando...";
    }
    const updated = await patchObservacao(pendingObservacaoItemId, value);
    observacaoContent.value = updated?.observacao ?? value;
    observacaoContent.readOnly = true;
    observacaoIsEditing = false;
    setObservacaoSaveVisible(false);
  } catch (error) {
    showListState(error instanceof Error ? error.message : "Falha ao salvar observação.");
  } finally {
    if (observacaoSave) {
      observacaoSave.disabled = false;
      observacaoSave.textContent = "Salvar";
    }
    // Guarantee UI consistency even if something else toggled `hidden`.
    if (!observacaoIsEditing) {
      setObservacaoSaveVisible(false);
    }
  }
};

const fetchItemById = async (id) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return null;
  }
  const response = await fetch(`/api/v1/itens/${id}`, {
    method: "GET",
    cache: "no-store",
    credentials: "same-origin",
    redirect: "manual",
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
  });
  const isRedirect =
    response.type === "opaqueredirect" ||
    (typeof response.status === "number" && response.status >= 300 && response.status < 400) ||
    response.redirected;
  if (isRedirect || response.status === 401) {
    window.location.href = "/login";
    throw new Error("Sessão expirada. Faça login novamente.");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, "Falha ao carregar dados do pagamento."));
  }
  return response.json();
};

const HISTORICO_ACAO_LABELS = {
  CRIADO: "Comprovante criado",
  ALTERADO: "Comprovante alterado",
  OBSERVACAO_ALTERADA: "Observação alterada",
  VERIFICACAO_ALTERADA: "Verificação alterada",
  PAGAMENTO_ALTERADO: "Pagamento alterado",
  ARQUIVO_INCLUIDO: "Arquivo incluído",
  ARQUIVO_REMOVIDO: "Arquivo removido",
  EXCLUIDO: "Comprovante excluído",
};

const HISTORICO_CAMPO_LABELS = {
  valor: "Valor",
  data: "Data",
  horarioCriacao: "Data de inserção",
  tipo: "Tipo",
  role: "Campanha",
  descricao: "Descrição",
  tipoDocumento: "Tipo de documento",
  numeroDocumento: "Número do documento",
  razaoSocialNome: "Razão social / Nome",
  cnpjCpf: "CNPJ / CPF",
  observacao: "Observação",
  verificado: "Verificação",
  arquivos: "Arquivos",
  pagamento: "Pagamento",
};

const formatHistoricoDocument = (value) => {
  const documentNumber = String(value || "").trim();
  if (!documentNumber) return "Não informado";
  const digits = documentNumber.replace(/\D/g, "");
  if (!digits) return "Valor protegido";
  if (digits.length <= 4) return documentNumber;
  return `${"•".repeat(Math.max(digits.length - 4, 0))}${digits.slice(-4)}`;
};

const formatHistoricoFiles = (files) => {
  if (!Array.isArray(files) || files.length === 0) return "Nenhum arquivo";
  const names = files
    .map((file) => extractDisplayFileName(file?.nome))
    .filter(Boolean);
  return names.length > 0 ? names.join(", ") : "Nenhum arquivo";
};

const formatHistoricoPayment = (payment) => {
  if (!payment || typeof payment !== "object" || Array.isArray(payment)) {
    return "Não informado";
  }
  const formLabel = payment.forma === "PARCELADO" ? "A prazo" : "À vista";
  const installments = Array.isArray(payment.parcelas) ? payment.parcelas : [];
  if (installments.length === 0) return formLabel;
  const installmentText = installments
    .map((installment) => {
      const number = Number(installment?.numero);
      const label = Number.isFinite(number) && number > 0 ? `${number}ª parcela` : "Parcela";
      const value = formatCurrency(installment?.valor);
      const status = installment?.paga === true ? "paga" : "pendente";
      const account = PAGAMENTO_CONTAS_ORIGEM_LABELS[installment?.contaOrigem] || "";
      const files = formatHistoricoFiles(installment?.arquivos);
      const accountText = account ? `, ${account}` : "";
      return `${label}: ${value}, ${status}${accountText}; arquivos: ${files}`;
    })
    .join(" | ");
  return `${formLabel}. ${installmentText}`;
};

const formatHistoricoValue = (field, value) => {
  if (value === null || value === undefined || value === "") return "Não informado";
  switch (field) {
    case "valor":
      return formatCurrency(value);
    case "data":
      return /^\d{4}-\d{2}-\d{2}$/.test(String(value)) ? formatDate(value) : String(value);
    case "horarioCriacao":
      return formatDateTime(value);
    case "tipo":
      return value === "RECEITA" ? "Receita" : value === "DESPESA" ? "Despesa" : String(value);
    case "descricao":
      return formatDescricao(value);
    case "verificado":
      return value === true ? "Verificado" : "Não verificado";
    case "cnpjCpf":
      return formatHistoricoDocument(value);
    case "arquivos":
      return formatHistoricoFiles(value);
    case "pagamento":
      return formatHistoricoPayment(value);
    default:
      return String(value);
  }
};

const createHistoricoText = (className, value) => {
  const node = document.createElement("span");
  node.className = className;
  node.textContent = value;
  return node;
};

const renderHistoricoMessage = (message, isError = false) => {
  if (!historicoContent) return;
  const content = document.createElement("p");
  content.className = isError ? "historico-message is-error" : "historico-message";
  content.textContent = message;
  historicoContent.replaceChildren(content);
};

const renderHistorico = (events) => {
  if (!historicoContent) return;
  if (!Array.isArray(events) || events.length === 0) {
    renderHistoricoMessage("Nenhum evento de histórico disponível.");
    return;
  }

  const list = document.createElement("ol");
  list.className = "historico-list";
  events.forEach((event) => {
    const item = document.createElement("li");
    item.className = "historico-event";
    const header = document.createElement("div");
    header.className = "historico-event-header";
    const action = document.createElement("h3");
    action.textContent = HISTORICO_ACAO_LABELS[event?.acao] || "Alteração registrada";
    const metadata = document.createElement("p");
    const actor = String(event?.ator || "Sistema").trim() || "Sistema";
    metadata.textContent = `${actor} · ${formatDateTime(event?.ocorridoEm)}`;
    header.append(action, metadata);
    item.appendChild(header);

    const changes = Object.entries(event?.alteracoes || {}).filter(([field]) =>
      Object.hasOwn(HISTORICO_CAMPO_LABELS, field),
    );
    if (changes.length === 0) {
      item.appendChild(createHistoricoText("historico-no-change", "Nenhum campo detalhado."));
    } else {
      const changeList = document.createElement("ul");
      changeList.className = "historico-changes";
      changes.forEach(([field, change]) => {
        const changeItem = document.createElement("li");
        const label = document.createElement("strong");
        label.textContent = HISTORICO_CAMPO_LABELS[field];
        const values = document.createElement("div");
        values.className = "historico-change-values";
        values.append(
          createHistoricoText(
            "historico-value historico-value-before",
            `Antes: ${formatHistoricoValue(field, change?.antes)}`,
          ),
          createHistoricoText(
            "historico-value historico-value-after",
            `Depois: ${formatHistoricoValue(field, change?.depois)}`,
          ),
        );
        changeItem.append(label, values);
        changeList.appendChild(changeItem);
      });
      item.appendChild(changeList);
    }
    list.appendChild(item);
  });
  historicoContent.replaceChildren(list);
};

const fetchItemHistorico = async (id) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return [];
  }
  const response = await fetch(`/api/v1/itens/${encodeURIComponent(id)}/auditoria`, {
    method: "GET",
    cache: "no-store",
    credentials: "same-origin",
    redirect: "manual",
    headers: {
      Accept: "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
  });
  const isRedirect =
    response.type === "opaqueredirect" ||
    (typeof response.status === "number" && response.status >= 300 && response.status < 400) ||
    response.redirected;
  if (isRedirect || response.status === 401) {
    window.location.href = "/login";
    throw new Error("Sessão expirada. Faça login novamente.");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, "Falha ao carregar histórico."));
  }
  return response.json();
};

const closeHistoricoModal = () => {
  pendingHistoricoItemId = null;
  if (!historicoOverlay) return;
  historicoOverlay.classList.remove("is-visible");
  historicoOverlay.setAttribute("aria-hidden", "true");
  if (historicoContent) historicoContent.replaceChildren();
};

const openHistoricoModal = async (id) => {
  pendingHistoricoItemId = id;
  if (!historicoOverlay || !historicoContent) return;
  renderHistoricoMessage("Carregando histórico...");
  historicoOverlay.classList.add("is-visible");
  historicoOverlay.setAttribute("aria-hidden", "false");
  try {
    const events = await fetchItemHistorico(id);
    if (pendingHistoricoItemId !== id) return;
    renderHistorico(events);
  } catch (error) {
    if (pendingHistoricoItemId !== id) return;
    renderHistoricoMessage(
      error instanceof Error ? error.message : "Falha ao carregar histórico.",
      true,
    );
  }
};

const buildPagamentoParcelas = (item, formaPagamento, quantidadeParcelas, parcelasSalvas = []) => {
  const savedByNumber = new Map(
    Array.isArray(parcelasSalvas)
      ? parcelasSalvas.map((parcela) => [Number(parcela?.numero || 0), parcela])
      : [],
  );
  const valores = calculateInstallmentValues(item?.valor, quantidadeParcelas);
  return valores.map((valorParcela, index) => {
    const numero = index + 1;
    const parcelaSalva = savedByNumber.get(numero);
    const arquivosSalvos = Array.isArray(parcelaSalva?.arquivosComprovantes)
      ? parcelaSalva.arquivosComprovantes
      : [];
    const arquivos = arquivosSalvos.length
      ? arquivosSalvos.map((arquivo) => ({
          id: arquivo?.id || null,
          name: extractDisplayFileName(arquivo?.nomeArquivo),
          file: null,
          legacy: !arquivo?.id,
        }))
      : parcelaSalva?.nomeArquivoComprovante
        ? [
            {
              id: null,
              name: extractDisplayFileName(parcelaSalva.nomeArquivoComprovante),
              file: null,
              legacy: true,
            },
          ]
        : [];
    return {
      numero,
      valorParcela: Number(parcelaSalva?.valorParcela ?? valorParcela),
      paga: Boolean(parcelaSalva?.paga),
      contaOrigemPagamento: parcelaSalva?.contaOrigemPagamento || "",
      arquivos,
      arquivosRemovidos: [],
      removerArquivoLegado: false,
    };
  });
};

const normalizePagamentoState = (item) => {
  const savedPagamento = item?.pagamento;
  const savedCount = Math.min(Math.max(Number(savedPagamento?.quantidadeParcelas || 1), 1), 4);
  const formaPagamento =
    savedPagamento?.formaPagamento === "PARCELADO" && savedCount > 1 ? "PARCELADO" : "AVISTA";
  const quantidadeParcelas = formaPagamento === "PARCELADO" ? Math.max(savedCount, 2) : 1;
  return {
    itemId: item?.id || null,
    valor: Number(item?.valor || 0),
    razaoSocialNome: formatText(item?.razaoSocialNome),
    hasSavedPaymentData:
      Array.isArray(savedPagamento?.parcelas) &&
      savedPagamento.parcelas.some(
        (parcela) =>
          parcela?.paga === true ||
          Boolean(parcela?.nomeArquivoComprovante) ||
          (Array.isArray(parcela?.arquivosComprovantes) && parcela.arquivosComprovantes.length > 0),
      ),
    formaPagamento,
    quantidadeParcelas,
    parcelas: buildPagamentoParcelas(
      item,
      formaPagamento,
      quantidadeParcelas,
      savedPagamento?.parcelas,
    ),
  };
};

const recalculatePagamentoParcelas = (nextCount) => {
  if (!pagamentoState) return;
  const previousByNumber = new Map(
    pagamentoState.parcelas.map((parcela) => [Number(parcela.numero), parcela]),
  );
  const valores = calculateInstallmentValues(pagamentoState.valor, nextCount);
  pagamentoState.quantidadeParcelas = nextCount;
  pagamentoState.parcelas = valores.map((valorParcela, index) => {
    const numero = index + 1;
    const previous = previousByNumber.get(numero);
    return {
      numero,
      valorParcela,
      paga: previous?.paga === true,
      contaOrigemPagamento: previous?.contaOrigemPagamento || "",
      arquivos: previous?.arquivos || [],
      arquivosRemovidos: previous?.arquivosRemovidos || [],
      removerArquivoLegado: previous?.removerArquivoLegado === true,
    };
  });
};

const resetPagamentoParcelas = (nextCount) => {
  if (!pagamentoState) return;
  const valores = calculateInstallmentValues(pagamentoState.valor, nextCount);
  pagamentoState.quantidadeParcelas = nextCount;
  pagamentoState.parcelas = valores.map((valorParcela, index) => ({
    numero: index + 1,
    valorParcela,
    paga: false,
    contaOrigemPagamento: "",
    arquivos: [],
    arquivosRemovidos: [],
    removerArquivoLegado: false,
  }));
};

const updatePagamentoTotalPago = () => {
  if (!pagamentoTotalPago || !pagamentoState) return;
  const total = pagamentoState.parcelas
    .filter((parcela) => parcela.paga)
    .reduce((sum, parcela) => sum + Number(parcela.valorParcela || 0), 0);
  pagamentoTotalPago.value = formatCurrency(total);
};

const setPagamentoValidation = (message = "") => {
  if (!pagamentoValidation) return;
  pagamentoValidation.textContent = message;
  pagamentoValidation.hidden = !message;
};

const getPagamentoParcelaLabel = (parcela) =>
  pagamentoState?.formaPagamento === "AVISTA" ? "pagamento à vista" : `${parcela.numero}ª parcela`;

const getPagamentoContaOrigemLabel = (contaOrigemPagamento) =>
  PAGAMENTO_CONTAS_ORIGEM_LABELS[contaOrigemPagamento] || "Conta de origem";

const validatePagamentoBeforeSave = () => {
  if (!pagamentoState) return "Não foi possível validar o pagamento.";
  for (const parcela of pagamentoState.parcelas) {
    if (!parcela.paga) continue;
    const parcelaLabel = getPagamentoParcelaLabel(parcela);
    if (!Array.isArray(parcela.arquivos) || parcela.arquivos.length === 0) {
      return `Anexe ao menos um PDF no ${parcelaLabel}.`;
    }
    if (!PAGAMENTO_CONTAS_ORIGEM.has(parcela.contaOrigemPagamento)) {
      return `Selecione a conta de origem do ${parcelaLabel}.`;
    }
    if (!Number.isFinite(Number(parcela.valorParcela)) || Number(parcela.valorParcela) <= 0) {
      return `Informe valor maior que zero no ${parcelaLabel}.`;
    }
  }
  return "";
};

const formatInstallmentValue = (value) =>
  Number(value || 0).toLocaleString("pt-BR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

const parseInstallmentValue = (value) => {
  const digits = String(value || "").replace(/\D/g, "").replace(/^0+(?=\d)/, "");
  if (!digits) return 0;
  if (digits.length > 9) return MAX_INSTALLMENT_VALUE;
  return Math.min(Number(digits) / 100, MAX_INSTALLMENT_VALUE);
};

const applyInstallmentValueMask = (input) => {
  if (!pagamentoState || pagamentoState.formaPagamento !== "PARCELADO") return;
  const numero = Number(input.dataset.parcela || 0);
  const parcela = pagamentoState.parcelas.find((entry) => entry.numero === numero);
  if (!parcela) return;
  const valor = parseInstallmentValue(input.value);
  parcela.valorParcela = valor;
  input.value = formatInstallmentValue(valor);
  updatePagamentoTotalPago();
};

const renderPagamentoParcelas = () => {
  if (!pagamentoParcelas || !pagamentoState) return;
  pagamentoParcelas.innerHTML = "";
  pagamentoParcelas.classList.toggle("is-scrollable", pagamentoState.parcelas.length >= 3);
  pagamentoState.parcelas.forEach((parcela) => {
    const row = document.createElement("article");
    row.className = "pagamento-row";
    row.dataset.parcela = String(parcela.numero);
    row.innerHTML = `
      <div class="pagamento-row-main">
        <label class="pagamento-row-check">
          <input type="checkbox" class="pagamento-check" data-parcela="${parcela.numero}" ${
            parcela.paga ? "checked" : ""
          } />
          <span>Paga</span>
        </label>
        <div class="pagamento-row-meta">
          <h3 class="pagamento-row-title">${
            pagamentoState.formaPagamento === "AVISTA" ? "Pagamento à vista" : `${parcela.numero}ª parcela`
          }</h3>
          <p class="pagamento-row-hint">${
            parcela.paga ? "Comprovantes em PDF podem ser anexados." : "Marque como paga para anexar PDFs."
          }</p>
          <div class="pagamento-pdf-list" data-parcela="${parcela.numero}"></div>
        </div>
      </div>
      <div class="pagamento-row-actions">
        <label class="pagamento-row-value">
          <span class="pagamento-value-prefix">R$</span>
          <input
            class="pagamento-valor-input"
            type="text"
            inputmode="numeric"
            autocomplete="off"
            data-parcela="${parcela.numero}"
            value="${formatInstallmentValue(parcela.valorParcela)}"
            ${pagamentoState.formaPagamento === "PARCELADO" ? "" : "readonly"}
          />
        </label>
        <div class="pagamento-origin-select" data-parcela="${parcela.numero}">
          <button
            class="pagamento-origin-trigger"
            type="button"
            aria-haspopup="listbox"
            aria-expanded="false"
            aria-label="Conta de origem da ${parcela.numero}ª parcela"
            ${parcela.paga ? "" : "disabled"}
          >${getPagamentoContaOrigemLabel(parcela.contaOrigemPagamento)}</button>
          <div class="pagamento-origin-menu" role="listbox" hidden>
            <button class="pagamento-origin-option" type="button" role="option" data-value="CONTA_DC">CONTA DC</button>
            <button class="pagamento-origin-option" type="button" role="option" data-value="CONTA_FEFC">CONTA FEFC</button>
            <button class="pagamento-origin-option" type="button" role="option" data-value="CONTA_FP">CONTA FP</button>
          </div>
        </div>
        <button
          class="btn btn-secondary pagamento-anexo-btn"
          type="button"
          data-parcela="${parcela.numero}"
          ${parcela.paga ? "" : "disabled"}
        >Anexar PDFs</button>
        <input
          class="pagamento-anexo-input"
          type="file"
          data-parcela="${parcela.numero}"
          accept=".pdf,application/pdf"
          multiple
          hidden
        />
      </div>
    `;
    const pdfList = row.querySelector(".pagamento-pdf-list");
    if (pdfList instanceof HTMLElement) {
      parcela.arquivos.forEach((arquivo, index) => {
        const thumbnail = document.createElement("div");
        thumbnail.className = "pagamento-pdf-thumb";
        thumbnail.title = arquivo.name || "PDF";
        thumbnail.innerHTML = `
          <img src="/assets/img/pdf-thumbnail-20260723-1.png" alt="" aria-hidden="true" />
          <button
            class="pagamento-pdf-remove"
            type="button"
            data-parcela="${parcela.numero}"
            data-arquivo-index="${index}"
            aria-label="Remover PDF"
            title="Remover após salvar"
          >×</button>
        `;
        pdfList.appendChild(thumbnail);
      });
      if (parcela.arquivos.length === 0) {
        const empty = document.createElement("p");
        empty.className = "pagamento-row-file";
        empty.textContent = "Nenhum PDF anexado.";
        pdfList.appendChild(empty);
      }
    }
    pagamentoParcelas.appendChild(row);
  });
  updatePagamentoTotalPago();
};

const syncPagamentoToolbar = () => {
  if (!pagamentoState) return;
  pagamentoFormaInputs.forEach((input) => {
    if (!(input instanceof HTMLInputElement)) return;
    input.checked = input.value === pagamentoState.formaPagamento;
    const isLocked = pagamentoState.hasSavedPaymentData && !input.checked;
    input.disabled = isLocked;
    const option = input.closest(".pagamento-mode-option");
    if (option instanceof HTMLElement) {
      option.title = isLocked
        ? "Desmarque pagamentos, exclua os PDFs e salve antes de trocar a modalidade."
        : "";
    }
  });
  if (pagamentoQuantidade) {
    pagamentoQuantidade.disabled = pagamentoState.formaPagamento !== "PARCELADO";
    const quantidadeCampo = pagamentoQuantidade.closest(".pagamento-count-field");
    if (quantidadeCampo instanceof HTMLElement) {
      quantidadeCampo.hidden = pagamentoState.formaPagamento !== "PARCELADO";
    }
    pagamentoQuantidade.value = String(
      pagamentoState.formaPagamento === "PARCELADO" ? pagamentoState.quantidadeParcelas : 2,
    );
  }
};

const renderPagamentoModal = () => {
  if (!pagamentoState) return;
  if (pagamentoTitle) {
    pagamentoTitle.textContent = pagamentoState.razaoSocialNome;
  }
  if (pagamentoSubtitle) {
    pagamentoSubtitle.textContent = `Valor do item: ${formatCurrency(pagamentoState.valor)}`;
  }
  syncPagamentoToolbar();
  renderPagamentoParcelas();
};

const closePagamentoModal = () => {
  pendingPagamentoItemId = null;
  pagamentoState = null;
  setPagamentoValidation();
  if (!pagamentoOverlay) return;
  pagamentoOverlay.classList.remove("is-visible");
  pagamentoOverlay.setAttribute("aria-hidden", "true");
  if (pagamentoTitle) pagamentoTitle.textContent = "Razão social / Nome";
  if (pagamentoSubtitle) pagamentoSubtitle.textContent = "Valor do item";
  if (pagamentoParcelas) {
    pagamentoParcelas.innerHTML = "";
    pagamentoParcelas.classList.remove("is-scrollable");
  }
  if (pagamentoTotalPago) pagamentoTotalPago.value = formatCurrency(0);
  if (pagamentoSave) {
    pagamentoSave.disabled = false;
    pagamentoSave.textContent = "Salvar";
  }
};

const showPagamentoSuccess = () => {
  if (!pagamentoSuccessOverlay) return;
  pagamentoSuccessOverlay.classList.add("is-visible");
  pagamentoSuccessOverlay.setAttribute("aria-hidden", "false");
};

const closePagamentoSuccess = () => {
  if (!pagamentoSuccessOverlay) return;
  pagamentoSuccessOverlay.classList.remove("is-visible");
  pagamentoSuccessOverlay.setAttribute("aria-hidden", "true");
};

const closePagamentoOriginMenus = () => {
  if (!pagamentoParcelas) return;
  pagamentoParcelas.querySelectorAll(".pagamento-origin-select").forEach((select) => {
    const trigger = select.querySelector(".pagamento-origin-trigger");
    const menu = select.querySelector(".pagamento-origin-menu");
    select.classList.remove("is-open");
    if (trigger instanceof HTMLButtonElement) trigger.setAttribute("aria-expanded", "false");
    if (menu instanceof HTMLElement) menu.hidden = true;
  });
};

const openPagamentoModal = async (id) => {
  pendingPagamentoItemId = id;
  hideListState();
  if (!pagamentoOverlay) return;
  pagamentoOverlay.classList.add("is-visible");
  pagamentoOverlay.setAttribute("aria-hidden", "false");
  if (pagamentoTitle) pagamentoTitle.textContent = "Razão social / Nome";
  if (pagamentoSubtitle) pagamentoSubtitle.textContent = "Carregando pagamento...";
  if (pagamentoParcelas) {
    pagamentoParcelas.innerHTML =
      '<article class="pagamento-row"><div class="pagamento-row-main"><div class="pagamento-row-meta"><h3 class="pagamento-row-title">Carregando...</h3></div></div></article>';
  }
  try {
    const item = await fetchItemById(id);
    if (pendingPagamentoItemId !== id) return;
    pagamentoState = normalizePagamentoState(item);
    renderPagamentoModal();
  } catch (error) {
    closePagamentoModal();
    showListState(error instanceof Error ? error.message : "Falha ao carregar pagamento.");
  }
};

const patchPagamento = async (id, payload) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return null;
  }
  const csrfToken = await ensureCsrfToken(true);
  const response = await fetch(`/api/v1/itens/${id}/pagamento`, {
    method: "PATCH",
    credentials: "same-origin",
    redirect: "manual",
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      Accept: "application/json",
      "X-CSRF-TOKEN": csrfToken,
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify(payload),
  });
  const isRedirect =
    response.type === "opaqueredirect" ||
    (typeof response.status === "number" && response.status >= 300 && response.status < 400) ||
    response.redirected;
  if (isRedirect || response.status === 401) {
    window.location.href = "/login";
    throw new Error("Sessão expirada. Faça login novamente.");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, "Falha ao salvar pagamento."));
  }
  return response.json();
};

const buildPagamentoPayload = async () => {
  if (!pagamentoState) return null;
  const parcelas = [];
  for (const parcela of pagamentoState.parcelas) {
    const arquivosPdf = [];
    const nomesArquivos = [];
    for (const arquivo of parcela.arquivos.filter((entry) => entry.file instanceof File)) {
      const picked = validateUploadFilesOrThrow([arquivo.file]);
      const [arquivoBase64] = await encodeFilesAsBase64(picked);
      arquivosPdf.push(arquivoBase64);
      nomesArquivos.push(picked[0].name);
    }
    parcelas.push({
      numero: parcela.numero,
      paga: parcela.paga === true,
      contaOrigemPagamento: parcela.paga ? parcela.contaOrigemPagamento || null : null,
      valorParcela:
        pagamentoState.formaPagamento === "PARCELADO" ? Number(parcela.valorParcela || 0) : null,
      arquivosPdf,
      nomesArquivos,
      arquivosRemovidos: parcela.arquivosRemovidos,
      removerArquivoLegado: parcela.removerArquivoLegado === true,
    });
  }
  return {
    formaPagamento: pagamentoState.formaPagamento,
    quantidadeParcelas:
      pagamentoState.formaPagamento === "PARCELADO" ? pagamentoState.quantidadeParcelas : 1,
    parcelas,
  };
};

const savePagamento = async () => {
  if (!pendingPagamentoItemId || !pagamentoState) return;
  const validationMessage = validatePagamentoBeforeSave();
  if (validationMessage) {
    setPagamentoValidation(validationMessage);
    return;
  }
  setPagamentoValidation();
  try {
    if (pagamentoSave) {
      pagamentoSave.disabled = true;
      pagamentoSave.textContent = "Salvando...";
    }
    const payload = await buildPagamentoPayload();
    const updated = await patchPagamento(pendingPagamentoItemId, payload);
    pagamentoState = normalizePagamentoState(updated);
    const idx = state.items.findIndex((entry) => entry.id === pendingPagamentoItemId);
    if (idx >= 0) {
      state.items[idx] = { ...state.items[idx], ...updated };
    }
    renderPagamentoModal();
    showPagamentoSuccess();
  } catch (error) {
    const message = error instanceof Error ? error.message : "Falha ao salvar pagamento.";
    showListState(message);
  } finally {
    if (pagamentoSave) {
      pagamentoSave.disabled = false;
      pagamentoSave.textContent = "Salvar";
    }
  }
};

const loadItemArquivos = async (id) => {
  if (!uploadFiles) return;
  uploadFiles.innerHTML = "<li>Carregando...</li>";
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }
  const response = await fetch(`/api/v1/itens/${id}/arquivos`, {
    method: "GET",
    credentials: "same-origin",
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });
  if (!response.ok) {
    uploadFiles.innerHTML = "<li>Falha ao carregar arquivos.</li>";
    return;
  }
  const arquivos = await response.json();
  if (!Array.isArray(arquivos) || arquivos.length === 0) {
    uploadFiles.innerHTML = "<li>Nenhum arquivo enviado.</li>";
    updateDownloadButton(id, false);
    return;
  }
  uploadFiles.innerHTML = "";
  uploadFiles.classList.toggle("is-editing", uploadIsEditing);
  updateDownloadButton(id, true);
  arquivos.forEach((arquivo) => {
    const li = document.createElement("li");
    li.className = "upload-file-item";
    li.dataset.arquivoId = arquivo.id;
    const name = arquivo.caminhoArquivoPdf
      ? arquivo.caminhoArquivoPdf.split(/[\\/]/).pop()
      : "PDF";
    const link = document.createElement("a");
    link.href = `/api/v1/itens/${id}/arquivos/${arquivo.id}`;
    link.textContent = name;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    li.appendChild(link);

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "upload-file-remove";
    remove.textContent = "×";
    remove.setAttribute("aria-label", "Remover arquivo");
    if (!uploadIsEditing) {
      remove.hidden = true;
    }
    li.appendChild(remove);

    const isPending = pendingDeleteArquivoIds.has(String(arquivo.id));
    li.classList.toggle("is-pending-delete", isPending);
    uploadFiles.appendChild(li);
  });
  updateUploadSaveVisibility();
};

const encodeFilesAsBase64 = async (files) => {
  if (!files || files.length === 0) return [];
  const array = Array.from(files);
  const encoded = [];
  for (const file of array) {
    const base64 = await new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const result = String(reader.result || "");
        const comma = result.indexOf(",");
        resolve(comma >= 0 ? result.slice(comma + 1) : result);
      };
      reader.onerror = () => reject(new Error("Falha ao ler arquivo"));
      reader.readAsDataURL(file);
    });
    encoded.push(base64);
  }
  return encoded;
};

const filesToNames = (files) =>
  files && files.length > 0 ? Array.from(files).map((file) => file.name) : [];

const createUploadErrorEntry = (fileName, message) => ({
  id: `${fileName || "arquivo"}::${message || "erro"}`,
  fileName: String(fileName || "Arquivo.pdf"),
  message: String(message || "Falha ao enviar arquivo."),
});

const clearUploadErrorEntries = () => {
  uploadErrorEntries = [];
};

const setUploadErrorEntries = (entries) => {
  uploadErrorEntries = Array.isArray(entries) ? entries.filter(Boolean) : [];
  renderSelectedFiles(uploadInput?.files);
};

const dismissUploadErrorEntry = (entryId) => {
  uploadErrorEntries = uploadErrorEntries.filter((entry) => entry.id !== entryId);
  renderSelectedFiles(uploadInput?.files);
};

const findInvalidUploadFile = (files) =>
  Array.from(files || []).find((file) => !isPdfFile(file));

const findOversizedUploadFile = (files) =>
  Array.from(files || []).find((file) => file.size > MAX_RECEIPT_SIZE_BYTES);

const sanitizeUploadFiles = (files) =>
  Array.from(files || []).filter(
    (file) => isPdfFile(file) && file.size <= MAX_RECEIPT_SIZE_BYTES,
  );

const collectUploadErrorEntries = (files) => {
  const entries = [];
  Array.from(files || []).forEach((file) => {
    if (!isPdfFile(file)) {
      entries.push(createUploadErrorEntry(file?.name, PDF_ONLY_MESSAGE));
      return;
    }
    if (file.size > MAX_RECEIPT_SIZE_BYTES) {
      entries.push(createUploadErrorEntry(file?.name, MAX_RECEIPT_SIZE_MESSAGE));
    }
  });
  return entries;
};

const validateUploadFilesOrThrow = (files) => {
  const entries = collectUploadErrorEntries(files);
  if (entries.length > 0) {
    throw Object.assign(new Error(entries[0].message), { uploadEntries: entries });
  }
  return Array.from(files || []);
};

const uploadFileKey = (file) => {
  if (!file) return "";
  return `${file.name}::${file.size}::${file.lastModified}`;
};

const mergeUploadFiles = (existing, incoming) => {
  const merged = [];
  const seen = new Set();

  const pushUnique = (file) => {
    if (!(file instanceof File)) return;
    const key = uploadFileKey(file);
    if (!key || seen.has(key)) return;
    seen.add(key);
    merged.push(file);
  };

  (existing || []).forEach(pushUnique);
  (incoming || []).forEach(pushUnique);
  return merged;
};

const setUploadInputFiles = (files) => {
  if (!uploadInput) return;
  const dataTransfer = new DataTransfer();
  (files || []).forEach((file) => dataTransfer.items.add(file));
  settingUploadFilesProgrammatically = true;
  uploadInput.files = dataTransfer.files;
  uploadInput.dispatchEvent(new Event("change", { bubbles: true }));
  settingUploadFilesProgrammatically = false;
};

const deleteArquivo = async (itemId, arquivoId) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }
  const token = await ensureCsrfToken(true);
  const response = await fetch(`/api/v1/itens/${itemId}/arquivos/${arquivoId}`, {
    method: "DELETE",
    credentials: "same-origin",
    redirect: "manual",
    headers: {
      "X-CSRF-TOKEN": token,
      Authorization: `Bearer ${accessToken}`,
    },
  });
  const isRedirect =
    response.type === "opaqueredirect" ||
    (typeof response.status === "number" && response.status >= 300 && response.status < 400) ||
    response.redirected;
  if (isRedirect) {
    window.location.href = "/login";
    throw new Error("Sessão expirada. Faça login novamente.");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, "Falha ao excluir arquivo."));
  }
};

const uploadArquivos = async (files) => {
  if (!pendingUploadItemId || !uploadInput) return;
  const effectiveFiles = files ? Array.from(files) : uploadInput.files ? Array.from(uploadInput.files) : [];
  const pdfs = validateUploadFilesOrThrow(effectiveFiles);
  if (pdfs.length === 0) {
    return [];
  }

  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }
  const token = await ensureCsrfToken(true);
  const arquivosPdf = await encodeFilesAsBase64(pdfs);
  const nomesArquivos = filesToNames(pdfs);
  const response = await fetch(`/api/v1/itens/${pendingUploadItemId}/arquivos`, {
    method: "POST",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-TOKEN": token,
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ arquivosPdf, nomesArquivos }),
  });
  if (!response.ok) {
    const message = await extractErrorMessage(response, "Falha ao enviar arquivos do comprovante.");
    throw Object.assign(new Error(message), {
      uploadEntries: pdfs.map((file) => createUploadErrorEntry(file?.name, message)),
    });
  }
  let savedArquivos = [];
  try {
    const payload = await response.json();
    if (Array.isArray(payload)) {
      savedArquivos = payload
        .map((arquivo) => arquivo?.caminhoArquivoPdf)
        .filter((value) => value && String(value).trim().length > 0);
    }
  } catch (error) {
    // Ignore JSON parse errors and keep UI state.
  }
  if (pendingUploadItemId) {
    const item = state.items.find((entry) => entry.id === pendingUploadItemId);
    if (item) {
      item.arquivosPdf = savedArquivos;
    }
    updateDownloadButton(pendingUploadItemId, savedArquivos.length > 0);
  }
  if (uploadSelected) {
    uploadSelected
      .querySelectorAll(".upload-file-card")
      .forEach((card) => card.classList.remove("is-loading"));
  }
  return savedArquivos;
};

const replaceUploadInputFiles = (files) => {
  const next = Array.isArray(files) ? files : files ? Array.from(files) : [];
  retainedUploadFiles = next;
  setUploadInputFiles(next);
};

const fileMatches = (a, b) => {
  if (!a || !b) return false;
  if (a === b) return true;
  return (
    a.name === b.name &&
    a.size === b.size &&
    a.lastModified === b.lastModified &&
    String(a.type || "") === String(b.type || "")
  );
};

const saveUploadChanges = async () => {
  if (!pendingUploadItemId) return;
  clearUploadErrorEntries();
  const newFiles = uploadInput?.files ? validateUploadFilesOrThrow(uploadInput.files) : [];
  const deleteIds = Array.from(pendingDeleteArquivoIds);
  if (newFiles.length === 0 && deleteIds.length === 0) {
    updateUploadSaveVisibility();
    return;
  }

  try {
    if (uploadSave) {
      uploadSave.disabled = true;
      uploadSave.textContent = "Salvando...";
    }
    for (const arquivoId of deleteIds) {
      await deleteArquivo(pendingUploadItemId, arquivoId);
    }
    await uploadArquivos(newFiles);

    // Reset UI state after saving (per requirement).
    pendingDeleteArquivoIds = new Set();
    uploadIsEditing = false;
    if (uploadFiles) uploadFiles.classList.remove("is-editing");
    retainedUploadFiles = [];
    if (uploadInput) uploadInput.value = "";
    if (uploadSelected) {
      uploadSelected.innerHTML = "";
      uploadSelected.classList.remove("is-grid");
    }
    updateUploadSaveVisibility();
    await loadItemArquivos(pendingUploadItemId);
  } catch (error) {
    if (error?.uploadEntries) {
      setUploadErrorEntries(error.uploadEntries);
    }
    showListState(error instanceof Error ? error.message : "Falha ao salvar alterações de arquivos.");
  } finally {
    if (uploadSave) {
      uploadSave.disabled = false;
      uploadSave.textContent = "Salvar";
    }
  }
};

const renderSelectedFiles = (files) => {
  if (!uploadSelected) return;
  uploadSelected.innerHTML = "";
  const allFiles =
    uploadInput?.files && uploadInput.files.length > 0
      ? Array.from(uploadInput.files)
      : files && files.length > 0
      ? Array.from(files)
        : [];
  const pdfs = allFiles.filter(isPdfFile);
  const visualCardsCount = pdfs.length + uploadErrorEntries.length;
  uploadSelected.classList.toggle("is-grid", visualCardsCount > 1);
  updateUploadSaveVisibility();
  if (visualCardsCount === 0) {
    uploadSelected.classList.remove("is-grid");
    return;
  }
  pdfs.forEach((file) => {
    const card = document.createElement("div");
    card.className = "upload-file-card is-loading";
    const icon = document.createElement("div");
    icon.className = "upload-file-icon";
    icon.textContent = "PDF";
    const name = document.createElement("div");
    name.className = "upload-file-name";
    name.textContent = file.name || "Arquivo.pdf";

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "upload-file-remove-selected";
    remove.textContent = "×";
    remove.setAttribute("aria-label", "Remover PDF selecionado");
    remove.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();

      const current = uploadInput?.files ? Array.from(uploadInput.files) : [];
      let removed = false;
      const kept = current.filter((entry) => {
        if (!removed && fileMatches(entry, file)) {
          removed = true;
          return false;
        }
        return true;
      });
      replaceUploadInputFiles(kept);
      renderSelectedFiles(uploadInput?.files);
    });
    card.appendChild(icon);
    card.appendChild(name);
    card.appendChild(remove);
    uploadSelected.appendChild(card);
  });
  uploadErrorEntries.forEach((entry) => {
    const card = document.createElement("div");
    card.className = "upload-file-card is-error";

    const icon = document.createElement("div");
    icon.className = "upload-file-icon is-error";
    icon.textContent = "!";

    const meta = document.createElement("div");
    meta.className = "upload-file-meta";

    const name = document.createElement("div");
    name.className = "upload-file-name";
    name.textContent = entry.fileName || "Arquivo.pdf";

    const message = document.createElement("div");
    message.className = "upload-file-message";
    message.textContent = entry.message || "Falha ao enviar arquivo.";

    const dismiss = document.createElement("button");
    dismiss.type = "button";
    dismiss.className = "upload-file-remove-selected is-error";
    dismiss.textContent = "×";
    dismiss.setAttribute("aria-label", "Dispensar erro do arquivo");
    dismiss.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      dismissUploadErrorEntry(entry.id);
    });

    meta.appendChild(name);
    meta.appendChild(message);
    card.appendChild(icon);
    card.appendChild(meta);
    card.appendChild(dismiss);
    uploadSelected.appendChild(card);
  });
};

const bindUploadDrop = () => {
  if (!uploadDrop || !uploadInput) return;
  const setFiles = (files) => {
    if (!files || files.length === 0) return;
    const picked = Array.from(files);
    setUploadErrorEntries(collectUploadErrorEntries(picked));
    const invalidFile = findInvalidUploadFile(picked);
    if (invalidFile) {
      showListState(PDF_ONLY_MESSAGE);
    }
    const oversizedFile = findOversizedUploadFile(picked);
    if (oversizedFile) {
      showListState(MAX_RECEIPT_SIZE_MESSAGE);
    }
    const validFiles = sanitizeUploadFiles(picked);
    if (validFiles.length === 0) {
      return;
    }
    const merged = mergeUploadFiles(retainedUploadFiles, validFiles);
    retainedUploadFiles = merged;
    setUploadInputFiles(merged);
  };

  uploadDrop.addEventListener("dragenter", (event) => {
    event.preventDefault();
    uploadDrop.classList.add("is-active");
  });
  uploadDrop.addEventListener("dragover", (event) => {
    event.preventDefault();
    uploadDrop.classList.add("is-active");
  });
  uploadDrop.addEventListener("dragleave", (event) => {
    event.preventDefault();
    uploadDrop.classList.remove("is-active");
  });
  uploadDrop.addEventListener("drop", (event) => {
    event.preventDefault();
    uploadDrop.classList.remove("is-active");
    setFiles(event.dataTransfer?.files);
  });
};

const toggleUploadEditMode = () => {
  uploadIsEditing = !uploadIsEditing;
  if (!uploadIsEditing) {
    pendingDeleteArquivoIds = new Set();
  }
  if (uploadFiles) {
    uploadFiles.classList.toggle("is-editing", uploadIsEditing);
    uploadFiles.querySelectorAll(".upload-file-remove").forEach((button) => {
      if (!(button instanceof HTMLButtonElement)) return;
      if (uploadIsEditing) {
        button.hidden = false;
        button.removeAttribute("hidden");
      } else {
        button.hidden = true;
        button.setAttribute("hidden", "");
      }
    });
    uploadFiles.querySelectorAll(".upload-file-item").forEach((li) => {
      if (!(li instanceof HTMLElement)) return;
      const id = li.dataset.arquivoId;
      li.classList.toggle("is-pending-delete", Boolean(id && pendingDeleteArquivoIds.has(id)));
    });
  }
  updateUploadSaveVisibility();
};

const bindUploadEditActions = () => {
  if (uploadEdit) {
    uploadEdit.addEventListener("click", toggleUploadEditMode);
  }
  if (!uploadFiles) return;
  uploadFiles.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLElement)) return;
    const remove = target.closest(".upload-file-remove");
    if (!remove) return;
    event.preventDefault();
    const li = remove.closest(".upload-file-item");
    if (!(li instanceof HTMLElement)) return;
    const arquivoId = li.dataset.arquivoId;
    if (!arquivoId) return;
    if (!uploadIsEditing) return;

    if (pendingDeleteArquivoIds.has(arquivoId)) {
      pendingDeleteArquivoIds.delete(arquivoId);
      li.classList.remove("is-pending-delete");
    } else {
      pendingDeleteArquivoIds.add(arquivoId);
      li.classList.add("is-pending-delete");
    }
    updateUploadSaveVisibility();
  });
};

const loadItems = async (cursor = null, direction = "NEXT") => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }

  const requestSequence = ++loadItemsRequestSequence;
  showListState("Carregando comprovantes...");
  const response = await fetch(`/api/v1/itens${buildListQuery(cursor, direction)}`, {
    method: "GET",
    cache: "no-store",
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
    throw new Error("Sem permissão para visualizar esses comprovantes.");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, "Não foi possível carregar os comprovantes."));
  }

  const payload = await response.json();
  if (requestSequence !== loadItemsRequestSequence) {
    return;
  }

  const normalizedPayload = Array.isArray(payload)
    ? {
        items: payload,
        page: state.pagination.page,
        pageSize: state.pagination.pageSize,
        totalItems: payload.length,
        totalPages: payload.length > 0 ? 1 : 1,
        hasNext: false,
        hasPrevious: false,
        nextCursor: null,
        previousCursor: null,
      }
    : {
        items: Array.isArray(payload?.items) ? payload.items : [],
        page: Number(payload?.page) > 0 ? Number(payload.page) : 1,
        pageSize:
          Number(payload?.pageSize) > 0 ? Number(payload.pageSize) : state.pagination.pageSize,
        totalItems: Number(payload?.totalItems) >= 0 ? Number(payload.totalItems) : 0,
        totalPages: Number(payload?.totalPages) > 0 ? Number(payload.totalPages) : 1,
        hasNext: payload?.hasNext === true,
        hasPrevious: payload?.hasPrevious === true,
        nextCursor: typeof payload?.nextCursor === "string" ? payload.nextCursor : null,
        previousCursor: typeof payload?.previousCursor === "string" ? payload.previousCursor : null,
      };

  state.items = normalizedPayload.items;
  state.pagination.page = normalizedPayload.page;
  state.pagination.pageSize = normalizedPayload.pageSize;
  state.pagination.totalItems = normalizedPayload.totalItems;
  state.pagination.totalPages = normalizedPayload.totalPages;
  state.pagination.hasNext = normalizedPayload.hasNext;
  state.pagination.hasPrevious = normalizedPayload.hasPrevious;
  state.pagination.nextCursor = normalizedPayload.nextCursor;
  state.pagination.previousCursor = normalizedPayload.previousCursor;
  if (state.pagination.page === 1) {
    state.pagination.pageRequests.set(1, { cursor: null, direction: "NEXT" });
  }
  if (normalizedPayload.hasNext && normalizedPayload.nextCursor) {
    state.pagination.pageRequests.set(state.pagination.page + 1, {
      cursor: normalizedPayload.nextCursor,
      direction: "NEXT",
    });
  }
  state.itemChecks = new Map(
    state.items.map((item) => [String(item.id), Boolean(item.verificado)]),
  );
  renderItems();
};

const loadRoleFilterOptions = async () => {
  if (!roleFilterBox || !roleFilterSelect) return;
  const accessToken = getAccessToken();
  if (!accessToken) return;

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
    removeRoleFilterBox();
    return;
  }

  const roles = await response.json();
  applyRoleOptions(roles);
};

const loadInitialRoleFilterOptions = async (initialSelectedRole) => {
  try {
    await loadRoleFilterOptions();
  } catch (error) {
    removeRoleFilterBox();
  }
  return state.selectedRole !== initialSelectedRole;
};

const loadItemsSafely = async (fallbackMessage) => {
  try {
    await loadItems();
  } catch (error) {
    showListState(error instanceof Error ? error.message : fallbackMessage);
  }
};

const openDeleteModal = (id) => {
  state.pendingDeleteId = id;
  if (!confirmOverlay) return;
  confirmOverlay.classList.add("is-visible");
  confirmOverlay.setAttribute("aria-hidden", "false");
};

const closeDeleteModal = () => {
  state.pendingDeleteId = null;
  if (!confirmOverlay) return;
  confirmOverlay.classList.remove("is-visible");
  confirmOverlay.setAttribute("aria-hidden", "true");
};

const animateItemRemoval = (itemId) =>
  new Promise((resolve) => {
    if (!itemsList || !itemId) {
      resolve();
      return;
    }

    const card = itemsList.querySelector(`.item-card[data-id="${itemId}"]`);
    if (!(card instanceof HTMLElement)) {
      resolve();
      return;
    }

    let settled = false;
    const finish = () => {
      if (settled) return;
      settled = true;
      card.removeEventListener("animationend", handleAnimationEnd);
      resolve();
    };

    const handleAnimationEnd = (event) => {
      if (event.target !== card) return;
      if (event.animationName !== "item-remove") return;
      finish();
    };

    card.addEventListener("animationend", handleAnimationEnd);
    window.setTimeout(finish, ITEM_REMOVE_ANIMATION_MS + 60);
    window.requestAnimationFrame(() => {
      card.classList.add("is-removing");
    });
  });

const deletePendingItem = async () => {
  if (!state.pendingDeleteId) return;
  if (isContabilUser()) {
    throw new Error("Você não tem permissão para excluir comprovantes.");
  }
  if (isItemChecked(state.pendingDeleteId)) {
    throw new Error(CHECKED_ITEM_DELETE_BLOCKED_MESSAGE);
  }
  const pendingDeleteId = state.pendingDeleteId;
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }

  const executeDelete = async (csrfToken) =>
    fetch(`/api/v1/itens/${pendingDeleteId}`, {
      method: "DELETE",
      credentials: "same-origin",
      redirect: "manual",
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${accessToken}`,
        "X-CSRF-TOKEN": csrfToken,
      },
    });

  let csrfToken = await ensureCsrfToken(true);
  let response = await executeDelete(csrfToken);
  if (response.status === 403) {
    csrfToken = await ensureCsrfToken(true);
    response = await executeDelete(csrfToken);
  }

  const isRedirect =
    response.type === "opaqueredirect" ||
    (typeof response.status === "number" && response.status >= 300 && response.status < 400) ||
    response.redirected;
  if (isRedirect || response.status === 401) {
    window.location.href = "/login";
    return;
  }
  if (response.status === 403) {
    throw new Error("Você nao tem permissão para excluir este comprovante.");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, "Falha ao excluir comprovante."));
  }

  closeDeleteModal();
  await animateItemRemoval(pendingDeleteId);
  await loadItems();
};

const bindEvents = () => {
  if (filterDateInput) {
    const openDatePicker = () => {
      if (filterDatePicker) {
        filterDatePicker.open();
        return;
      }
      void initDateFilter().then(() => {
        filterDatePicker?.open();
      });
    };

    filterDateInput.addEventListener("click", openDatePicker);

    filterDateInput.addEventListener("focus", openDatePicker);

    filterDateInput.addEventListener("input", () => {
      const formattedValue = formatDateRangeInput(filterDateInput.value);
      if (filterDateInput.value !== formattedValue) {
        filterDateInput.value = formattedValue;
      }
    });

    filterDateInput.addEventListener("blur", () => {
      const formattedValue = formatDateRangeInput(filterDateInput.value);
      if (filterDateInput.value !== formattedValue) {
        filterDateInput.value = formattedValue;
      }
      const max = todayMidnight();
      const { start, end } = parseDateRange(filterDateInput.value);
      if (start || end) {
        const safeStart = start && start > max ? max : start;
        const safeEnd = end && end > max ? max : end;
        if (safeStart && safeEnd) {
          const next = `${formatDatePt(safeStart)} - ${formatDatePt(safeEnd)}`;
          filterDateInput.value = next;
          if (filterDatePicker) {
            filterDatePicker.setDate([safeStart, safeEnd], false);
          }
        } else if (safeStart) {
          const next = formatDatePt(safeStart);
          filterDateInput.value = next;
          if (filterDatePicker) {
            filterDatePicker.setDate([safeStart], false);
          }
        }
      }
      const normalizedValue = getNormalizedDateFilterValue();
      if (normalizedValue === lastAppliedDateRangeValue) {
        return;
      }
      lastAppliedDateRangeValue = normalizedValue;
      void applyFilters();
    });
  }

  if (filterDescricaoTrigger && filterDescricaoMenu) {
    filterDescricaoTrigger.addEventListener("click", (event) => {
      event.preventDefault();
      const willOpen = filterDescricaoMenu.hidden;
      filterDescricaoMenu.hidden = !willOpen;
      filterDescricaoTrigger.setAttribute("aria-expanded", String(willOpen));
    });

    filterDescricaoMenu.addEventListener("click", (event) => {
      const option = event.target instanceof Element
        ? event.target.closest(".filter-descricao-option")
        : null;
      if (!(option instanceof HTMLButtonElement)) return;

      filterDescricaoValue = option.dataset.value || "";
      filterDescricaoTrigger.textContent = option.textContent || "Todas";
      getFilterDescricaoOptions().forEach((node) => node.classList.remove("is-active"));
      option.classList.add("is-active");
      closeFilterDescricaoMenu();
      void applyFilters();
    });

    document.addEventListener(
      "mousedown",
      (event) => {
        const target = event.target;
        if (!(target instanceof Node)) return;
        if (filterDescricaoTrigger.contains(target)) return;
        if (filterDescricaoMenu.contains(target)) return;
        closeFilterDescricaoMenu();
      },
      { capture: true },
    );
  }

  if (filterRazaoInput) {
    filterRazaoInput.addEventListener(
      "input",
      debounce(() => {
        void applyFilters();
      }, 300),
    );
  }

  if (filterTypeTrigger && filterTypeMenu) {
    const closeMenu = () => {
      filterTypeMenu.hidden = true;
      filterTypeTrigger.setAttribute("aria-expanded", "false");
    };

    filterTypeTrigger.addEventListener("click", (event) => {
      event.preventDefault();
      const willOpen = filterTypeMenu.hidden;
      filterTypeMenu.hidden = !willOpen;
      filterTypeTrigger.setAttribute("aria-expanded", String(willOpen));
    });

    filterTypeOptions.forEach((option) => {
      option.addEventListener("click", () => {
        const value = option.dataset.value || "";
        filterTypeValue = value;
        filterTypeTrigger.textContent = option.textContent || "Todos";
        filterTypeOptions.forEach((node) => node.classList.remove("is-active"));
        option.classList.add("is-active");
        void syncFilterDescricaoOptions(filterTypeValue);
        closeMenu();
        void applyFilters();
      });
    });

    document.addEventListener(
      "mousedown",
      (event) => {
        const target = event.target;
        if (!(target instanceof Node)) return;
        if (filterTypeTrigger.contains(target)) return;
        if (filterTypeMenu.contains(target)) return;
        closeMenu();
      },
      { capture: true },
    );
  }

  if (filterClear) {
    filterClear.addEventListener("click", () => {
      if (filterDateInput) {
        if (filterDateInput._flatpickr) {
          filterDateInput._flatpickr.clear();
        } else {
          filterDateInput.value = "";
        }
      }
      lastAppliedDateRangeValue = "";
      filterDescricaoValue = "";
      if (filterDescricaoTrigger) filterDescricaoTrigger.textContent = "Todas";
      if (filterRazaoInput) filterRazaoInput.value = "";
      filterTypeValue = "";
      if (filterTypeTrigger) filterTypeTrigger.textContent = "Todos";
      filterTypeOptions.forEach((node) => node.classList.remove("is-active"));
      if (filterTypeOptions[0]) filterTypeOptions[0].classList.add("is-active");
      void syncFilterDescricaoOptions(filterTypeValue);
      void applyFilters();
    });
  }

  if (filterRazaoToggle && filterExtraField) {
    filterRazaoToggle.addEventListener("click", () => {
      const isCollapsed = filterExtraField.classList.toggle("is-collapsed");
      filterRazaoToggle.classList.toggle("is-collapsed", isCollapsed);
      filterRazaoToggle.setAttribute("aria-expanded", String(!isCollapsed));
    });
  }

  void syncFilterDescricaoOptions(filterTypeValue);

  if (itemsList) {
    itemsList.addEventListener("click", async (event) => {
      const target = event.target;
      if (!(target instanceof Element)) return;
      const uploadButton = target.closest(".item-upload");
      if (uploadButton) {
        const card = uploadButton.closest(".item-card");
        if (card?.dataset.id) {
          openUploadModal(card.dataset.id);
        }
        return;
      }
      const observacaoButton = target.closest(".item-observacao");
      if (observacaoButton) {
        const card = observacaoButton.closest(".item-card");
        if (card?.dataset.id) {
          void openObservacaoModal(card.dataset.id);
        }
        return;
      }
      const editButton = target.closest(".item-edit");
      if (editButton) {
        const card = editButton.closest(".item-card");
        if (card?.dataset.id) {
          void openItemEditModal(card.dataset.id);
        }
        return;
      }
      const pagamentoButton = target.closest(".item-pagamento");
      if (pagamentoButton) {
        const card = pagamentoButton.closest(".item-card");
        if (card?.dataset.id) {
          void openPagamentoModal(card.dataset.id);
        }
        return;
      }
      const checkButton = target.closest(".item-check-toggle");
      if (checkButton instanceof HTMLButtonElement) {
        const card = checkButton.closest(".item-card");
        if (!card?.dataset.id) return;
        if (isSupportUser() && isItemChecked(card.dataset.id)) {
          showListState(SUPPORT_UNCHECK_BLOCKED_MESSAGE);
          return;
        }
        const nextChecked = !isItemChecked(card.dataset.id);
        checkButton.disabled = true;
        try {
          const updated = await patchVerificacao(card.dataset.id, nextChecked);
          const persistedChecked = Boolean(updated?.verificado);
          syncItemCheckedState(card.dataset.id, persistedChecked);
          if (String(card.dataset.tipo || "").toLowerCase() === "receita") {
            window.dispatchEvent(new CustomEvent("notifications:changed"));
          }
        } catch (error) {
          showListState(
            error instanceof Error ? error.message : "Falha ao atualizar verificação.",
          );
        } finally {
          setCheckButtonState(checkButton, isItemChecked(card.dataset.id));
        }
        return;
      }
      const deleteButton = target.closest(".delete-item");
      if (!deleteButton) return;
      if (isContabilUser()) {
        showListState("Você não tem permissão para excluir comprovantes.");
        return;
      }
      const card = deleteButton.closest(".item-card");
      if (!card || !card.dataset.id) return;
      if (isItemChecked(card.dataset.id)) {
        showListState(CHECKED_ITEM_DELETE_BLOCKED_MESSAGE);
        return;
      }
      openDeleteModal(card.dataset.id);
    });
  }

  if (paginationContainers.length > 0 && itemsList) {
    paginationContainers.forEach((pagination) => {
      pagination.addEventListener("click", async (event) => {
        const target = event.target;
        if (!(target instanceof Element)) return;
        const button = target.closest("button[data-page][data-direction]");
        if (!(button instanceof HTMLButtonElement)) return;
        if (button.disabled) return;
        const page = Number(button.dataset.page);
        if (!Number.isInteger(page) || page < 1 || page === state.pagination.page) return;
        const cursor = String(button.dataset.cursor || "");
        const direction = String(button.dataset.direction || "NEXT");
        const currentPage = state.pagination.page;
        try {
          if (page > 1 && cursor && ["NEXT", "PREVIOUS"].includes(direction)) {
            state.pagination.page = page;
            await loadItems(cursor, direction);
          } else {
            await navigateToPaginationPage(page);
          }
        } catch (error) {
          state.pagination.page = currentPage;
          showListState(
            error instanceof Error ? error.message : "Erro ao carregar comprovantes. Tente novamente.",
          );
        }
        itemsList.scrollIntoView({ block: "start" });
      });
    });
  }

  if (confirmCancel) {
    confirmCancel.addEventListener("click", closeDeleteModal);
  }

  if (confirmDelete) {
    confirmDelete.addEventListener("click", async () => {
      try {
        await deletePendingItem();
      } catch (error) {
        showListState(
          error instanceof Error ? error.message : "Erro ao excluir comprovante. Tente novamente."
        );
      }
    });
  }

  if (uploadClose) {
    uploadClose.addEventListener("click", closeUploadModal);
  }

  if (observacaoClose) {
    observacaoClose.addEventListener("click", closeObservacaoModal);
  }
  if (historicoClose) {
    historicoClose.addEventListener("click", closeHistoricoModal);
  }
  if (itemEditClose) {
    itemEditClose.addEventListener("click", closeItemEditModal);
  }
  if (itemEditHistory) {
    itemEditHistory.addEventListener("click", () => {
      if (pendingItemEditId) void openHistoricoModal(pendingItemEditId);
    });
  }
  if (itemEditForm) {
    itemEditForm.addEventListener("submit", (event) => {
      void saveItemEdit(event);
    });
  }
  if (itemEditTipo) {
    itemEditTipo.addEventListener("change", () => {
      setItemEditError();
      void updateItemEditDescricaoByTipo(itemEditTipo.value);
    });
  }
  if (itemEditData) {
    itemEditData.addEventListener("input", () => {
      itemEditData.value = formatItemEditDate(itemEditData.value);
      itemEditData.setCustomValidity("");
    });
    itemEditData.addEventListener("blur", () => {
      itemEditData.value = formatItemEditDate(itemEditData.value);
    });
  }
  if (itemEditValor) {
    const applyItemEditMoneyMask = () => {
      itemEditValor.value = formatItemEditMoney(itemEditValor.value);
      itemEditValor.setCustomValidity("");
    };
    itemEditValor.addEventListener("input", applyItemEditMoneyMask);
    itemEditValor.addEventListener("blur", applyItemEditMoneyMask);
  }
  if (pagamentoClose) {
    pagamentoClose.addEventListener("click", closePagamentoModal);
  }
  if (pagamentoSuccessClose) {
    pagamentoSuccessClose.addEventListener("click", closePagamentoSuccess);
  }
  document.addEventListener(
    "mousedown",
    (event) => {
      const target = event.target;
      if (target instanceof Element && target.closest(".pagamento-origin-select")) return;
      closePagamentoOriginMenus();
    },
    { capture: true },
  );
  if (observacaoEdit) {
    observacaoEdit.addEventListener("click", () => {
      if (!pendingObservacaoItemId || !observacaoContent) return;
      if (!observacaoIsEditing) {
        startObservacaoEdit();
      }
    });
  }
  if (observacaoSave) {
    observacaoSave.addEventListener("click", saveObservacao);
  }
  pagamentoFormaInputs.forEach((input) => {
    if (!(input instanceof HTMLInputElement)) return;
    input.addEventListener("change", () => {
      if (!pagamentoState || !input.checked) return;
      setPagamentoValidation();
      pagamentoState.formaPagamento = input.value === "PARCELADO" ? "PARCELADO" : "AVISTA";
      const nextCount =
        pagamentoState.formaPagamento === "PARCELADO"
          ? Math.min(Math.max(Number(pagamentoQuantidade?.value || pagamentoState.quantidadeParcelas || 2), 2), 4)
          : 1;
      resetPagamentoParcelas(nextCount);
      renderPagamentoModal();
    });
  });
  if (pagamentoQuantidade) {
    pagamentoQuantidade.addEventListener("change", () => {
      if (!pagamentoState || pagamentoState.formaPagamento !== "PARCELADO") return;
      setPagamentoValidation();
      const nextCount = Math.min(Math.max(Number(pagamentoQuantidade.value || 2), 2), 4);
      recalculatePagamentoParcelas(nextCount);
      renderPagamentoModal();
    });
  }
  if (pagamentoParcelas) {
    pagamentoParcelas.addEventListener("click", (event) => {
      const target = event.target;
      if (!(target instanceof Element) || !pagamentoState) return;
      const removeButton = target.closest(".pagamento-pdf-remove");
      if (removeButton instanceof HTMLButtonElement) {
        const numero = Number(removeButton.dataset.parcela || 0);
        const arquivoIndex = Number(removeButton.dataset.arquivoIndex || -1);
        const parcela = pagamentoState.parcelas.find((entry) => entry.numero === numero);
        const arquivo = parcela?.arquivos?.[arquivoIndex];
        if (!parcela || !arquivo) return;
        setPagamentoValidation();
        if (arquivo.legacy) {
          parcela.removerArquivoLegado = true;
        } else if (arquivo.id) {
          parcela.arquivosRemovidos.push(arquivo.id);
        }
        parcela.arquivos.splice(arquivoIndex, 1);
        renderPagamentoModal();
        return;
      }
      const originOption = target.closest(".pagamento-origin-option");
      if (originOption instanceof HTMLButtonElement) {
        const select = originOption.closest(".pagamento-origin-select");
        const numero = Number(select?.dataset.parcela || 0);
        const parcela = pagamentoState.parcelas.find((entry) => entry.numero === numero);
        if (!parcela || !PAGAMENTO_CONTAS_ORIGEM.has(originOption.dataset.value)) return;
        setPagamentoValidation();
        parcela.contaOrigemPagamento = originOption.dataset.value;
        renderPagamentoModal();
        return;
      }
      const originTrigger = target.closest(".pagamento-origin-trigger");
      if (originTrigger instanceof HTMLButtonElement) {
        const select = originTrigger.closest(".pagamento-origin-select");
        const menu = select?.querySelector(".pagamento-origin-menu");
        if (!(select instanceof HTMLElement) || !(menu instanceof HTMLElement)) return;
        const shouldOpen = menu.hidden;
        closePagamentoOriginMenus();
        if (shouldOpen) {
          select.classList.add("is-open");
          menu.hidden = false;
          originTrigger.setAttribute("aria-expanded", "true");
        }
        return;
      }
      const attachButton = target.closest(".pagamento-anexo-btn");
      if (!(attachButton instanceof HTMLButtonElement)) return;
      const numero = Number(attachButton.dataset.parcela || 0);
      const input = pagamentoParcelas.querySelector(`.pagamento-anexo-input[data-parcela="${numero}"]`);
      if (input instanceof HTMLInputElement) {
        input.click();
      }
    });
    pagamentoParcelas.addEventListener("change", async (event) => {
      const target = event.target;
      if (!(target instanceof Element) || !pagamentoState) return;
      if (target.matches(".pagamento-check")) {
        const checkbox = target;
        if (!(checkbox instanceof HTMLInputElement)) return;
        const numero = Number(checkbox.dataset.parcela || 0);
        const parcela = pagamentoState.parcelas.find((entry) => entry.numero === numero);
        if (!parcela) return;
        setPagamentoValidation();
        parcela.paga = checkbox.checked;
        if (!parcela.paga) {
          parcela.contaOrigemPagamento = "";
          parcela.arquivos.forEach((arquivo) => {
            if (arquivo.legacy) {
              parcela.removerArquivoLegado = true;
            } else if (arquivo.id && !parcela.arquivosRemovidos.includes(arquivo.id)) {
              parcela.arquivosRemovidos.push(arquivo.id);
            }
          });
          parcela.arquivos = [];
        }
        renderPagamentoModal();
        return;
      }
      if (target.matches(".pagamento-anexo-input")) {
        const input = target;
        if (!(input instanceof HTMLInputElement)) return;
        const numero = Number(input.dataset.parcela || 0);
        const parcela = pagamentoState.parcelas.find((entry) => entry.numero === numero);
        if (!parcela) return;
        setPagamentoValidation();
        const picked = input.files ? Array.from(input.files) : [];
        try {
          const valid = validateUploadFilesOrThrow(picked);
          parcela.arquivos.push(
            ...valid.map((file) => ({ id: null, name: file.name, file, legacy: false })),
          );
          renderPagamentoModal();
        } catch (error) {
          showListState(error instanceof Error ? error.message : PDF_ONLY_MESSAGE);
        }
      }
    });
    pagamentoParcelas.addEventListener("input", (event) => {
      const target = event.target;
      if (!(target instanceof HTMLInputElement) || !pagamentoState) return;
      if (!target.matches(".pagamento-valor-input")) return;
      setPagamentoValidation();
      applyInstallmentValueMask(target);
    });
    pagamentoParcelas.addEventListener("change", (event) => {
      const target = event.target;
      if (!(target instanceof HTMLInputElement) || !target.matches(".pagamento-valor-input")) return;
      setPagamentoValidation();
      applyInstallmentValueMask(target);
    });
  }
  if (pagamentoSave) {
    pagamentoSave.addEventListener("click", () => {
      void savePagamento();
    });
  }

  if (uploadSave) {
    uploadSave.addEventListener("click", saveUploadChanges);
  }
};

const init = async () => {
  bindEvents();
  scheduleFlatpickrWarmup();
  bindUploadDrop();
  bindUploadEditActions();
  if (uploadInput) {
    retainedUploadFiles = uploadInput.files ? Array.from(uploadInput.files) : [];
    uploadInput.addEventListener("change", () => {
      const picked = uploadInput.files ? Array.from(uploadInput.files) : [];

      if (settingUploadFilesProgrammatically) {
        retainedUploadFiles = picked;
        renderSelectedFiles(uploadInput.files);
        return;
      }

      setUploadErrorEntries(collectUploadErrorEntries(picked));
      const invalidFile = findInvalidUploadFile(picked);
      if (invalidFile) {
        showListState(PDF_ONLY_MESSAGE);
      }
      const oversizedFile = findOversizedUploadFile(picked);
      if (oversizedFile) {
        showListState(MAX_RECEIPT_SIZE_MESSAGE);
      }
      const validFiles = sanitizeUploadFiles(picked);
      const merged = mergeUploadFiles(retainedUploadFiles, validFiles);
      retainedUploadFiles = merged;
      setUploadInputFiles(merged);
    });
  }
  state.selectedRole = getStoredSelectedRole();

  const initialSelectedRole = state.selectedRole;
  const userRolesPromise = loadAndApplyCurrentUserRoles();
  const roleFilterPromise = loadInitialRoleFilterOptions(initialSelectedRole);

  await loadItemsSafely("Erro ao carregar comprovantes. Tente novamente.");

  void roleFilterPromise.then((selectedRoleChanged) => {
    if (!selectedRoleChanged) {
      return;
    }
    resetPagination();
    void loadItemsSafely("Erro ao carregar comprovantes do candidato selecionado.");
  });

  await userRolesPromise;
};

init();


