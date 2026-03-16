const state = {
  items: [],
  filteredItems: [],
  pendingDeleteId: null,
  csrfToken: null,
};

const root = document.documentElement;
const navbarRoot = document.getElementById("navbar-root");
const filterInput = document.querySelector(".filter-date-range");
const filterType = document.querySelector(".filter-type-select");
const filterClear = document.querySelector(".filter-clear");
const listState = document.getElementById("list-state");
const itemsList = document.getElementById("items-list");
const itemCardTemplate = document.getElementById("item-card-template");
const confirmOverlay = document.querySelector(".confirm-overlay");
const confirmCancel = document.querySelector(".confirm-cancel");
const confirmDelete = document.querySelector(".confirm-delete");

const readCookie = (name) => {
  const match = document.cookie.match(new RegExp("(^| )" + name + "=([^;]+)"));
  return match ? decodeURIComponent(match[2]) : null;
};

const writeCookie = (name, value, days = 365) => {
  const expires = new Date(Date.now() + days * 864e5).toUTCString();
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=/`;
};

const getAccessToken = () => localStorage.getItem("sc_access_token");

const mountNavbar = () => {
  if (!navbarRoot) return;
  navbarRoot.innerHTML = `
    <nav class="navbar">
      <div class="navbar-content">
        <a class="brand" href="/home" aria-label="Ir para a home">
          <span class="brand-mark">SC</span>
          <span class="brand-name">Sistema</span>
        </a>
        <div class="nav-actions">
          <button class="logout-btn" type="button" aria-label="Sair da conta">Logout</button>
          <button class="theme-toggle" type="button" aria-pressed="false" aria-label="Alternar tema">
            <span class="theme-icon" aria-hidden="true"></span>
          </button>
        </div>
      </div>
    </nav>
  `;
  const logoutButton = navbarRoot.querySelector(".logout-btn");
  if (logoutButton) {
    logoutButton.addEventListener("click", () => {
      localStorage.removeItem("sc_access_token");
      document.cookie = "SC_TOKEN=; Max-Age=0; path=/";
      window.location.href = "/login";
    });
  }
};

const updateThemeToggleLabel = () => {
  const toggle = document.querySelector(".theme-toggle");
  if (!toggle) return;
  const isDark = root.dataset.theme === "dark";
  toggle.setAttribute("aria-pressed", isDark ? "true" : "false");
  toggle.setAttribute("aria-label", isDark ? "Ativar modo claro" : "Ativar modo escuro");
  toggle.querySelector(".theme-icon").innerHTML = `
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <circle cx="12" cy="12" r="4.6" fill="currentColor"></circle>
      <g stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
        <line x1="12" y1="2.4" x2="12" y2="5"></line>
        <line x1="12" y1="19" x2="12" y2="21.6"></line>
        <line x1="2.4" y1="12" x2="5" y2="12"></line>
        <line x1="19" y1="12" x2="21.6" y2="12"></line>
        <line x1="5.1" y1="5.1" x2="6.9" y2="6.9"></line>
        <line x1="17.1" y1="17.1" x2="18.9" y2="18.9"></line>
        <line x1="17.1" y1="6.9" x2="18.9" y2="5.1"></line>
        <line x1="5.1" y1="18.9" x2="6.9" y2="17.1"></line>
      </g>
    </svg>`;
};

const initTheme = () => {
  const savedTheme = readCookie("theme") || localStorage.getItem("theme");
  root.dataset.theme = savedTheme === "dark" ? "dark" : "light";
  writeCookie("theme", root.dataset.theme);
  localStorage.setItem("theme", root.dataset.theme);
  updateThemeToggleLabel();

  const toggle = document.querySelector(".theme-toggle");
  if (!toggle) return;
  toggle.addEventListener("click", () => {
    const isDark = root.dataset.theme === "dark";
    root.dataset.theme = isDark ? "light" : "dark";
    writeCookie("theme", root.dataset.theme);
    localStorage.setItem("theme", root.dataset.theme);
    updateThemeToggleLabel();
  });
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

const parseDateRange = (value) => {
  const text = (value || "").trim();
  if (!text) return { start: null, end: null };
  const [startRaw, endRaw] = text.split(" até ").map((part) => part.trim());
  const parse = (dateValue) => {
    const [d, m, y] = dateValue.split("/").map((part) => Number(part));
    if (!d || !m || !y) return null;
    const date = new Date(y, m - 1, d);
    date.setHours(0, 0, 0, 0);
    return date;
  };
  return {
    start: parse(startRaw),
    end: endRaw ? parse(endRaw) : parse(startRaw),
  };
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

  node.querySelector('[data-field="valor"]').textContent = formatCurrency(item.valor);
  node.querySelector('[data-field="tipo"]').textContent =
    item.tipo === "RECEITA" ? "Receita" : "Despesa";
  node.querySelector('[data-field="data"]').textContent = formatDate(item.data);
  node.querySelector('[data-field="horario"]').textContent = formatTime(item.horarioCriacao);

  const downloadLink = node.querySelector(".item-download");
  const arquivoUrl = item.id ? `/api/v1/itens/${item.id}/arquivo` : null;
  if (arquivoUrl) {
    downloadLink.href = arquivoUrl;
    downloadLink.download = "";
  } else {
    downloadLink.removeAttribute("href");
    downloadLink.classList.add("is-disabled");
    downloadLink.setAttribute("aria-disabled", "true");
    downloadLink.textContent = "Arquivo indisponível";
  }

  return node;
};

const renderItems = () => {
  if (!itemsList) return;
  itemsList.innerHTML = "";
  if (state.filteredItems.length === 0) {
    showListState("Nenhum comprovante encontrado.");
    return;
  }
  hideListState();

  const fragment = document.createDocumentFragment();
  state.filteredItems.forEach((item) => {
    fragment.appendChild(createItemCard(item));
  });
  itemsList.appendChild(fragment);
};

const applyFilters = () => {
  const type = (filterType?.value || "").trim();
  const { start, end } = parseDateRange(filterInput?.value || "");

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
    return true;
  });

  renderItems();
};

const ensureCsrfToken = async () => {
  if (state.csrfToken) return state.csrfToken;
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

  if (response.status === 401 || response.status === 403) {
    window.location.href = "/login";
    return;
  }
  if (!response.ok) {
    throw new Error("Não foi possível carregar os comprovantes.");
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

  const csrfToken = await ensureCsrfToken();
  const response = await fetch(`/api/v1/itens/${state.pendingDeleteId}`, {
    method: "DELETE",
    credentials: "same-origin",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "X-CSRF-TOKEN": csrfToken,
    },
  });

  if (response.status === 401 || response.status === 403) {
    window.location.href = "/login";
    return;
  }
  if (!response.ok) {
    throw new Error("Falha ao excluir comprovante.");
  }

  state.items = state.items.filter((item) => item.id !== state.pendingDeleteId);
  closeDeleteModal();
  applyFilters();
};

const bindEvents = () => {
  if (filterInput) {
    if (window.flatpickr) {
      window.flatpickr(filterInput, {
        mode: "range",
        dateFormat: "d/m/Y",
        maxDate: "today",
        locale: {
          rangeSeparator: " até ",
        },
        onChange: applyFilters,
      });
    }
    filterInput.addEventListener("input", applyFilters);
  }

  if (filterType) {
    filterType.addEventListener("change", applyFilters);
  }

  if (filterClear) {
    filterClear.addEventListener("click", () => {
      if (filterInput) filterInput.value = "";
      if (filterType) filterType.value = "";
      applyFilters();
    });
  }

  if (itemsList) {
    itemsList.addEventListener("click", (event) => {
      const target = event.target;
      if (!(target instanceof Element)) return;
      const deleteButton = target.closest(".delete-item");
      if (!deleteButton) return;
      const card = deleteButton.closest(".item-card");
      if (!card || !card.dataset.id) return;
      openDeleteModal(card.dataset.id);
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
        showListState("Erro ao excluir comprovante. Tente novamente.");
      }
    });
  }
};

const init = async () => {
  mountNavbar();
  initTheme();
  bindEvents();
  try {
    await loadItems();
  } catch (error) {
    showListState("Erro ao carregar comprovantes. Tente novamente.");
  }
};

init();
