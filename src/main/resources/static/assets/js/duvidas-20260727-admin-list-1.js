(() => {
  const form = document.querySelector("#question-filters");
  const searchInput = document.querySelector("#question-search");
  const statusFilter = document.querySelector("#status-filter");
  const clearButton = document.querySelector("#clear-filters");
  const list = document.querySelector("#questions-list");
  const feedback = document.querySelector("#questions-feedback");
  const totalCount = document.querySelector("#total-count");
  const pagination = document.querySelector("#pagination");
  const previousButton = document.querySelector("#previous-page");
  const nextButton = document.querySelector("#next-page");
  const pageSummary = document.querySelector("#page-summary");
  const template = document.querySelector("#question-card-template");

  const state = {
    pagina: 0,
    totalPaginas: 0,
    carregando: false,
  };

  const authHeaders = () => {
    const token = localStorage.getItem("sc_access_token");
    return token ? { Authorization: `Bearer ${token}` } : {};
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

  const formatDate = (value) => {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "Data não informada";
    return new Intl.DateTimeFormat("pt-BR", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(date);
  };

  const setFeedback = (message = "", isError = false) => {
    feedback.textContent = message;
    feedback.classList.toggle("is-error", isError);
  };

  const renderEmptyState = () => {
    const empty = document.createElement("div");
    empty.className = "empty-state";
    const title = document.createElement("strong");
    title.textContent = "Nenhuma dúvida encontrada";
    const description = document.createElement("span");
    description.textContent = "Altere os filtros ou aguarde novos envios pela página pública.";
    empty.append(title, description);
    list.replaceChildren(empty);
  };

  const updateStatus = async (question, select, badge) => {
    const previousStatus = question.status;
    const nextStatus = select.value;
    if (nextStatus === previousStatus) return;
    select.disabled = true;
    setFeedback("Atualizando situação…");
    try {
      const csrfToken = await fetchCsrfToken();
      const response = await fetch(`/api/v1/duvidas/${question.protocolo}/status`, {
        method: "PATCH",
        credentials: "same-origin",
        headers: {
          ...authHeaders(),
          "Content-Type": "application/json",
          "X-CSRF-TOKEN": csrfToken,
        },
        body: JSON.stringify({ status: nextStatus }),
      });
      if (handleAuthorizationFailure(response)) return;
      if (!response.ok) throw new Error("Não foi possível atualizar a situação.");
      question.status = nextStatus;
      badge.textContent = nextStatus === "RESPONDIDA" ? "Respondida" : "Pendente";
      badge.classList.toggle("is-answered", nextStatus === "RESPONDIDA");
      setFeedback("Situação atualizada com sucesso.");
    } catch (error) {
      select.value = previousStatus;
      setFeedback(error.message || "Falha ao atualizar a situação.", true);
    } finally {
      select.disabled = false;
    }
  };

  const renderQuestion = (question) => {
    const fragment = template.content.cloneNode(true);
    const card = fragment.querySelector(".question-card");
    const badge = fragment.querySelector(".question-status");
    const name = fragment.querySelector(".question-name");
    const date = fragment.querySelector(".question-date");
    const message = fragment.querySelector(".question-message");
    const email = fragment.querySelector(".question-email");
    const protocol = fragment.querySelector(".question-protocol");
    const copyButton = fragment.querySelector(".copy-button");
    const statusSelect = fragment.querySelector(".question-status-select");

    const answered = question.status === "RESPONDIDA";
    badge.textContent = answered ? "Respondida" : "Pendente";
    badge.classList.toggle("is-answered", answered);
    name.textContent = question.nome;
    date.textContent = formatDate(question.recebidaEm);
    date.dateTime = question.recebidaEm;
    message.textContent = question.duvida;
    email.textContent = question.email;
    email.href = `mailto:${question.email}`;
    protocol.textContent = question.protocolo;
    statusSelect.value = question.status;
    card.dataset.protocol = question.protocolo;

    copyButton.addEventListener("click", async () => {
      try {
        await navigator.clipboard.writeText(question.protocolo);
        copyButton.textContent = "Copiado";
        window.setTimeout(() => {
          copyButton.textContent = "Copiar";
        }, 1400);
      } catch (error) {
        setFeedback("Não foi possível copiar o protocolo.", true);
      }
    });

    statusSelect.addEventListener("change", () => {
      void updateStatus(question, statusSelect, badge);
    });
    return fragment;
  };

  const updatePagination = (payload) => {
    state.totalPaginas = payload.totalPaginas;
    const visible = payload.totalPaginas > 1;
    pagination.hidden = !visible;
    previousButton.disabled = payload.pagina <= 0;
    nextButton.disabled = payload.pagina + 1 >= payload.totalPaginas;
    pageSummary.textContent = visible
      ? `Página ${payload.pagina + 1} de ${payload.totalPaginas}`
      : "";
  };

  const syncUrl = () => {
    const params = new URLSearchParams();
    if (searchInput.value.trim()) params.set("termo", searchInput.value.trim());
    if (statusFilter.value) params.set("status", statusFilter.value);
    if (state.pagina > 0) params.set("pagina", String(state.pagina));
    const query = params.toString();
    history.replaceState(null, "", query ? `/duvidas?${query}` : "/duvidas");
  };

  const loadQuestions = async () => {
    if (state.carregando) return;
    state.carregando = true;
    setFeedback("Carregando dúvidas…");
    const params = new URLSearchParams({
      pagina: String(state.pagina),
      tamanho: "12",
    });
    if (searchInput.value.trim()) params.set("termo", searchInput.value.trim());
    if (statusFilter.value) params.set("status", statusFilter.value);

    try {
      const response = await fetch(`/api/v1/duvidas?${params}`, {
        credentials: "same-origin",
        headers: authHeaders(),
      });
      if (handleAuthorizationFailure(response)) return;
      if (!response.ok) throw new Error("Não foi possível carregar as dúvidas.");
      const payload = await response.json();
      const questions = Array.isArray(payload.duvidas) ? payload.duvidas : [];
      totalCount.textContent = new Intl.NumberFormat("pt-BR").format(payload.totalElementos || 0);
      if (questions.length === 0) {
        renderEmptyState();
      } else {
        list.replaceChildren(...questions.map(renderQuestion));
      }
      updatePagination(payload);
      syncUrl();
      setFeedback(
        questions.length === 1
          ? "1 dúvida exibida."
          : `${questions.length} dúvidas exibidas nesta página.`,
      );
    } catch (error) {
      list.replaceChildren();
      totalCount.textContent = "—";
      pagination.hidden = true;
      setFeedback(error.message || "Falha ao carregar as dúvidas.", true);
    } finally {
      state.carregando = false;
    }
  };

  const hydrateFromUrl = () => {
    const params = new URLSearchParams(window.location.search);
    searchInput.value = params.get("termo") || "";
    const status = params.get("status") || "";
    statusFilter.value = ["PENDENTE", "RESPONDIDA"].includes(status) ? status : "";
    const requestedPage = Number.parseInt(params.get("pagina") || "0", 10);
    state.pagina = Number.isFinite(requestedPage) ? Math.max(requestedPage, 0) : 0;
  };

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    state.pagina = 0;
    void loadQuestions();
  });

  statusFilter.addEventListener("change", () => {
    state.pagina = 0;
    void loadQuestions();
  });

  clearButton.addEventListener("click", () => {
    searchInput.value = "";
    statusFilter.value = "";
    state.pagina = 0;
    void loadQuestions();
  });

  previousButton.addEventListener("click", () => {
    if (state.pagina <= 0) return;
    state.pagina -= 1;
    void loadQuestions();
    window.scrollTo({ top: 0, behavior: "smooth" });
  });

  nextButton.addEventListener("click", () => {
    if (state.pagina + 1 >= state.totalPaginas) return;
    state.pagina += 1;
    void loadQuestions();
    window.scrollTo({ top: 0, behavior: "smooth" });
  });

  hydrateFromUrl();
  void loadQuestions();
})();
