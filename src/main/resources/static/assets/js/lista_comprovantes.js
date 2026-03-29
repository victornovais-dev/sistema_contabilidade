const state = {
  items: [],
  filteredItems: [],
  pendingDeleteId: null,
  csrfToken: null,
};

const filterInput = document.querySelector(".filter-date-range");
const filterType = document.querySelector(".filter-type-select");
const filterClear = document.querySelector(".filter-clear");
const listState = document.getElementById("list-state");
const itemsList = document.getElementById("items-list");
const itemCardTemplate = document.getElementById("item-card-template");
const confirmOverlay = document.querySelector(".confirm-overlay");
const confirmCancel = document.querySelector(".confirm-cancel");
const confirmDelete = document.querySelector(".confirm-delete");
const uploadOverlay = document.querySelector(".upload-overlay");
const uploadClose = document.querySelector(".upload-close");
const uploadFiles = document.getElementById("upload-files");
const uploadInput = document.querySelector(".upload-input");
const uploadSave = document.querySelector(".upload-save");
const uploadDrop = document.querySelector("[data-upload-drop]");
const uploadSelected = document.getElementById("upload-selected");
const observacaoOverlay = document.querySelector(".observacao-overlay");
const observacaoClose = document.querySelector(".observacao-close");
const observacaoContent = document.getElementById("observacao-content");
let pendingUploadItemId = null;
let pendingObservacaoItemId = null;

const getAccessToken = () => localStorage.getItem("sc_access_token");

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
  article.dataset.observacao = item.observacao ? String(item.observacao) : "";

  node.querySelector('[data-field="valor"]').textContent = formatCurrency(item.valor);
  node.querySelector('[data-field="tipo"]').textContent =
    item.tipo === "RECEITA" ? "Receita" : "Despesa";
  node.querySelector('[data-field="data"]').textContent = formatDate(item.data);
  node.querySelector('[data-field="horario"]').textContent = formatTime(item.horarioCriacao);
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
  uploadOverlay.classList.add("is-visible");
  uploadOverlay.setAttribute("aria-hidden", "false");
  await loadItemArquivos(id);
};

const closeUploadModal = () => {
  pendingUploadItemId = null;
  if (!uploadOverlay) return;
  uploadOverlay.classList.remove("is-visible");
  uploadOverlay.setAttribute("aria-hidden", "true");
  if (uploadInput) {
    uploadInput.value = "";
  }
  if (uploadSelected) {
    uploadSelected.innerHTML = "";
  }
  if (uploadDrop) {
    uploadDrop.classList.remove("is-active");
  }
};

const openObservacaoModal = (id) => {
  pendingObservacaoItemId = id;
  if (!observacaoOverlay || !observacaoContent) return;
  const item = state.items.find((entry) => entry.id === id);
  const texto = item?.observacao ? String(item.observacao) : "Nenhuma observação registrada.";
  observacaoContent.textContent = texto;
  observacaoOverlay.classList.add("is-visible");
  observacaoOverlay.setAttribute("aria-hidden", "false");
};

const closeObservacaoModal = () => {
  pendingObservacaoItemId = null;
  if (!observacaoOverlay) return;
  observacaoOverlay.classList.remove("is-visible");
  observacaoOverlay.setAttribute("aria-hidden", "true");
  if (observacaoContent) {
    observacaoContent.textContent = "";
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
    return;
  }
  uploadFiles.innerHTML = "";
  arquivos.forEach((arquivo) => {
    const li = document.createElement("li");
    const name = arquivo.caminhoArquivoPdf
      ? arquivo.caminhoArquivoPdf.split(/[\\/]/).pop()
      : "PDF";
    const link = document.createElement("a");
    link.href = `/api/v1/itens/${id}/arquivos/${arquivo.id}`;
    link.textContent = name;
    link.target = "_blank";
    link.rel = "noopener noreferrer";
    li.appendChild(link);
    uploadFiles.appendChild(li);
  });
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

const uploadArquivos = async () => {
  if (!pendingUploadItemId || !uploadInput) return;
  const files = uploadInput.files ? Array.from(uploadInput.files) : [];
  if (files.length === 0) {
    return;
  }
  const invalid = files.find(
    (file) => file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf"),
  );
  if (invalid) {
    return;
  }

  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }
  const token = await ensureCsrfToken(true);
  const arquivosPdf = await filesToBase64(files);
  const nomesArquivos = filesToNames(files);
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
    return;
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
  if (uploadInput) uploadInput.value = "";
  await loadItemArquivos(pendingUploadItemId);
};

const renderSelectedFiles = (files) => {
  if (!uploadSelected) return;
  uploadSelected.innerHTML = "";
  if (!files || files.length === 0) return;
  Array.from(files).forEach((file) => {
    const card = document.createElement("div");
    card.className = "upload-file-card is-loading";
    const icon = document.createElement("div");
    icon.className = "upload-file-icon";
    icon.textContent = "PDF";
    const name = document.createElement("div");
    name.className = "upload-file-name";
    name.textContent = file.name || "Arquivo.pdf";
    card.appendChild(icon);
    card.appendChild(name);
    uploadSelected.appendChild(card);
  });
};

const bindUploadDrop = () => {
  if (!uploadDrop || !uploadInput) return;
  const setFiles = (files) => {
    if (!files || files.length === 0) return;
    const dataTransfer = new DataTransfer();
    Array.from(files).forEach((file) => dataTransfer.items.add(file));
    uploadInput.files = dataTransfer.files;
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
    renderSelectedFiles(event.dataTransfer?.files);
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

  if (uploadSave) {
    uploadSave.addEventListener("click", uploadArquivos);
  }
};

const init = async () => {
  bindEvents();
  bindUploadDrop();
  if (uploadInput) {
    uploadInput.addEventListener("change", () => renderSelectedFiles(uploadInput.files));
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


