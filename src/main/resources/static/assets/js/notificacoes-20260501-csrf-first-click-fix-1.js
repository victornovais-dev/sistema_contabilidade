const state = {
  notifications: [],
  selectedRole: "",
  csrfToken: null,
  userRoles: [],
};

const REFRESH_ANIMATION_MS = 180;

const notificationsState = document.getElementById("notifications-state");
const notificationsList = document.getElementById("notifications-list");
const notificationCardTemplate = document.getElementById("notification-card-template");
const summaryGrid = document.getElementById("summary-grid");
const summaryCount = document.querySelector('[data-summary="count"]');
const summaryTotal = document.querySelector('[data-summary="total"]');
const summaryLaunched = document.querySelector('[data-summary="launched"]');
const uploadOverlay = document.querySelector(".upload-overlay");
const uploadClose = document.querySelector(".upload-close");
const uploadFiles = document.getElementById("upload-files");
const uploadInput = document.querySelector(".upload-input");
const uploadSave = document.querySelector(".upload-save");
const uploadEdit = document.querySelector(".upload-edit");
const uploadDrop = document.querySelector("[data-upload-drop]");
const uploadSelected = document.getElementById("upload-selected");
const technicalRoles = new Set(["ADMIN", "CONTABIL", "MANAGER", "SUPPORT", "CANDIDATO"]);
const roleFilterStorageKey = "sc_home_selected_role";
const MAX_RECEIPT_SIZE_BYTES = 20 * 1024 * 1024;
const PDF_ONLY_MESSAGE = "Envie somente arquivos PDF.";
const MAX_RECEIPT_SIZE_MESSAGE = "Cada comprovante deve ter no maximo 20 MB.";
const smoothHideTimers = new WeakMap();
const SMOOTH_HIDE_MS = 100;
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
            await loadNotifications({ preserveVisibleContent: state.notifications.length > 0 });
          } catch (error) {
            setRefreshing(false);
            showState(
              error instanceof Error
                ? error.message
                : "Erro ao carregar notificacoes do candidato selecionado.",
              true,
            );
          }
        },
      })
    : null;

const getAccessToken = () => localStorage.getItem("sc_access_token");
const wait = (ms) => new Promise((resolve) => window.setTimeout(resolve, ms));
const SUPPORT_UNCHECK_BLOCKED_MESSAGE =
  "Usuarios SUPPORT nao podem desmarcar comprovantes verificados.";
let pendingUploadItemId = null;
let uploadIsEditing = false;
let pendingDeleteArquivoIds = new Set();
let retainedUploadFiles = [];
let settingUploadFilesProgrammatically = false;
let uploadErrorEntries = [];

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
    const roles = await response.json();
    return Array.isArray(roles)
      ? roles.map((role) => String(role || "").trim().toUpperCase()).filter(Boolean)
      : [];
  } catch (error) {
    return [];
  }
};

const isSupportUser = () => state.userRoles.includes("SUPPORT");

const extractErrorMessage = async (response, fallbackMessage) => {
  try {
    const payload = await response.json();
    if (payload && typeof payload === "object") {
      return payload.message || payload.error || fallbackMessage;
    }
  } catch (error) {
    // Keep fallback when backend returns no JSON body.
  }
  return fallbackMessage;
};

