const state = {
  relatorio: null,
  selectedRole: "",
  isAdmin: false,
};

const summaryCard = document.getElementById("summary-card");
const reportsGrid = document.getElementById("reports-grid");
const reportState = document.getElementById("report-state");
const downloadReportButton = document.getElementById("download-report-btn");
const roleFilterBox = document.getElementById("role-filter-box");
const roleFilterSelect = document.getElementById("role-filter-select");

const summaryItemTemplate = document.getElementById("summary-item-template");
const reportCardTemplate = document.getElementById("report-card-template");
const reportRowTemplate = document.getElementById("report-row-template");
const reportEmptyTemplate = document.getElementById("report-empty-template");

const CARD_STYLE_CONFIG = {
  receitas: {
    cardClass: "report-card-receitas",
    headerClass: "report-header-receitas",
    countClass: "report-count-receitas",
    listClass: "report-list-receitas",
    rowClass: "report-row-receitas",
    emptyClass: "report-row-empty-receitas",
  },
  despesas: {
    cardClass: "report-card-despesas",
    headerClass: "report-header-despesas",
    countClass: "report-count-despesas",
    listClass: "report-list-despesas",
    rowClass: "report-row-despesas",
    emptyClass: "report-row-empty-despesas",
  },
};

const getAccessToken = () => localStorage.getItem("sc_access_token");

const buildRoleQuery = () => {
  if (!state.isAdmin || !state.selectedRole) return "";
  const params = new URLSearchParams({ role: state.selectedRole });
  return `?${params.toString()}`;
};

