const state = {
  items: [],
  filteredItems: [],
  pendingDeleteId: null,
  csrfToken: null,
  pagination: {
    page: 1,
    pageSize: 10,
  },
};

const filterDateInput = document.querySelector(".filter-date-range");
const filterDescricaoTrigger = document.querySelector(".filter-descricao-trigger");
const filterDescricaoMenu = document.querySelector(".filter-descricao-menu");
const filterDescricaoOptions = document.querySelectorAll(".filter-descricao-option");
let filterDescricaoValue = "";
const filterRazaoInput = document.querySelector(".filter-razao-input");
const filterTypeTrigger = document.querySelector(".filter-type-trigger");
const filterTypeMenu = document.querySelector(".filter-type-menu");
const filterTypeOptions = document.querySelectorAll(".filter-type-option");
let filterTypeValue = "";
const filterClear = document.querySelector(".filter-clear");
const filterRazaoToggle = document.querySelector(".filter-toggle");
const filterExtraField = document.querySelector("[data-filter-extra]");
const listState = document.getElementById("list-state");
const itemsList = document.getElementById("items-list");
const pagination = document.getElementById("pagination");
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
let pendingUploadItemId = null;
let pendingObservacaoItemId = null;
let uploadIsEditing = false;
let pendingDeleteArquivoIds = new Set();
let filterDatePicker = null;
let dateFilterReady = false;
let monthMenuCloseHandlerBound = false;
let yearMenuCloseHandlerBound = false;
let observacaoIsEditing = false;
let retainedUploadFiles = [];
let settingUploadFilesProgrammatically = false;

const getAccessToken = () => localStorage.getItem("sc_access_token");

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

const formatCurrency = (value) =>
  Number(value || 0).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

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
    ENERGIA: "Energia elétrica",
    AGUA: "Ãgua",
    SERVICOS: "ServiÃ§os",
    IMPOSTOS: "Impostos",
    MATERIAIS: "Materiais",
    OUTROS: "Outros",
  };
  return labels[key] || (value ? String(value) : "-");
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

const ensureFlatpickrReady = async () => {
  if (window.flatpickr) {
    return true;
  }

  try {
    await loadScript("assets/vendor/flatpickr/flatpickr.min.js");
    return Boolean(window.flatpickr);
  } catch (error) {
    return false;
  }
};

const initDateFilter = async () => {
  if (!filterDateInput || dateFilterReady) {
    return;
  }

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
    onChange: applyFilters,
  });

  dateFilterReady = true;
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

const createItemCard = (item) => {
  const node = itemCardTemplate.content.cloneNode(true);
  const article = node.querySelector(".item-card");
  article.dataset.id = item.id;
  article.dataset.tipo = String(item.tipo || "").toLowerCase();
  article.dataset.data = item.data;
  article.dataset.observacao = item.observacao ? String(item.observacao) : "";

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

  return node;
};

