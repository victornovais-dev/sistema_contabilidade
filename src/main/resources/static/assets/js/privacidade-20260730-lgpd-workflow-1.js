(() => {
  "use strict";

  const TYPE_LABELS = Object.freeze({
    ACESSO: "Acesso aos dados",
    EXPORTACAO: "Cópia ou exportação",
    CORRECAO: "Correção de dados",
    DESATIVACAO: "Desativação de conta",
    ANONIMIZACAO: "Anonimização",
    EXCLUSAO: "Exclusão de dados",
    OPOSICAO: "Oposição ao tratamento",
    INFORMACOES_COMPARTILHAMENTO: "Informações de compartilhamento",
  });

  const STATUS_LABELS = Object.freeze({
    RECEBIDA: "Recebida",
    IDENTIDADE_PENDENTE: "Aguardando confirmação de identidade",
    EM_ANALISE: "Em análise",
    RETENCAO_LEGAL: "Em análise de retenção",
    PRONTA_EXECUCAO: "Aprovada para execução",
    CONCLUIDA: "Concluída",
    PARCIALMENTE_ATENDIDA: "Parcialmente atendida",
    NEGADA: "Não atendida",
    CANCELADA: "Cancelada",
  });

  const STATUS_MESSAGES = Object.freeze({
    RECEBIDA: "O pedido foi registrado e aguarda triagem.",
    IDENTIDADE_PENDENTE:
      "Precisamos confirmar sua identidade por canal seguro antes de continuar.",
    EM_ANALISE: "O pedido está sendo analisado pela equipe responsável.",
    RETENCAO_LEGAL:
      "Parte dos dados pode estar sujeita a uma obrigação de conservação. A análise continua.",
    PRONTA_EXECUCAO: "A solicitação foi aprovada e está em execução.",
    CONCLUIDA: "O atendimento foi concluído. Verifique o canal de resposta escolhido.",
    PARCIALMENTE_ATENDIDA:
      "Parte do pedido foi atendida. A justificativa será enviada pelo canal informado.",
    NEGADA: "O pedido não pôde ser atendido. A justificativa será enviada pelo canal informado.",
    CANCELADA: "Esta solicitação foi cancelada.",
  });

  const requestForm = document.querySelector("#privacy-request-form");
  const lookupForm = document.querySelector("#privacy-lookup-form");
  const requestFeedback = document.querySelector("#request-feedback");
  const lookupResult = document.querySelector("#lookup-result");
  const descriptionCount = document.querySelector("#description-count");
  const requestSubmit = document.querySelector("#request-submit");
  const lookupSubmit = document.querySelector("#lookup-submit");
  const successDialog = document.querySelector("#success-dialog");
  const successProtocol = document.querySelector("#success-protocol");
  const copyProtocol = document.querySelector("#copy-protocol");
  const closeSuccess = document.querySelector("#close-success");
  const toastContainer = document.querySelector("#toast-container");

  const fetchCsrfToken = async () => {
    const response = await fetch("/api/v1/auth/csrf", {
      credentials: "same-origin",
      headers: { Accept: "application/json" },
    });
    if (!response.ok) throw new Error("Não foi possível validar o envio.");
    const payload = await response.json();
    if (!payload?.token) throw new Error("Token de segurança ausente.");
    return payload.token;
  };

  const postJson = async (path, payload) => {
    const csrfToken = await fetchCsrfToken();
    return fetch(path, {
      method: "POST",
      credentials: "same-origin",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": csrfToken,
      },
      body: JSON.stringify(payload),
    });
  };

  const setBusy = (button, busy, busyLabel, defaultLabel) => {
    button.disabled = busy;
    button.textContent = busy ? busyLabel : defaultLabel;
  };

  const setFeedback = (message = "", success = false) => {
    requestFeedback.textContent = message;
    requestFeedback.classList.toggle("success", success);
  };

  const validateForm = (form) => {
    form.querySelectorAll(".invalid").forEach((field) => field.classList.remove("invalid"));
    if (form.checkValidity()) return true;
    const invalid = [...form.querySelectorAll(":invalid")];
    invalid.forEach((field) => field.classList.add("invalid"));
    invalid[0]?.focus();
    return false;
  };

  const collectRequest = () => {
    const data = new FormData(requestForm);
    return {
      nome: data.get("nome"),
      email: data.get("email"),
      organizacao: data.get("organizacao"),
      vinculo: data.get("vinculo"),
      tipo: data.get("tipo"),
      escopos: data.getAll("escopos"),
      canalResposta: data.get("canalResposta"),
      referencia: data.get("referencia") || null,
      descricao: data.get("descricao"),
      avisoAceito: data.get("avisoAceito") === "on",
      website: data.get("website") || "",
    };
  };

  const submitRequest = async (event) => {
    event.preventDefault();
    setFeedback();
    if (!validateForm(requestForm)) {
      setFeedback("Revise os campos obrigatórios destacados.");
      return;
    }

    const payload = collectRequest();
    if (payload.escopos.length === 0) {
      setFeedback("Selecione ao menos uma categoria de dados ou “Não sei informar”.");
      requestForm.querySelector(".scope-fieldset")?.scrollIntoView({
        behavior: "smooth",
        block: "center",
      });
      return;
    }

    setBusy(requestSubmit, true, "Enviando…", "Enviar solicitação");
    try {
      const response = await postJson("/api/v1/solicitacoes-privacidade", payload);
      if (!response.ok) {
        throw new Error(
          response.status === 429
            ? "Muitas tentativas. Aguarde alguns minutos e tente novamente."
            : "Não foi possível registrar a solicitação. Revise os dados e tente novamente.",
        );
      }
      const result = await response.json();
      successProtocol.textContent = result.protocolo;
      successDialog.hidden = false;
      document.body.classList.add("no-scroll");
      copyProtocol.focus();
      requestForm.reset();
      updateDescriptionCount();
      setFeedback("Solicitação registrada com sucesso.", true);
    } catch (error) {
      setFeedback(error.message || "Falha ao enviar solicitação.");
    } finally {
      setBusy(requestSubmit, false, "Enviando…", "Enviar solicitação");
    }
  };

  const createElement = (tag, className = "", text = "") => {
    const element = document.createElement(tag);
    if (className) element.className = className;
    if (text) element.textContent = text;
    return element;
  };

  const appendLookupRow = (container, label, value) => {
    const row = createElement("div", "lookup-row");
    row.append(createElement("span", "", label), createElement("strong", "", value));
    container.append(row);
  };

  const renderLookup = (request) => {
    const header = createElement("div", "lookup-header");
    header.append(
      createElement("strong", "", request.protocolo),
      createElement(
        "span",
        "status-badge",
        STATUS_LABELS[request.status] || "Em processamento",
      ),
    );
    const details = createElement("div", "lookup-details");
    appendLookupRow(details, "Tipo", TYPE_LABELS[request.tipo] || "Não informado");
    appendLookupRow(details, "Recebida em", formatDate(request.recebidaEm));
    appendLookupRow(details, "Última atualização", formatDateTime(request.atualizadaEm));
    const message = createElement(
      "p",
      "lookup-message",
      STATUS_MESSAGES[request.status] || "A solicitação está em processamento.",
    );
    lookupResult.replaceChildren(header, details, message);
    lookupResult.hidden = false;
  };

  const renderLookupError = (message) => {
    lookupResult.replaceChildren(createElement("p", "feedback", message));
    lookupResult.hidden = false;
  };

  const submitLookup = async (event) => {
    event.preventDefault();
    lookupResult.hidden = true;
    if (!validateForm(lookupForm)) {
      lookupForm.reportValidity();
      return;
    }
    const data = new FormData(lookupForm);
    setBusy(lookupSubmit, true, "Consultando…", "Consultar andamento");
    try {
      const response = await postJson("/api/v1/solicitacoes-privacidade/consulta", {
        protocolo: String(data.get("protocolo") || "").trim().toUpperCase(),
        email: data.get("email"),
      });
      if (!response.ok) {
        throw new Error(
          response.status === 429
            ? "Muitas consultas. Aguarde alguns minutos."
            : "Não foi possível localizar uma solicitação com os dados informados.",
        );
      }
      renderLookup(await response.json());
    } catch (error) {
      renderLookupError(error.message || "Falha ao consultar protocolo.");
    } finally {
      setBusy(lookupSubmit, false, "Consultando…", "Consultar andamento");
    }
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
      : new Intl.DateTimeFormat("pt-BR", {
          dateStyle: "short",
          timeStyle: "short",
        }).format(date);
  };

  const updateDescriptionCount = () => {
    descriptionCount.textContent = String(requestForm.elements.descricao.value.length);
  };

  const closeDialog = () => {
    successDialog.hidden = true;
    document.body.classList.remove("no-scroll");
  };

  const showToast = (message) => {
    const toast = createElement("div", "toast", message);
    toastContainer.append(toast);
    window.setTimeout(() => toast.remove(), 3200);
  };

  requestForm.addEventListener("submit", (event) => void submitRequest(event));
  requestForm.addEventListener("reset", () => window.setTimeout(updateDescriptionCount, 0));
  requestForm.elements.descricao.addEventListener("input", updateDescriptionCount);
  lookupForm.addEventListener("submit", (event) => void submitLookup(event));
  closeSuccess.addEventListener("click", closeDialog);
  copyProtocol.addEventListener("click", async () => {
    try {
      await navigator.clipboard.writeText(successProtocol.textContent);
      showToast("Protocolo copiado.");
    } catch {
      showToast(`Copie o protocolo: ${successProtocol.textContent}`);
    }
  });
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !successDialog.hidden) closeDialog();
  });
})();
