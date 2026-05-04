const state = {
  relatorio: null,
  selectedRole: "",
};

const REFRESH_ANIMATION_MS = 180;

const summaryLayout = document.getElementById("summary-layout");
const summaryDespesasCard = document.getElementById("summary-despesas-card");
const summaryLimitesCard = document.getElementById("summary-limites-card");
const summaryOverviewCard = document.getElementById("summary-overview-card");
const summaryDespesasTitle = summaryDespesasCard?.querySelector(".summary-card-title") || null;
const summaryLimitesTitle = summaryLimitesCard?.querySelector(".summary-card-title") || null;
const summaryOverviewTitle = summaryOverviewCard?.querySelector(".summary-card-title") || null;
const reportState = document.getElementById("report-state");
const downloadReportButton = document.getElementById("download-report-btn");
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
            await loadRelatorio({ preserveVisibleContent: Boolean(state.relatorio) });
          } catch (error) {
            setRefreshing(false);
            showState("Erro ao carregar relatÃ³rios. Tente novamente.", true);
          }
        },
      })
    : null;

const summaryItemTemplate = document.getElementById("summary-item-template");
const getAccessToken = () => localStorage.getItem("sc_access_token");
const wait = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms));

const buildRoleQuery = () => {
  if (!state.selectedRole) return "";
  const params = new URLSearchParams({ role: state.selectedRole });
  return `?${params.toString()}`;
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

const formatFullCurrency = (value) =>
  Number(value || 0).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

const formatPercent = (ratio, fractionDigits = 0) => {
  const numeric = Number(ratio);
  if (!Number.isFinite(numeric)) {
    return "0%";
  }
  return numeric.toLocaleString("pt-BR", {
    style: "percent",
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
};

const clamp01 = (value) => Math.min(1, Math.max(0, Number(value)));

const utilizedHue = (ratio) => {
  const t = clamp01(ratio);
  return (1 - t) * 120;
};

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

const formatDescricao = (value) => {
  const key = String(value || "").trim().toUpperCase();
  const labels = {
    ALUGUEL: "Aluguel",
    ENERGIA: "Energia elÃ©trica",
    AGUA: "Ãgua",
    SERVICOS: "ServiÃ§os",
    IMPOSTOS: "Impostos",
    MATERIAIS: "Materiais",
    OUTROS: "Outros",
  };
  return labels[key] || (key ? String(value) : "-");
};

const showState = (message, isError = false) => {
  if (!reportState) return;
  reportState.hidden = false;
  reportState.textContent = message;
  reportState.classList.toggle("is-error", isError);
  if (summaryLayout) summaryLayout.hidden = true;
  if (summaryDespesasCard) summaryDespesasCard.hidden = true;
  if (summaryLimitesCard) summaryLimitesCard.hidden = true;
  if (summaryOverviewCard) summaryOverviewCard.hidden = true;
};

const hideState = () => {
  if (!reportState) return;
  reportState.hidden = true;
  reportState.textContent = "";
  reportState.classList.remove("is-error");
  if (summaryLayout) summaryLayout.hidden = false;
  if (summaryDespesasCard) summaryDespesasCard.hidden = false;
  if (summaryLimitesCard) summaryLimitesCard.hidden = false;
  if (summaryOverviewCard) summaryOverviewCard.hidden = false;
};

const setRefreshing = (refreshing) => {
  [summaryLayout].forEach((element) => {
    if (!element) return;
    element.classList.toggle("is-refreshing", refreshing);
  });
};

const clearSummaryCards = () => {
  [summaryDespesasCard, summaryLimitesCard, summaryOverviewCard].forEach((card) => {
    if (card) {
      card.innerHTML = "";
    }
  });
  if (summaryDespesasCard && summaryDespesasTitle) {
    summaryDespesasCard.appendChild(summaryDespesasTitle);
  }
  if (summaryLimitesCard && summaryLimitesTitle) {
    summaryLimitesCard.appendChild(summaryLimitesTitle);
  }
  if (summaryOverviewCard && summaryOverviewTitle) {
    summaryOverviewCard.appendChild(summaryOverviewTitle);
  }
};

const addSummaryMetric = (container, label, value, options = {}) => {
  if (!container || !summaryItemTemplate) return;
  const { variant = "", color = "", styleVars = null } = options || {};
  const node = summaryItemTemplate.content.cloneNode(true);
  const labelElement = node.querySelector('[data-field="label"]');
  const valueElement = node.querySelector('[data-field="value"]');
  if (!labelElement || !valueElement) return;
  labelElement.textContent = label;
  valueElement.textContent = value;
  if (variant) valueElement.classList.add(variant);
  if (color) valueElement.style.color = color;
  if (styleVars && typeof styleVars === "object") {
    Object.entries(styleVars).forEach(([key, value]) => {
      if (!key) return;
      valueElement.style.setProperty(String(key), String(value));
    });
  }
  container.appendChild(node);
};

const getLimitBarSize = (value, maxValue) => {
  const numericValue = Number(value || 0);
  const numericMax = Number(maxValue || 0);
  if (numericValue <= 0 || numericMax <= 0) return "0%";
  return `${Math.max(4, Math.min(100, (numericValue / numericMax) * 100)).toFixed(2)}%`;
};

const addLimitBar = (bars, label, value, maxValue, variant) => {
  const bar = document.createElement("div");
  bar.className = `limit-bar ${variant}`;

  const header = document.createElement("div");
  header.className = "limit-bar-header";

  const labelElement = document.createElement("span");
  labelElement.textContent = label;

  const valueElement = document.createElement("strong");
  valueElement.textContent = formatFullCurrency(value);

  const track = document.createElement("div");
  track.className = "limit-bar-track";

  const fill = document.createElement("span");
  fill.className = `limit-bar-fill ${variant}`;
  fill.style.height = getLimitBarSize(value, maxValue);

  header.append(labelElement, valueElement);
  track.appendChild(fill);
  bar.append(header, track);
  bars.appendChild(bar);
};

const addExpenseLimitChart = (container, category) => {
  if (!container || !category) return;
  const spent = Number(category.spent || 0);
  const ceiling = Number(category.ceiling || 0);
  const maxValue = Math.max(spent, ceiling);
  const isOverLimit = ceiling > 0 && spent > ceiling;

  const row = document.createElement("article");
  row.className = "expense-limit-row";
  row.classList.toggle("is-over-limit", isOverLimit);
  row.classList.toggle("is-long-title", category.label === "LocaÃ§Ã£o de VeÃ­culos");

  const header = document.createElement("div");
  header.className = "expense-limit-header";

  const titleRow = document.createElement("div");
  titleRow.className = "expense-limit-title-row";

  const title = document.createElement("h3");
  title.textContent = category.label;

  const ratio = document.createElement("span");
  ratio.className = "expense-limit-ratio";
  ratio.textContent = category.limitText;

  const bars = document.createElement("div");
  bars.className = "expense-limit-bars";

  addLimitBar(bars, "Gasto", spent, maxValue, isOverLimit ? "is-danger" : "is-spent");
  addLimitBar(bars, "Teto", ceiling, maxValue, "is-ceiling");

  titleRow.appendChild(title);
  if (isOverLimit) {
    const overLimit = document.createElement("span");
    overLimit.className = "expense-limit-over-limit";
    overLimit.textContent = "Limite excedido";
    titleRow.appendChild(overLimit);
  }

  header.append(titleRow, ratio);
  row.append(header, bars);
  container.appendChild(row);
};

const renderRelatorio = () => {
  if (!summaryLayout || !state.relatorio) return;
  clearSummaryCards();

  const relatorio = state.relatorio;
  const receitasFinanceiras = Number(relatorio.receitasFinanceiras || 0);
  const receitasEstimaveis = Number(relatorio.receitasEstimaveis || 0);
  const receitasTotais = Number(relatorio.totalReceitas || 0);
  const despesasTotais = Number(relatorio.totalDespesas || 0);
  const despesasAdvocaciaContabilidade = Number(relatorio.despesasAdvocaciaContabilidade || 0);
  const despesasConsideradas = Number(relatorio.despesasConsideradas || 0);
  const despesasTotaisResumo = Number(relatorio.despesasTotaisResumo || 0);
  const despesasCombustivel = Number(relatorio.despesasCombustivel || 0);
  const despesasAlimentacao = Number(relatorio.despesasAlimentacao || 0);
  const despesasLocacaoVeiculos = Number(relatorio.despesasLocacaoVeiculos || 0);
  const tetoCombustivel = Number(relatorio.tetoGastosCombustivel || 0);
  const tetoAlimentacao = Number(relatorio.tetoGastosAlimentacao || 0);
  const tetoLocacaoVeiculos = Number(relatorio.tetoGastosLocacaoVeiculos || 0);
  const utilizadoRatio = Number(relatorio.utilizadoRatio || 0);

  addSummaryMetric(summaryOverviewCard, "Financeiras", formatCurrency(receitasFinanceiras), {
    variant: "positive",
  });
  addSummaryMetric(summaryOverviewCard, "EstimÃ¡veis", formatCurrency(receitasEstimaveis), {
    variant: "positive",
  });
  addSummaryMetric(summaryOverviewCard, "Totais", formatCurrency(receitasTotais), {
    variant: "positive",
  });
  addSummaryMetric(summaryOverviewCard, "Utilizado", formatPercent(utilizadoRatio), {
    variant: "utilizado",
    styleVars: {
      "--util-hue": utilizedHue(utilizadoRatio).toFixed(1),
      "--util-hue2": Math.max(0, utilizedHue(utilizadoRatio) - 18).toFixed(1),
    },
  });
  addSummaryMetric(summaryOverviewCard, "Saldo final", formatCurrency(relatorio.saldoFinal), {
    variant: Number(relatorio.saldoFinal || 0) < 0 ? "negative" : "positive",
  });

  addSummaryMetric(summaryDespesasCard, "Considerada", formatCurrency(despesasConsideradas), {
    variant: "negative",
  });
  addSummaryMetric(summaryDespesasCard, "Estim\u00e1veis", formatCurrency(receitasEstimaveis), {
    variant: "negative",
  });
  addSummaryMetric(
    summaryDespesasCard,
    "Advocacia e contabilidade",
    formatCurrency(despesasAdvocaciaContabilidade),
    {
      variant: "negative",
    },
  );
  addSummaryMetric(summaryDespesasCard, "Total", formatCurrency(despesasTotaisResumo), {
    variant: "negative",
  });

  addExpenseLimitChart(summaryLimitesCard, {
    label: "CombustÃ­vel",
    spent: despesasCombustivel,
    ceiling: tetoCombustivel,
    limitText: "Limite 10%",
  });
  addExpenseLimitChart(summaryLimitesCard, {
    label: "AlimentaÃ§Ã£o",
    spent: despesasAlimentacao,
    ceiling: tetoAlimentacao,
    limitText: "Limite 10%",
  });
  addExpenseLimitChart(summaryLimitesCard, {
    label: "LocaÃ§Ã£o de VeÃ­culos",
    spent: despesasLocacaoVeiculos,
    ceiling: tetoLocacaoVeiculos,
    limitText: "Limite 20%",
  });

  hideState();
};

const loadRelatorio = async ({ preserveVisibleContent = false } = {}) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }
  if (preserveVisibleContent) {
    setRefreshing(true);
    await wait(REFRESH_ANIMATION_MS);
  } else {
    showState("Carregando relatÃ³rios...");
  }
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
    throw new Error("Acesso negado ao relatÃ³rio para o candidato selecionado.");
  }
  if (!response.ok) {
    throw new Error("NÃ£o foi possÃ­vel carregar o relatÃ³rio financeiro.");
  }
  state.relatorio = await response.json();
  renderRelatorio();
  requestAnimationFrame(() => {
    setRefreshing(false);
  });
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
    throw new Error("Acesso negado para gerar PDF do candidato selecionado.");
  }
  if (!response.ok) {
    throw new Error("NÃ£o foi possÃ­vel gerar o PDF.");
  }

  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "relatorio-financeiro.pdf";
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
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
};

const init = async () => {
  bindEvents();
  const initialStoredRole = getStoredSelectedRole();
  if (initialStoredRole) {
    state.selectedRole = initialStoredRole;
  }

  const tryLoadRelatorio = async (options = {}) => {
    try {
      await loadRelatorio(options);
      return null;
    } catch (error) {
      return error;
    }
  };

  const initialReportPromise = initialStoredRole ? tryLoadRelatorio() : null;

  try {
    await loadRoleFilterOptions();
  } catch (error) {
    removeRoleFilterBox();
  }

  if (!initialStoredRole) {
    const loadError = await tryLoadRelatorio();
    if (loadError) {
      showState("Erro ao carregar relatórios. Tente novamente.", true);
    }
    return;
  }

  const initialLoadError = initialReportPromise ? await initialReportPromise : null;
  if (state.selectedRole !== initialStoredRole) {
    const retryError = await tryLoadRelatorio({ preserveVisibleContent: Boolean(state.relatorio) });
    if (retryError) {
      showState("Erro ao carregar relatórios. Tente novamente.", true);
    }
    return;
  }

  if (initialLoadError) {
    showState("Erro ao carregar relatórios. Tente novamente.", true);
  }
};

init();

