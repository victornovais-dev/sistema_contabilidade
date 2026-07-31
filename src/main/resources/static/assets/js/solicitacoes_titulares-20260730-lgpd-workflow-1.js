(() => {
  "use strict";

  const TYPE_LABELS = Object.freeze({
    ACESSO: "Acesso aos dados",
    EXPORTACAO: "Exportação",
    CORRECAO: "Correção",
    DESATIVACAO: "Desativação",
    ANONIMIZACAO: "Anonimização",
    EXCLUSAO: "Exclusão",
    OPOSICAO: "Oposição",
    INFORMACOES_COMPARTILHAMENTO: "Compartilhamento",
  });

  const STATUS_LABELS = Object.freeze({
    RECEBIDA: "Recebida",
    IDENTIDADE_PENDENTE: "Identidade pendente",
    EM_ANALISE: "Em análise",
    RETENCAO_LEGAL: "Retenção legal",
    PRONTA_EXECUCAO: "Pronta para execução",
    CONCLUIDA: "Concluída",
    PARCIALMENTE_ATENDIDA: "Parcialmente atendida",
    NEGADA: "Negada",
    CANCELADA: "Cancelada",
  });

  const STATUS_FLOW = Object.freeze({
    RECEBIDA: ["IDENTIDADE_PENDENTE", "EM_ANALISE", "CANCELADA"],
    IDENTIDADE_PENDENTE: ["EM_ANALISE", "CANCELADA"],
    EM_ANALISE: ["RETENCAO_LEGAL", "PRONTA_EXECUCAO", "NEGADA", "PARCIALMENTE_ATENDIDA", "CANCELADA"],
    RETENCAO_LEGAL: ["EM_ANALISE", "PRONTA_EXECUCAO", "NEGADA"],
    PRONTA_EXECUCAO: ["CONCLUIDA", "PARCIALMENTE_ATENDIDA", "RETENCAO_LEGAL"],
    PARCIALMENTE_ATENDIDA: ["CONCLUIDA", "RETENCAO_LEGAL"],
    CONCLUIDA: [],
    NEGADA: [],
    CANCELADA: [],
  });

  const TYPE_VALUES = Object.keys(TYPE_LABELS);
  const STATUS_VALUES = Object.keys(STATUS_LABELS);
  const CLOSED_STATUSES = new Set(["CONCLUIDA", "PARCIALMENTE_ATENDIDA", "NEGADA", "CANCELADA"]);

  const elements = Object.freeze({
    filters: document.querySelector("#request-filters"),
    search: document.querySelector("#request-search"),
    statusFilter: document.querySelector("#status-filter"),
    typeFilter: document.querySelector("#type-filter"),
    clearFilters: document.querySelector("#clear-filters"),
    feedback: document.querySelector("#requests-feedback"),
    table: document.querySelector("#requests-table"),
    empty: document.querySelector("#empty-state"),
    pagination: document.querySelector("#pagination"),
    previous: document.querySelector("#previous-page"),
    next: document.querySelector("#next-page"),
    pageSummary: document.querySelector("#page-summary"),
    metricOpen: document.querySelector("#metric-open"),
    metricDue: document.querySelector("#metric-due"),
    metricOverdue: document.querySelector("#metric-overdue"),
    metricCompleted: document.querySelector("#metric-completed"),
    exportButton: document.querySelector("#export-button"),
    drawer: document.querySelector("#request-drawer"),
    drawerProtocol: document.querySelector("#drawer-protocol"),
    drawerFeedback: document.querySelector("#drawer-feedback"),
    details: document.querySelector("#request-details"),
    description: document.querySelector("#request-description"),
    holdAlert: document.querySelector("#hold-alert"),
    holdAlertReason: document.querySelector("#hold-alert-reason"),
    workflowForm: document.querySelector("#workflow-form"),
    workflowStatus: document.querySelector("#workflow-status"),
    workflowNote: document.querySelector("#workflow-note"),
    workflowSubmit: document.querySelector("#workflow-submit"),
    identityButton: document.querySelector("#identity-button"),
    ownerForm: document.querySelector("#owner-form"),
    ownerInput: document.querySelector("#owner-input"),
    holdReason: document.querySelector("#hold-reason"),
    holdButton: document.querySelector("#hold-button"),
    timeline: document.querySelector("#request-timeline"),
    toastContainer: document.querySelector("#toast-container"),
  });

  const state = {
    page: 0,
    totalPages: 0,
    loading: false,
    requests: [],
    currentRequest: null,
  };

  const authHeaders = () => {
    const token = localStorage.getItem("sc_access_token");
    return token ? { Authorization: `Bearer ${token}` } : {};
  };

  const handleAuthorizationFailure = (response) => {
    if (response.status === 401) {
      window.location.href = "/login";
      return true;
    }
    if (response.status === 403) {
      window.location.href = "/404";
      return true;
    }
    return false;
  };

  const fetchCsrfToken = async () => {
    const response = await fetch("/api/v1/auth/csrf", {
      credentials: "same-origin",
      headers: authHeaders(),
    });
    if (!response.ok) throw new Error("Não foi possível validar a alteração.");
    const payload = await response.json();
    if (!payload?.token) throw new Error("Token de segurança ausente.");
    return payload.token;
  };

  const fetchAdmin = async (path, options = {}) => {
    const response = await fetch(path, {
      credentials: "same-origin",
      ...options,
      headers: {
        Accept: "application/json",
        ...authHeaders(),
        ...(options.headers || {}),
      },
    });
    if (handleAuthorizationFailure(response)) throw new Error("Acesso encerrado.");
    return response;
  };

  const patchRequest = async (payload) => {
    const csrfToken = await fetchCsrfToken();
    const response = await fetchAdmin(
      `/api/v1/solicitacoes-privacidade/${encodeURIComponent(state.currentRequest.protocolo)}`,
      {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-TOKEN": csrfToken,
        },
        body: JSON.stringify(payload),
      },
    );
    if (!response.ok) {
      throw new Error(
        response.status === 409
          ? "Transição não permitida para o status atual."
          : "Não foi possível atualizar a solicitação.",
      );
    }
    state.currentRequest = await response.json();
    renderDrawer();
    await loadRequests();
  };

  const appendOption = (select, value, label) => {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = label;
    select.append(option);
  };

  STATUS_VALUES.forEach((status) => appendOption(elements.statusFilter, status, STATUS_LABELS[status]));
  TYPE_VALUES.forEach((type) => appendOption(elements.typeFilter, type, TYPE_LABELS[type]));

  const createElement = (tag, className = "", text = "") => {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text !== "") element.textContent = text;
    return element;
  };

  const stackedCell = (primary, secondary) => {
    const cell = createElement("div", "stacked-cell");
    cell.append(createElement("strong", "", primary), createElement("small", "", secondary || "—"));
    return cell;
  };

  const badgeClass = (status) => {
    if (status === "CONCLUIDA") return "badge completed";
    if (status === "RETENCAO_LEGAL" || status === "IDENTIDADE_PENDENTE") return "badge warning";
    if (status === "NEGADA" || status === "CANCELADA") return "badge danger";
    return "badge";
  };

  const daysUntil = (dateValue) => {
    const target = new Date(`${dateValue}T12:00:00`);
    const today = new Date();
    today.setHours(12, 0, 0, 0);
    return Math.ceil((target.getTime() - today.getTime()) / 86400000);
  };

  const deadlineCell = (request) => {
    const wrapper = createElement("div", "deadline");
    wrapper.append(createElement("strong", "", formatDate(request.prazo)));
    const days = daysUntil(request.prazo);
    if (CLOSED_STATUSES.has(request.status)) {
      wrapper.append(createElement("small", "", "Encerrada"));
    } else if (days < 0) {
      wrapper.append(createElement("span", "badge danger", "Atrasada"));
    } else if (days <= 3) {
      wrapper.append(createElement("span", "badge warning", "Prazo próximo"));
    } else {
      wrapper.append(createElement("small", "", `${days} dias restantes`));
    }
    return wrapper;
  };

  const renderRow = (request) => {
    const row = document.createElement("tr");
    const protocol = document.createElement("td");
    protocol.append(createElement("span", "protocol", request.protocolo));
    const person = document.createElement("td");
    person.append(stackedCell(request.nome, request.email));
    const organization = document.createElement("td");
    organization.textContent = request.organizacao;
    const type = document.createElement("td");
    type.textContent = TYPE_LABELS[request.tipo] || request.tipo;
    const status = document.createElement("td");
    status.append(createElement("span", badgeClass(request.status), STATUS_LABELS[request.status]));
    const deadline = document.createElement("td");
    deadline.append(deadlineCell(request));
    const owner = document.createElement("td");
    owner.textContent = request.responsavel;
    const actions = document.createElement("td");
    const openButton = createElement("button", "button button-secondary row-button", "Detalhes");
    openButton.type = "button";
    openButton.addEventListener("click", () => void openDrawer(request.protocolo));
    actions.append(openButton);
    row.append(protocol, person, organization, type, status, deadline, owner, actions);
    return row;
  };

  const updateMetrics = (summary) => {
    elements.metricOpen.textContent = formatNumber(summary?.abertas || 0);
    elements.metricDue.textContent = formatNumber(summary?.prazoProximo || 0);
    elements.metricOverdue.textContent = formatNumber(summary?.atrasadas || 0);
    elements.metricCompleted.textContent = formatNumber(summary?.concluidas || 0);
  };

  const updatePagination = (payload) => {
    state.totalPages = payload.totalPaginas;
    elements.pagination.hidden = payload.totalPaginas <= 1;
    elements.previous.disabled = payload.pagina <= 0;
    elements.next.disabled = payload.pagina + 1 >= payload.totalPaginas;
    elements.pageSummary.textContent =
      payload.totalPaginas > 0
        ? `Página ${payload.pagina + 1} de ${payload.totalPaginas}`
        : "";
  };

  const syncUrl = () => {
    const params = new URLSearchParams();
    if (elements.search.value.trim()) params.set("termo", elements.search.value.trim());
    if (elements.statusFilter.value) params.set("status", elements.statusFilter.value);
    if (elements.typeFilter.value) params.set("tipo", elements.typeFilter.value);
    if (state.page > 0) params.set("pagina", String(state.page));
    const query = params.toString();
    history.replaceState(null, "", query ? `${window.location.pathname}?${query}` : window.location.pathname);
  };

  const loadRequests = async () => {
    if (state.loading) return;
    state.loading = true;
    elements.feedback.textContent = "Carregando solicitações…";
    elements.feedback.classList.remove("error");
    const params = new URLSearchParams({ pagina: String(state.page), tamanho: "12" });
    if (elements.search.value.trim()) params.set("termo", elements.search.value.trim());
    if (elements.statusFilter.value) params.set("status", elements.statusFilter.value);
    if (elements.typeFilter.value) params.set("tipo", elements.typeFilter.value);

    try {
      const response = await fetchAdmin(`/api/v1/solicitacoes-privacidade?${params}`);
      if (!response.ok) throw new Error("Não foi possível carregar as solicitações.");
      const payload = await response.json();
      state.requests = Array.isArray(payload.solicitacoes) ? payload.solicitacoes : [];
      elements.table.replaceChildren(...state.requests.map(renderRow));
      elements.empty.hidden = state.requests.length > 0;
      updateMetrics(payload.resumo);
      updatePagination(payload);
      syncUrl();
      elements.feedback.textContent =
        state.requests.length === 1
          ? "1 solicitação exibida."
          : `${state.requests.length} solicitações exibidas nesta página.`;
    } catch (error) {
      if (error.message === "Acesso encerrado.") return;
      elements.table.replaceChildren();
      elements.empty.hidden = true;
      elements.pagination.hidden = true;
      elements.feedback.textContent = error.message || "Falha ao carregar solicitações.";
      elements.feedback.classList.add("error");
    } finally {
      state.loading = false;
    }
  };

  const addDetail = (label, value) => {
    const item = createElement("div", "detail-item");
    item.append(createElement("dt", "", label), createElement("dd", "", value || "—"));
    elements.details.append(item);
  };

  const renderTimeline = (events) => {
    const items = (events || []).map((event) => {
      const item = createElement("div", "timeline-item");
      const content = document.createElement("div");
      content.append(
        createElement("strong", "", event.titulo),
        createElement("p", "", event.descricao),
        createElement("small", "", `${event.ator} · ${formatDateTime(event.ocorridoEm)}`),
      );
      item.append(createElement("span", "timeline-dot"), content);
      return item;
    });
    elements.timeline.replaceChildren(...items);
  };

  const renderWorkflowOptions = (request) => {
    elements.workflowStatus.replaceChildren();
    const transitions = (STATUS_FLOW[request.status] || []).filter(
      (status) => status !== "RETENCAO_LEGAL",
    );
    if (transitions.length === 0) {
      appendOption(elements.workflowStatus, "", "Sem transições disponíveis");
      elements.workflowStatus.disabled = true;
      elements.workflowSubmit.disabled = true;
      return;
    }
    transitions.forEach((status) => appendOption(elements.workflowStatus, status, STATUS_LABELS[status]));
    elements.workflowStatus.disabled = false;
    elements.workflowSubmit.disabled = false;
  };

  const renderDrawer = () => {
    const request = state.currentRequest;
    elements.drawerProtocol.textContent = request.protocolo;
    elements.details.replaceChildren();
    addDetail("Titular", request.nome);
    addDetail("E-mail", request.email);
    addDetail("Organização", request.organizacao);
    addDetail("Tipo", TYPE_LABELS[request.tipo] || request.tipo);
    addDetail("Status", STATUS_LABELS[request.status] || request.status);
    addDetail("Prazo", formatDate(request.prazo));
    addDetail("Canal de resposta", request.canalResposta);
    addDetail("Identidade", request.identidadeVerificada ? "Verificada" : "Pendente");
    addDetail("Vínculo", request.vinculo);
    addDetail("Escopos", (request.escopos || []).join(", "));
    addDetail("Referência", request.referencia);
    addDetail("Recebida em", formatDate(request.recebidaEm));
    elements.description.textContent = request.descricao;
    elements.holdAlert.hidden = !request.retencaoLegal;
    elements.holdAlertReason.textContent = request.motivoRetencao || "Motivo não informado.";
    elements.holdReason.value = request.motivoRetencao || "";
    elements.holdButton.textContent = request.retencaoLegal ? "Liberar retenção" : "Aplicar retenção";
    elements.holdButton.classList.toggle("button-danger", request.retencaoLegal);
    elements.identityButton.textContent = request.identidadeVerificada
      ? "Reabrir validação"
      : "Confirmar identidade";
    elements.ownerInput.value = request.responsavel;
    elements.workflowNote.value = "";
    renderWorkflowOptions(request);
    renderTimeline(request.eventos);
  };

  const openDrawer = async (protocol) => {
    elements.drawer.hidden = false;
    document.body.classList.add("no-scroll");
    elements.drawerProtocol.textContent = protocol;
    elements.drawerFeedback.textContent = "Carregando detalhes…";
    try {
      const response = await fetchAdmin(
        `/api/v1/solicitacoes-privacidade/${encodeURIComponent(protocol)}`,
      );
      if (!response.ok) throw new Error("Não foi possível carregar os detalhes.");
      state.currentRequest = await response.json();
      elements.drawerFeedback.textContent = "";
      renderDrawer();
    } catch (error) {
      elements.drawerFeedback.textContent = error.message || "Falha ao carregar detalhes.";
      elements.drawerFeedback.classList.add("error");
    }
  };

  const closeDrawer = () => {
    elements.drawer.hidden = true;
    document.body.classList.remove("no-scroll");
    state.currentRequest = null;
  };

  const runUpdate = async (payload, successMessage) => {
    elements.drawerFeedback.textContent = "Salvando alteração…";
    elements.drawerFeedback.classList.remove("error");
    try {
      await patchRequest(payload);
      elements.drawerFeedback.textContent = successMessage;
      showToast(successMessage);
    } catch (error) {
      elements.drawerFeedback.textContent = error.message || "Falha ao atualizar.";
      elements.drawerFeedback.classList.add("error");
    }
  };

  const exportCsv = () => {
    if (state.requests.length === 0) {
      showToast("Nenhuma solicitação nesta página para exportar.");
      return;
    }
    if (!window.confirm("O arquivo contém dados pessoais. Confirme que o destino é autorizado e protegido.")) {
      return;
    }
    const headers = ["Protocolo", "Titular", "Email", "Organizacao", "Tipo", "Status", "Prazo", "Responsavel"];
    const rows = state.requests.map((request) => [
      request.protocolo,
      request.nome,
      request.email,
      request.organizacao,
      TYPE_LABELS[request.tipo],
      STATUS_LABELS[request.status],
      request.prazo,
      request.responsavel,
    ]);
    const csv = [headers, ...rows].map((row) => row.map(csvValue).join(";")).join("\n");
    const blob = new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `solicitacoes-lgpd-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.append(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };

  const csvValue = (value) => {
    let safe = String(value || "");
    if (/^[=+\-@]/.test(safe)) safe = `'${safe}`;
    return `"${safe.replaceAll('"', '""')}"`;
  };

  const formatDate = (value) => {
    const date = new Date(`${value}T12:00:00`);
    return Number.isNaN(date.getTime())
      ? "—"
      : new Intl.DateTimeFormat("pt-BR", { dateStyle: "short" }).format(date);
  };

  const formatDateTime = (value) => {
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? "—"
      : new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(date);
  };

  const formatNumber = (value) => new Intl.NumberFormat("pt-BR").format(value);

  const showToast = (message) => {
    const toast = createElement("div", "toast", message);
    elements.toastContainer.append(toast);
    window.setTimeout(() => toast.remove(), 3200);
  };

  const hydrateFilters = () => {
    const params = new URLSearchParams(window.location.search);
    elements.search.value = params.get("termo") || "";
    const status = params.get("status") || "";
    const type = params.get("tipo") || "";
    elements.statusFilter.value = STATUS_VALUES.includes(status) ? status : "";
    elements.typeFilter.value = TYPE_VALUES.includes(type) ? type : "";
    const page = Number.parseInt(params.get("pagina") || "0", 10);
    state.page = Number.isFinite(page) ? Math.max(page, 0) : 0;
  };

  elements.filters.addEventListener("submit", (event) => {
    event.preventDefault();
    state.page = 0;
    void loadRequests();
  });
  elements.statusFilter.addEventListener("change", () => {
    state.page = 0;
    void loadRequests();
  });
  elements.typeFilter.addEventListener("change", () => {
    state.page = 0;
    void loadRequests();
  });
  elements.clearFilters.addEventListener("click", () => {
    elements.search.value = "";
    elements.statusFilter.value = "";
    elements.typeFilter.value = "";
    state.page = 0;
    void loadRequests();
  });
  elements.previous.addEventListener("click", () => {
    if (state.page <= 0) return;
    state.page -= 1;
    void loadRequests();
  });
  elements.next.addEventListener("click", () => {
    if (state.page + 1 >= state.totalPages) return;
    state.page += 1;
    void loadRequests();
  });
  elements.exportButton.addEventListener("click", exportCsv);
  document.querySelectorAll("[data-close-drawer]").forEach((button) => {
    button.addEventListener("click", closeDrawer);
  });
  elements.workflowForm.addEventListener("submit", (event) => {
    event.preventDefault();
    void runUpdate(
      {
        status: elements.workflowStatus.value,
        observacao: elements.workflowNote.value || null,
      },
      "Status e auditoria atualizados.",
    );
  });
  elements.identityButton.addEventListener("click", () => {
    void runUpdate(
      { identidadeVerificada: !state.currentRequest.identidadeVerificada },
      "Situação da identidade atualizada.",
    );
  });
  elements.ownerForm.addEventListener("submit", (event) => {
    event.preventDefault();
    void runUpdate({ responsavel: elements.ownerInput.value }, "Responsável atualizado.");
  });
  elements.holdButton.addEventListener("click", () => {
    if (!state.currentRequest.retencaoLegal && !elements.holdReason.value.trim()) {
      elements.drawerFeedback.textContent = "Informe o motivo antes de aplicar a retenção.";
      elements.drawerFeedback.classList.add("error");
      elements.holdReason.focus();
      return;
    }
    void runUpdate(
      {
        retencaoLegal: !state.currentRequest.retencaoLegal,
        motivoRetencao: elements.holdReason.value || null,
      },
      state.currentRequest.retencaoLegal ? "Retenção liberada." : "Retenção aplicada.",
    );
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !elements.drawer.hidden) closeDrawer();
  });

  hydrateFilters();
  void loadRequests();
})();