const removeRoleFilterBox = () => {
  state.isAdmin = false;
  state.selectedRole = "";
  if (roleFilterBox) {
    roleFilterBox.remove();
  }
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

const applyRoleOptions = (roles) => {
  if (!roleFilterBox || !roleFilterSelect) return;
  const orderedRoles = orderRoles(roles);
  if (!orderedRoles.length) {
    removeRoleFilterBox();
    return;
  }

  state.isAdmin = true;
  roleFilterSelect.innerHTML = "";
  orderedRoles.forEach((role) => {
    const option = document.createElement("option");
    option.value = role;
    option.textContent = role;
    roleFilterSelect.appendChild(option);
  });

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
};

const formatCompactNumber = (value) => {
  const rounded = Number(value.toFixed(2));
  return rounded.toLocaleString("pt-BR", {
    minimumFractionDigits: Number.isInteger(rounded) ? 0 : 1,
    maximumFractionDigits: 2,
  });
};

const formatCurrency = (value) => {
  const numericValue = Number(value || 0);
  const signal = numericValue < 0 ? "-" : "";
  const absoluteValue = Math.abs(numericValue);

  if (absoluteValue >= 1000000) {
    return `${signal}R$ ${formatCompactNumber(absoluteValue / 1000000)} MI`;
  }
  if (absoluteValue >= 100000) {
    return `${signal}R$ ${formatCompactNumber(absoluteValue / 1000)} Mil`;
  }

  return `${signal}${absoluteValue.toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
};

const formatPercent = (value) =>
  `${Number(value || 0).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;

const formatDate = (isoDate) => {
  if (!isoDate) return "-";
  const [year, month, day] = String(isoDate).split("-");
  return year && month && day ? `${day}/${month}/${year}` : "-";
};

const formatTime = (isoDateTime) => {
  if (!isoDateTime) return "-";
  const date = new Date(isoDateTime);
  if (Number.isNaN(date.getTime())) return "-";
  return date.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });
};

const showState = (message, isError = false) => {
  if (!reportState) return;
  reportState.hidden = false;
  reportState.textContent = message;
  reportState.classList.toggle("is-error", isError);
  if (summaryCard) summaryCard.hidden = true;
  if (reportsGrid) reportsGrid.hidden = true;
};

const hideState = () => {
  if (!reportState) return;
  reportState.hidden = true;
  reportState.textContent = "";
  reportState.classList.remove("is-error");
  if (summaryCard) summaryCard.hidden = false;
  if (reportsGrid) reportsGrid.hidden = false;
};

const addSummaryMetric = (label, value, variant = "") => {
  if (!summaryCard || !summaryItemTemplate) return;
  const node = summaryItemTemplate.content.cloneNode(true);
  const labelElement = node.querySelector('[data-field="label"]');
  const valueElement = node.querySelector('[data-field="value"]');
  if (!labelElement || !valueElement) return;
  labelElement.textContent = label;
  valueElement.textContent = value;
  if (variant) valueElement.classList.add(variant);
  summaryCard.appendChild(node);
};

const createReportCard = (title, items, styleConfig) => {
  if (!reportCardTemplate || !reportRowTemplate || !reportEmptyTemplate) return null;
  const node = reportCardTemplate.content.cloneNode(true);
  const cardElement = node.querySelector(".report-card");
  const headerElement = node.querySelector('[data-field="header"]');
  const titleElement = node.querySelector('[data-field="title"]');
  const countElement = node.querySelector('[data-field="count"]');
  const listElement = node.querySelector('[data-field="list"]');
  if (!cardElement || !headerElement || !titleElement || !countElement || !listElement) return null;

  titleElement.textContent = title;
  countElement.textContent = `${items.length} itens`;
  if (styleConfig.cardClass) {
    cardElement.classList.add(styleConfig.cardClass);
  }
  if (styleConfig.headerClass) {
    headerElement.classList.add(styleConfig.headerClass);
  }
  if (styleConfig.countClass) {
    countElement.classList.add(styleConfig.countClass);
  }
  if (styleConfig.listClass) {
    listElement.classList.add(styleConfig.listClass);
  }

  if (!items.length) {
    const emptyNode = reportEmptyTemplate.content.cloneNode(true);
    const emptyElement = emptyNode.querySelector('[data-field="empty"]');
    if (emptyElement && styleConfig.emptyClass) {
      emptyElement.classList.add(styleConfig.emptyClass);
    }
    listElement.appendChild(emptyNode);
  }

  items.forEach((item) => {
    const row = reportRowTemplate.content.cloneNode(true);
    const rowElement = row.querySelector('[data-field="row"]');
    const labelElement = row.querySelector('[data-field="label"]');
    const valueElement = row.querySelector('[data-field="value"]');
    if (!rowElement || !labelElement || !valueElement) return;
    if (styleConfig.rowClass) {
      rowElement.classList.add(styleConfig.rowClass);
    }
    labelElement.textContent = `${formatDate(item.data)} ${formatTime(item.horarioCriacao)}`;
    valueElement.textContent = formatCurrency(item.valor);
    listElement.appendChild(row);
  });

  return node;
};

const renderRelatorio = () => {
  if (!summaryCard || !reportsGrid || !state.relatorio) return;
  summaryCard.innerHTML = "";
  reportsGrid.innerHTML = "";

  const relatorio = state.relatorio;
  const utilizadoNumero = Number(relatorio.utilizadoPercentual || 0);
  addSummaryMetric("Total de receitas", formatCurrency(relatorio.totalReceitas), "positive");
  addSummaryMetric("Total de despesas", formatCurrency(relatorio.totalDespesas), "negative");
  addSummaryMetric("Orcamento", formatCurrency(relatorio.orcamento));
  addSummaryMetric("Utilizado", formatPercent(utilizadoNumero));
  addSummaryMetric(
    "Saldo final",
    formatCurrency(relatorio.saldoFinal),
    Number(relatorio.saldoFinal || 0) < 0 ? "negative" : "positive",
  );

  const receitas = Array.isArray(relatorio.receitas) ? relatorio.receitas : [];
  const despesas = Array.isArray(relatorio.despesas) ? relatorio.despesas : [];

  const receitasCard = createReportCard("Receitas", receitas, CARD_STYLE_CONFIG.receitas);
  const despesasCard = createReportCard("Despesas", despesas, CARD_STYLE_CONFIG.despesas);
  if (receitasCard) reportsGrid.appendChild(receitasCard);
  if (despesasCard) reportsGrid.appendChild(despesasCard);
  hideState();
};

const loadRelatorio = async () => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }
  showState("Carregando relatorios...");
  const response = await fetch(`/api/v1/relatorios/financeiro${buildRoleQuery()}`, {
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
    throw new Error("Acesso negado ao relatorio para a role selecionada.");
  }
  if (!response.ok) {
    throw new Error("Nao foi possivel carregar o relatorio financeiro.");
  }
  state.relatorio = await response.json();
  renderRelatorio();
};

const downloadPdf = async () => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }

  const response = await fetch(`/api/v1/relatorios/financeiro/pdf${buildRoleQuery()}`, {
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
    throw new Error("Acesso negado para gerar PDF da role selecionada.");
  }
  if (!response.ok) {
    throw new Error("Nao foi possivel gerar o PDF.");
  }

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "relatorio-financeiro.pdf";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};

const loadRoleFilterOptions = async () => {
  if (!roleFilterBox || !roleFilterSelect) return;
  const accessToken = getAccessToken();
  if (!accessToken) return;

  const response = await fetch("/api/v1/relatorios/roles", {
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
  if (!downloadReportButton) return;
  downloadReportButton.addEventListener("click", async () => {
    try {
      await downloadPdf();
    } catch (error) {
      showState("Erro ao gerar PDF. Tente novamente.", true);
    }
  });

  if (roleFilterSelect) {
    roleFilterSelect.addEventListener("change", async () => {
      state.selectedRole = roleFilterSelect.value || "";
      try {
        await loadRelatorio();
      } catch (error) {
        showState("Erro ao carregar relatorios. Tente novamente.", true);
      }
    });
  }
};

const init = async () => {
  bindEvents();
  try {
    await loadRoleFilterOptions();
  } catch (error) {
    removeRoleFilterBox();
  }
  try {
    await loadRelatorio();
  } catch (error) {
    showState("Erro ao carregar relatorios. Tente novamente.", true);
  }
};

init();