const buildRoleQuery = () => {
  if (!state.selectedRole) return "";
  const params = new URLSearchParams({ role: state.selectedRole });
  return `?${params.toString()}`;
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

const formatCurrency = (value) =>
  Number(value || 0).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

const formatText = (value) => {
  const text = String(value || "").trim();
  return text || "-";
};

const formatDateTime = (value) => {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "-";
  return date.toLocaleString("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
};

const isNotificationChecked = (notification) =>
  notification && Object.prototype.hasOwnProperty.call(notification, "verificado")
    ? Boolean(notification.verificado)
    : Boolean(notification?.limpa);

const getPendingNotificationCount = () =>
  (Array.isArray(state.notifications) ? state.notifications : []).filter(
    (notification) => !isNotificationChecked(notification),
  ).length;

const notifyNavbarCountChanged = () => {
  window.dispatchEvent(
    new CustomEvent("notifications:changed", {
      detail: { pendingCount: getPendingNotificationCount() },
    }),
  );
};

const showState = (message, isError = false) => {
  if (!notificationsState) return;
  notificationsState.hidden = false;
  notificationsState.textContent = message;
  notificationsState.classList.toggle("is-error", isError);
  if (summaryGrid) summaryGrid.hidden = true;
  if (notificationsList) notificationsList.hidden = true;
};

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

const setUploadSaveVisible = (visible) => {
  if (!uploadSave) return;
  setButtonVisibleSmooth(uploadSave, visible);
};

const isPdfFile = (file) => {
  if (!file) return false;
  const name = String(file.name || "").toLowerCase();
  return file.type === "application/pdf" || name.endsWith(".pdf");
};

const updateUploadSaveVisibility = () => {
  const hasNewFiles = uploadInput?.files && Array.from(uploadInput.files).some(isPdfFile);
  const hasDeletes = pendingDeleteArquivoIds.size > 0;
  setUploadSaveVisible(Boolean(uploadIsEditing || hasNewFiles || hasDeletes));
};

const hideState = () => {
  if (!notificationsState) return;
  notificationsState.hidden = true;
  notificationsState.textContent = "";
  notificationsState.classList.remove("is-error");
  if (summaryGrid) summaryGrid.hidden = false;
  if (notificationsList) notificationsList.hidden = false;
};

const setRefreshing = (refreshing) => {
  [summaryGrid, notificationsList].forEach((element) => {
    if (!element) return;
    element.classList.toggle("is-refreshing", refreshing);
  });
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
      throw new Error("Token CSRF invalido.");
    }
    return state.csrfToken;
  }
  const response = await fetch("/api/v1/auth/csrf", {
    method: "GET",
    credentials: "same-origin",
    headers: {
      Accept: "application/json",
    },
  });
  if (!response.ok) {
    throw new Error("Nao foi possivel carregar o token CSRF.");
  }
  const data = await response.json();
  state.csrfToken = data.token || null;
  if (!state.csrfToken) {
    throw new Error("Token CSRF invalido.");
  }
  return state.csrfToken;
};

const syncNotificationCheckedState = (notificationId, verificado) => {
  const index = state.notifications.findIndex(
    (notification) => String(notification?.id) === String(notificationId),
  );
  if (index < 0) return;
  state.notifications[index] = {
    ...state.notifications[index],
    limpa: verificado === true,
    verificado: verificado === true,
  };
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
  uploadFiles.classList.toggle("is-editing", uploadIsEditing);
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
    remove.textContent = "\u00d7";
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
    throw new Error("Sessao expirada. Faca login novamente.");
  }
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, "Falha ao excluir arquivo."));
  }
};