const updateDownloadButton = (itemId, hasArquivos) => {
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

const clampPaginationPage = () => {
  const pageSize = state.pagination.pageSize;
  const totalPages = Math.max(1, Math.ceil(state.filteredItems.length / pageSize));
  state.pagination.page = Math.min(Math.max(1, state.pagination.page), totalPages);
  return totalPages;
};

const resetPagination = () => {
  state.pagination.page = 1;
};

const createPaginationButton = ({ label, page, disabled = false, current = false }) => {
  const button = document.createElement("button");
  button.type = "button";
  button.className = "pagination-btn";
  button.textContent = label;
  button.dataset.page = String(page);
  button.disabled = disabled;
  if (current) {
    button.classList.add("is-current");
    button.setAttribute("aria-current", "page");
  }
  return button;
};

const createPaginationEllipsis = () => {
  const span = document.createElement("span");
  span.className = "pagination-ellipsis";
  span.textContent = "…";
  span.setAttribute("aria-hidden", "true");
  return span;
};

const renderPagination = () => {
  if (!pagination) return;

  if (state.filteredItems.length === 0) {
    pagination.hidden = true;
    pagination.innerHTML = "";
    return;
  }

  const totalPages = clampPaginationPage();
  if (totalPages <= 1) {
    pagination.hidden = true;
    pagination.innerHTML = "";
    return;
  }

  const currentPage = state.pagination.page;
  pagination.hidden = false;
  pagination.innerHTML = "";

  pagination.appendChild(
    createPaginationButton({
      label: "‹",
      page: currentPage - 1,
      disabled: currentPage <= 1,
    }),
  );

  const addPage = (page) => {
    pagination.appendChild(
      createPaginationButton({
        label: String(page),
        page,
        current: page === currentPage,
      }),
    );
  };

  const addEllipsis = () => pagination.appendChild(createPaginationEllipsis());

  if (totalPages <= 7) {
    for (let page = 1; page <= totalPages; page += 1) {
      addPage(page);
    }
  } else if (currentPage <= 4) {
    addPage(1);
    addPage(2);
    addPage(3);
    addPage(4);
    addPage(5);
    addEllipsis();
    addPage(totalPages);
  } else if (currentPage >= totalPages - 3) {
    addPage(1);
    addEllipsis();
    for (let page = totalPages - 4; page <= totalPages; page += 1) {
      addPage(page);
    }
  } else {
    addPage(1);
    addEllipsis();
    addPage(currentPage - 1);
    addPage(currentPage);
    addPage(currentPage + 1);
    addEllipsis();
    addPage(totalPages);
  }

  pagination.appendChild(
    createPaginationButton({
      label: "›",
      page: currentPage + 1,
      disabled: currentPage >= totalPages,
    }),
  );
};

const renderItems = () => {
  if (!itemsList) return;
  itemsList.innerHTML = "";
  if (state.filteredItems.length === 0) {
    showListState("Nenhum comprovante encontrado.");
    if (pagination) {
      pagination.hidden = true;
      pagination.innerHTML = "";
    }
    return;
  }
  hideListState();

  const totalPages = clampPaginationPage();
  const page = state.pagination.page;
  const pageSize = state.pagination.pageSize;
  const startIndex = (page - 1) * pageSize;
  const pageItems = state.filteredItems.slice(startIndex, startIndex + pageSize);

  const fragment = document.createDocumentFragment();
  pageItems.forEach((item) => {
    fragment.appendChild(createItemCard(item));
  });
  itemsList.appendChild(fragment);

  if (totalPages > 1) {
    renderPagination();
  } else if (pagination) {
    pagination.hidden = true;
    pagination.innerHTML = "";
  }
};

const applyFilters = (resetPage = true) => {
  const type = (filterTypeValue || "").trim();
  const { start, end } = parseDateRange(filterDateInput?.value || "");
  const descricaoFilter = (filterDescricaoValue || "").trim().toUpperCase();
  const razaoFilter = normalizeText(filterRazaoInput?.value || "").trim();

  state.filteredItems = state.items.filter((item) => {
    const itemType = String(item.tipo || "").toLowerCase();
    if (type && itemType !== type) {
      return false;
    }

    if (start && end) {
      const itemDate = new Date(`${item.data}T00:00:00`);
      itemDate.setHours(0, 0, 0, 0);
      if (itemDate < start || itemDate > end) {
        return false;
      }
    }

    if (razaoFilter) {
      const razaoText = normalizeText(item.razaoSocialNome);
      if (!razaoText.includes(razaoFilter)) {
        return false;
      }
    }

    if (descricaoFilter) {
      const rawKey = String(item.descricao || "").trim().toUpperCase();
      if (rawKey !== descricaoFilter) {
        return false;
      }
    }
    return true;
  });

  if (resetPage) {
    resetPagination();
  }
  renderItems();
};

const ensureCsrfToken = async (forceRefresh = false) => {
  if (!forceRefresh && state.csrfToken) return state.csrfToken;
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

const openObservacaoModal = (id) => {
  pendingObservacaoItemId = id;
  if (!observacaoOverlay || !observacaoContent) return;
  const item = state.items.find((entry) => entry.id === id);
  const texto = item?.observacao ? String(item.observacao) : "";
  observacaoContent.value = texto;
  observacaoContent.readOnly = true;
  observacaoIsEditing = false;
  setObservacaoSaveVisible(false);
  observacaoOverlay.classList.add("is-visible");
  observacaoOverlay.setAttribute("aria-hidden", "false");
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
    const idx = state.items.findIndex((entry) => entry.id === pendingObservacaoItemId);
    if (idx >= 0) {
      state.items[idx].observacao = updated?.observacao ?? value;
    }
    // Keep dataset in sync for the card.
    if (itemsList) {
      const card = itemsList.querySelector(`.item-card[data-id="${pendingObservacaoItemId}"]`);
      if (card) {
        card.dataset.observacao = updated?.observacao ?? value;
      }
    }
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

const filesToBase64 = async (files) => {
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
  const pdfs = effectiveFiles.filter(isPdfFile);
  if (pdfs.length === 0) {
    return [];
  }

  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }
  const token = await ensureCsrfToken(true);
  const arquivosPdf = await filesToBase64(pdfs);
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
    return [];
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
  const newFiles = uploadInput?.files ? Array.from(uploadInput.files).filter(isPdfFile) : [];
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
  uploadSelected.classList.toggle("is-grid", pdfs.length > 1);
  updateUploadSaveVisibility();
  if (pdfs.length === 0) {
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
};

const bindUploadDrop = () => {
  if (!uploadDrop || !uploadInput) return;
  const setFiles = (files) => {
    if (!files || files.length === 0) return;
    const picked = Array.from(files);
    const merged = mergeUploadFiles(retainedUploadFiles, picked);
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

const loadItems = async () => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }

  showListState("Carregando comprovantes...");
  const response = await fetch("/api/v1/itens", {
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
    throw new Error("Sem permissão para visualizar esses comprovantes.");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, "Não foi possível carregar os comprovantes."));
  }

  const items = await response.json();
  state.items = Array.isArray(items) ? items : [];
  state.items.sort((a, b) => {
    const dateA = new Date(a.horarioCriacao).getTime();
    const dateB = new Date(b.horarioCriacao).getTime();
    return dateB - dateA;
  });
  applyFilters();
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

const deletePendingItem = async () => {
  if (!state.pendingDeleteId) return;
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }

  const executeDelete = async (csrfToken) =>
    fetch(`/api/v1/itens/${state.pendingDeleteId}`, {
      method: "DELETE",
      credentials: "same-origin",
      headers: {
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

  if (response.status === 401) {
    window.location.href = "/login";
    return;
  }
  if (response.status === 403) {
    throw new Error("Você nao tem permissão para excluir este comprovante.");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, "Falha ao excluir comprovante."));
  }

  state.items = state.items.filter((item) => item.id !== state.pendingDeleteId);
  closeDeleteModal();
  applyFilters(false);
};

const bindEvents = () => {
  if (filterDateInput) {
    filterDateInput.addEventListener("click", () => {
      if (filterDatePicker) {
        filterDatePicker.open();
      }
    });

    filterDateInput.addEventListener("focus", () => {
      if (filterDatePicker) {
        filterDatePicker.open();
      }
    });

    filterDateInput.addEventListener("input", () => {
      const formattedValue = formatDateRangeInput(filterDateInput.value);
      if (filterDateInput.value !== formattedValue) {
        filterDateInput.value = formattedValue;
      }
      applyFilters();
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
      applyFilters();
    });
  }

  if (filterDescricaoTrigger && filterDescricaoMenu) {
    const closeMenu = () => {
      filterDescricaoMenu.hidden = true;
      filterDescricaoTrigger.setAttribute("aria-expanded", "false");
    };

    filterDescricaoTrigger.addEventListener("click", (event) => {
      event.preventDefault();
      const willOpen = filterDescricaoMenu.hidden;
      filterDescricaoMenu.hidden = !willOpen;
      filterDescricaoTrigger.setAttribute("aria-expanded", String(willOpen));
    });

    filterDescricaoOptions.forEach((option) => {
      option.addEventListener("click", () => {
        const value = option.dataset.value || "";
        filterDescricaoValue = value;
        filterDescricaoTrigger.textContent = option.textContent || "Todas";
        filterDescricaoOptions.forEach((node) => node.classList.remove("is-active"));
        option.classList.add("is-active");
        closeMenu();
        applyFilters();
      });
    });

    document.addEventListener(
      "mousedown",
      (event) => {
        const target = event.target;
        if (!(target instanceof Node)) return;
        if (filterDescricaoTrigger.contains(target)) return;
        if (filterDescricaoMenu.contains(target)) return;
        closeMenu();
      },
      { capture: true },
    );
  }

  if (filterRazaoInput) {
    filterRazaoInput.addEventListener("input", applyFilters);
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
        closeMenu();
        applyFilters();
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
      filterDescricaoValue = "";
      if (filterDescricaoTrigger) filterDescricaoTrigger.textContent = "Todas";
      filterDescricaoOptions.forEach((node) => node.classList.remove("is-active"));
      if (filterDescricaoOptions[0]) filterDescricaoOptions[0].classList.add("is-active");
      if (filterRazaoInput) filterRazaoInput.value = "";
      filterTypeValue = "";
      if (filterTypeTrigger) filterTypeTrigger.textContent = "Todos";
      filterTypeOptions.forEach((node) => node.classList.remove("is-active"));
      if (filterTypeOptions[0]) filterTypeOptions[0].classList.add("is-active");
      applyFilters();
    });
  }

  if (filterRazaoToggle && filterExtraField) {
    filterRazaoToggle.addEventListener("click", () => {
      const isCollapsed = filterExtraField.classList.toggle("is-collapsed");
      filterRazaoToggle.classList.toggle("is-collapsed", isCollapsed);
      filterRazaoToggle.setAttribute("aria-expanded", String(!isCollapsed));
      if (isCollapsed) {
        if (filterRazaoInput) filterRazaoInput.value = "";
        filterDescricaoValue = "";
        if (filterDescricaoTrigger) filterDescricaoTrigger.textContent = "Todas";
        filterDescricaoOptions.forEach((node) => node.classList.remove("is-active"));
        if (filterDescricaoOptions[0]) filterDescricaoOptions[0].classList.add("is-active");
        applyFilters();
      }
    });
  }

  if (itemsList) {
    itemsList.addEventListener("click", (event) => {
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
          openObservacaoModal(card.dataset.id);
        }
        return;
      }
      const deleteButton = target.closest(".delete-item");
      if (!deleteButton) return;
      const card = deleteButton.closest(".item-card");
      if (!card || !card.dataset.id) return;
      openDeleteModal(card.dataset.id);
    });
  }

  if (pagination && itemsList) {
    pagination.addEventListener("click", (event) => {
      const target = event.target;
      if (!(target instanceof Element)) return;
      const button = target.closest("button[data-page]");
      if (!(button instanceof HTMLButtonElement)) return;
      if (button.disabled) return;
      const next = Number(button.dataset.page);
      if (!Number.isFinite(next)) return;
      state.pagination.page = next;
      renderItems();
      itemsList.scrollIntoView({ block: "start" });
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

  if (uploadSave) {
    uploadSave.addEventListener("click", saveUploadChanges);
  }
};

const init = async () => {
  bindEvents();
  await initDateFilter();
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

      const merged = mergeUploadFiles(retainedUploadFiles, picked);
      retainedUploadFiles = merged;
      setUploadInputFiles(merged);
    });
  }
  try {
    await loadItems();
  } catch (error) {
    showListState(
      error instanceof Error ? error.message : "Erro ao carregar comprovantes. Tente novamente."
    );
  }
};

init();