const uploadArquivos = async (files) => {
  if (!pendingUploadItemId || !uploadInput) return [];
  const effectiveFiles = files ? Array.from(files) : uploadInput.files ? Array.from(uploadInput.files) : [];
  const pdfs = validateUploadFilesOrThrow(effectiveFiles);
  if (pdfs.length === 0) {
    return [];
  }

  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return [];
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
    const message = await extractErrorMessage(response, "Falha ao enviar arquivos do comprovante.");
    throw Object.assign(new Error(message), {
      uploadEntries: pdfs.map((file) => createUploadErrorEntry(file?.name, message)),
    });
  }
  if (uploadSelected) {
    uploadSelected
      .querySelectorAll(".upload-file-card")
      .forEach((card) => card.classList.remove("is-loading"));
  }
  return response.json().catch(() => []);
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
    showState(error instanceof Error ? error.message : "Falha ao salvar alteracoes de arquivos.", true);
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
    remove.textContent = "\u00d7";
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
    dismiss.textContent = "\u00d7";
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
      showState(PDF_ONLY_MESSAGE, true);
    }
    const oversizedFile = findOversizedUploadFile(picked);
    if (oversizedFile) {
      showState(MAX_RECEIPT_SIZE_MESSAGE, true);
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

const openUploadModal = async (itemId) => {
  pendingUploadItemId = itemId;
  if (!uploadOverlay) return;
  hideState();
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
  await loadItemArquivos(itemId);
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

const renderSummary = () => {
  if (!summaryGrid) return;
  const notifications = Array.isArray(state.notifications) ? state.notifications : [];
  const totalValue = notifications.reduce((total, notification) => {
    return total + Number(notification?.valor || 0);
  }, 0);
  const launchedValue = notifications.reduce((total, notification) => {
    if (!isNotificationChecked(notification)) return total;
    return total + Number(notification?.valor || 0);
  }, 0);

  if (summaryCount) summaryCount.textContent = String(notifications.length);
  if (summaryTotal) summaryTotal.textContent = formatCurrency(totalValue);
  if (summaryLaunched) summaryLaunched.textContent = formatCurrency(launchedValue);
};

const renderNotifications = () => {
  if (!notificationsList || !notificationCardTemplate) return;
  notificationsList.innerHTML = "";
  const notifications = Array.isArray(state.notifications) ? state.notifications : [];

  if (!notifications.length) {
    const emptyCard = document.createElement("article");
    emptyCard.className = "card notification-card notification-card-empty";
    emptyCard.innerHTML =
      '<p class="notification-empty-copy">Nenhuma receita lancada para os filtros selecionados.</p>';
    notificationsList.appendChild(emptyCard);
    return;
  }

  notifications.forEach((notification) => {
    const node = notificationCardTemplate.content.cloneNode(true);
    const card = node.querySelector(".notification-card");
    if (!card) return;
    card.dataset.id = String(notification.id || "");
    card.dataset.itemId = String(notification.itemId || "");
    const checked = isNotificationChecked(notification);
    const supportLocked = isSupportUser() && checked;
    card.classList.toggle("is-cleaned", checked);
    card.querySelector('[data-field="role"]').textContent = formatText(notification.role);
    card.querySelector('[data-field="criadoEm"]').textContent = formatDateTime(notification.criadoEm);
    card.querySelector('[data-field="valor"]').textContent = formatCurrency(notification.valor);
    card.querySelector('[data-field="descricao"]').textContent = formatText(notification.descricao);
    card.querySelector('[data-field="razaoSocialNome"]').textContent = formatText(
      notification.razaoSocialNome,
    );
    const checkButton = card.querySelector(".notification-check-toggle");
    const uploadButton = card.querySelector(".notification-upload");
    if (uploadButton instanceof HTMLButtonElement) {
      const hasItemId = String(notification.itemId || "").trim().length > 0;
      uploadButton.disabled = !hasItemId;
      uploadButton.hidden = !hasItemId;
    }
    if (checkButton instanceof HTMLButtonElement) {
      checkButton.classList.toggle("is-checked", checked);
      checkButton.classList.toggle("is-locked", supportLocked);
      checkButton.disabled = supportLocked;
      checkButton.setAttribute("aria-pressed", String(checked));
      checkButton.setAttribute(
        "aria-label",
        supportLocked
          ? SUPPORT_UNCHECK_BLOCKED_MESSAGE
          : checked
            ? "Desmarcar comprovante"
            : "Marcar comprovante como verificado",
      );
      if (supportLocked) {
        checkButton.title = SUPPORT_UNCHECK_BLOCKED_MESSAGE;
      } else {
        checkButton.removeAttribute("title");
      }
    }
    notificationsList.appendChild(node);
  });
};

const renderPage = () => {
  renderSummary();
  renderNotifications();
  hideState();
  notifyNavbarCountChanged();
};

const patchVerificacao = async (itemId, verificado) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return null;
  }

  const executePatch = async (csrfToken) =>
    fetch(`/api/v1/itens/${itemId}/verificacao`, {
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
  if (isRedirect || response.status === 401) {
    window.location.href = "/login";
    throw new Error("Sessao expirada. Faca login novamente.");
  }

  if (!response.ok) {
    const message = await extractErrorMessage(response, "Falha ao atualizar verificacao.");
    throw new Error(message);
  }

  return response.json();
};

const loadNotifications = async ({ preserveVisibleContent = false } = {}) => {
  const accessToken = getAccessToken();
  if (!accessToken) {
    window.location.href = "/login";
    return;
  }

  if (preserveVisibleContent) {
    setRefreshing(true);
    await wait(REFRESH_ANIMATION_MS);
  } else {
    showState("Carregando notificacoes...");
  }

  const response = await fetch(`/api/v1/notificacoes${buildRoleQuery()}`, {
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
    throw new Error("Acesso negado as notificacoes do candidato selecionado.");
  }
  if (!response.ok) {
    throw new Error("Nao foi possivel carregar as notificacoes.");
  }

  const payload = await response.json();
  state.notifications = Array.isArray(payload) ? payload : [];
  renderPage();
  requestAnimationFrame(() => {
    setRefreshing(false);
  });
};

const loadRoleFilterOptions = async () => {
  if (!roleFilterBox || !roleFilterSelect) return;
  const accessToken = getAccessToken();
  if (!accessToken) return;

  const response = await fetch("/api/v1/notificacoes/roles", {
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
  if (!notificationsList) return;
  notificationsList.addEventListener("click", async (event) => {
    const target = event.target;
    if (!(target instanceof Element)) return;
    const uploadButton = target.closest(".notification-upload");
    if (uploadButton) {
      const card = uploadButton.closest(".notification-card");
      const itemId = card?.dataset.itemId;
      if (itemId) {
        openUploadModal(itemId);
      }
      return;
    }
    const checkButton = target.closest(".notification-check-toggle");
    if (!(checkButton instanceof HTMLButtonElement)) return;

    const card = checkButton.closest(".notification-card");
    const notificationId = card?.dataset.id;
    const itemId = card?.dataset.itemId;
    if (!notificationId || !itemId) return;
    if (isSupportUser() && checkButton.classList.contains("is-checked")) {
      showState(SUPPORT_UNCHECK_BLOCKED_MESSAGE, true);
      return;
    }

    const nextChecked = !checkButton.classList.contains("is-checked");
    checkButton.disabled = true;
    try {
      const updated = await patchVerificacao(itemId, nextChecked);
      const persistedChecked = Boolean(updated?.verificado);
      syncNotificationCheckedState(notificationId, persistedChecked);
      renderPage();
    } catch (error) {
      showState(
        error instanceof Error ? error.message : "Erro ao atualizar verificacao.",
        true,
      );
    } finally {
      checkButton.disabled = false;
    }
  });

  if (uploadClose) {
    uploadClose.addEventListener("click", closeUploadModal);
  }

  if (uploadSave) {
    uploadSave.addEventListener("click", saveUploadChanges);
  }
};

const init = async () => {
  bindEvents();
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
        showState(PDF_ONLY_MESSAGE, true);
      }
      const oversizedFile = findOversizedUploadFile(picked);
      if (oversizedFile) {
        showState(MAX_RECEIPT_SIZE_MESSAGE, true);
      }
      const validFiles = sanitizeUploadFiles(picked);
      retainedUploadFiles = validFiles;
      if (picked.length !== validFiles.length) {
        replaceUploadInputFiles(validFiles);
        return;
      }
      renderSelectedFiles(uploadInput.files);
    });
  }
  try {
    state.userRoles = await loadCurrentUserRoles();
  } catch (error) {
    state.userRoles = [];
  }
  try {
    await loadRoleFilterOptions();
  } catch (error) {
    removeRoleFilterBox();
  }

  try {
    await loadNotifications();
  } catch (error) {
    showState(
      error instanceof Error ? error.message : "Erro ao carregar notificacoes. Tente novamente.",
      true,
    );
  }
};

init();
