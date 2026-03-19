const root = document.documentElement;
let toggle = document.querySelector(".theme-toggle");
const fileInput = document.querySelector(".file-input");
const fileHint = document.querySelector(".file-hint");
const fileStatus = document.querySelector(".file-status");
const dropOverlay = document.querySelector(".drop-overlay");
const moneyInput = document.querySelector(".money-input");
const dateInput = document.querySelector(".date-input");
const typeSelect = document.querySelector("select[name=\"entry_type\"]");
const form = document.querySelector(".form");
const confirmOverlay = document.querySelector(".confirm-overlay");
const confirmClose = document.querySelector(".confirm-close");
const confirmTitle = document.querySelector(".confirm-card h2");
const confirmText = document.querySelector(".confirm-card p");
const confirmIcon = document.querySelector(".confirm-icon");
let csrfToken = null;
let lastSubmitOk = false;

const parseDate = (value) => {
  const digits = (value || "").replace(/\D/g, "");
  if (digits.length !== 8) return null;
  const day = Number(digits.slice(0, 2));
  const month = Number(digits.slice(2, 4));
  const year = Number(digits.slice(4, 8));
  if (!day || !month || !year) return null;
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }
  date.setHours(0, 0, 0, 0);
  return date;
};

const todayMidnight = () => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return today;
};

const toIsoLocalDate = (value) => {
  const parsed = parseDate(value);
  if (!parsed) return null;
  const day = String(parsed.getDate()).padStart(2, "0");
  const month = String(parsed.getMonth() + 1).padStart(2, "0");
  const year = String(parsed.getFullYear());
  return `${year}-${month}-${day}`;
};

const nowAsLocalDateTime = () => {
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  const dd = String(now.getDate()).padStart(2, "0");
  const hh = String(now.getHours()).padStart(2, "0");
  const mi = String(now.getMinutes()).padStart(2, "0");
  const ss = String(now.getSeconds()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}T${hh}:${mi}:${ss}`;
};

const moneyToDecimal = (value) => {
  const digits = (value || "").replace(/\D/g, "");
  const cents = Number(digits || 0);
  return (cents / 100).toFixed(2);
};

const fileToBase64 = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = String(reader.result || "");
      const comma = result.indexOf(",");
      resolve(comma >= 0 ? result.slice(comma + 1) : result);
    };
    reader.onerror = () => reject(new Error("Falha ao ler arquivo"));
    reader.readAsDataURL(file);
  });

const ensureCsrfToken = async () => {
  if (csrfToken) return csrfToken;
  const response = await fetch("/api/v1/auth/csrf", {
    method: "GET",
    credentials: "same-origin",
  });
  if (!response.ok) {
    throw new Error("Falha ao obter token CSRF");
  }
  const data = await response.json();
  csrfToken = data.token || null;
  if (!csrfToken) {
    throw new Error("Token CSRF ausente");
  }
  return csrfToken;
};

const showConfirm = (ok, message) => {
  lastSubmitOk = ok;
  if (!confirmOverlay) return;
  if (confirmTitle) confirmTitle.textContent = ok ? "Comprovante enviado" : "Falha ao enviar";
  if (confirmText) confirmText.textContent = message;
  if (confirmIcon) {
    confirmIcon.textContent = ok ? "✓" : "✕";
    confirmIcon.style.background = ok ? "#2eb05e" : "#d84b34";
    confirmIcon.style.boxShadow = ok
      ? "0 10px 20px rgba(46, 176, 94, 0.35)"
      : "0 10px 20px rgba(216, 75, 52, 0.35)";
  }
  confirmOverlay.classList.add("is-visible");
  confirmOverlay.setAttribute("aria-hidden", "false");
};

const readCookie = (name) => {
  const match = document.cookie.match(new RegExp("(^| )" + name + "=([^;]+)"));
  return match ? decodeURIComponent(match[2]) : null;
};

const writeCookie = (name, value, days = 365) => {
  const expires = new Date(Date.now() + days * 864e5).toUTCString();
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=/`;
};

const savedTheme = readCookie("theme") || localStorage.getItem("theme");
root.dataset.theme = savedTheme === "dark" ? "dark" : "light";
writeCookie("theme", root.dataset.theme);
localStorage.setItem("theme", root.dataset.theme);

const updateLabel = () => {
  if (!toggle) return;
  const isDark = root.dataset.theme === "dark";
  toggle.setAttribute("aria-pressed", isDark ? "true" : "false");
  toggle.setAttribute("aria-label", isDark ? "Ativar modo claro" : "Ativar modo escuro");
  toggle.querySelector(".theme-icon").innerHTML = isDark
    ? '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="4.6" fill="currentColor"/><g stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><line x1="12" y1="2.4" x2="12" y2="5"/><line x1="12" y1="19" x2="12" y2="21.6"/><line x1="2.4" y1="12" x2="5" y2="12"/><line x1="19" y1="12" x2="21.6" y2="12"/><line x1="5.1" y1="5.1" x2="6.9" y2="6.9"/><line x1="17.1" y1="17.1" x2="18.9" y2="18.9"/><line x1="17.1" y1="6.9" x2="18.9" y2="5.1"/><line x1="5.1" y1="18.9" x2="6.9" y2="17.1"/></g></svg>'
    : '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="4.6" fill="currentColor"/><g stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><line x1="12" y1="2.4" x2="12" y2="5"/><line x1="12" y1="19" x2="12" y2="21.6"/><line x1="2.4" y1="12" x2="5" y2="12"/><line x1="19" y1="12" x2="21.6" y2="12"/><line x1="5.1" y1="5.1" x2="6.9" y2="6.9"/><line x1="17.1" y1="17.1" x2="18.9" y2="18.9"/><line x1="17.1" y1="6.9" x2="18.9" y2="5.1"/><line x1="5.1" y1="18.9" x2="6.9" y2="17.1"/></g></svg>';
};

const bindThemeToggle = () => {
  toggle = document.querySelector(".theme-toggle");
  if (!toggle) return;
  if (toggle.dataset.navbarManaged === "true") return;
  updateLabel();
  toggle.addEventListener("click", () => {
    const isDark = root.dataset.theme === "dark";
    root.dataset.theme = isDark ? "light" : "dark";
    writeCookie("theme", root.dataset.theme);
    localStorage.setItem("theme", root.dataset.theme);
    updateLabel();
  });
};

bindThemeToggle();

if (fileInput && fileHint && fileStatus) {
  fileInput.addEventListener("change", () => {
    const file = fileInput.files && fileInput.files[0] ? fileInput.files[0] : null;
    const fileName = file ? file.name : "Nenhum arquivo selecionado";
    fileHint.textContent = fileName;

    if (!file) {
      fileStatus.classList.remove("is-ready");
      return;
    }
    fileStatus.classList.add("is-ready");
  });
}

if (fileInput) {
  const setDroppedFile = (file) => {
    if (!file) return;
    const dataTransfer = new DataTransfer();
    dataTransfer.items.add(file);
    fileInput.files = dataTransfer.files;
    fileInput.dispatchEvent(new Event("change", { bubbles: true }));
  };

  let dragDepth = 0;

  document.addEventListener("dragenter", (event) => {
    event.preventDefault();
    dragDepth += 1;
    if (dropOverlay) dropOverlay.classList.add("is-visible");
  });

  document.addEventListener("dragover", (event) => {
    event.preventDefault();
    if (dropOverlay) dropOverlay.classList.add("is-visible");
  });

  document.addEventListener("drop", (event) => {
    event.preventDefault();
    dragDepth = 0;
    if (dropOverlay) dropOverlay.classList.remove("is-visible");
    const file = event.dataTransfer && event.dataTransfer.files ? event.dataTransfer.files[0] : null;
    setDroppedFile(file);
  });

  document.addEventListener("dragleave", (event) => {
    dragDepth = Math.max(0, dragDepth - 1);
    if (dragDepth === 0 && dropOverlay) {
      dropOverlay.classList.remove("is-visible");
    }
  });

  document.addEventListener("dragend", () => {
    dragDepth = 0;
    if (dropOverlay) dropOverlay.classList.remove("is-visible");
  });
}

if (moneyInput) {
  const formatMoney = (value) => {
    const digits = value.replace(/\D/g, "");
    const number = Number(digits || 0) / 100;
    return number.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  };

  const updateMoney = () => {
    moneyInput.value = formatMoney(moneyInput.value);
    moneyInput.setCustomValidity("");
  };

  moneyInput.addEventListener("input", updateMoney);
  moneyInput.addEventListener("blur", updateMoney);
  updateMoney();
}

if (dateInput) {
  const formatDate = (value) => {
    const digits = (value || "").replace(/\D/g, "").slice(0, 8);
    const parts = [];
    if (digits.length >= 1) parts.push(digits.slice(0, 2));
    if (digits.length >= 3) parts.push(digits.slice(2, 4));
    if (digits.length >= 5) parts.push(digits.slice(4, 8));
    return parts.join("/");
  };

  if (window.flatpickr) {
    const ensureYearDropdown = (instance) => {
      const container = instance?.calendarContainer;
      if (!container) return;
      const currentMonth = container.querySelector(".flatpickr-current-month");
      const numWrapper = container.querySelector(".numInputWrapper");
      const yearInput = container.querySelector(".cur-year");
      if (!currentMonth || !yearInput) return;

      let select = container.querySelector("select.year-dropdown");
      if (!select) {
        select = document.createElement("select");
        select.className = "year-dropdown";
        currentMonth.insertBefore(select, numWrapper || yearInput);
        select.addEventListener("change", (event) => {
          const nextYear = Number(event.target.value);
          if (!Number.isNaN(nextYear)) {
            instance.changeYear(nextYear);
          }
        });
      }

      const minYear = 2000;
      const maxYear = new Date().getFullYear();
      if (select.options.length === 0) {
        for (let y = maxYear; y >= minYear; y -= 1) {
          const option = document.createElement("option");
          option.value = String(y);
          option.textContent = String(y);
          select.appendChild(option);
        }
      }
      select.value = String(instance.currentYear);

      if (numWrapper) numWrapper.style.display = "none";
      yearInput.style.display = "none";
    };

    window.flatpickr(dateInput, {
      dateFormat: "d/m/Y",
      allowInput: true,
      maxDate: "today",
      yearSelectorType: "dropdown",
      onReady: (_, __, instance) => {
        ensureYearDropdown(instance);
        dateInput.setCustomValidity("");
      },
      onYearChange: (_, __, instance) => {
        ensureYearDropdown(instance);
      },
      onOpen: (_, __, instance) => {
        ensureYearDropdown(instance);
      },
      onChange: () => {
        dateInput.setCustomValidity("");
      },
    });
  }
  dateInput.addEventListener("input", () => {
    dateInput.value = formatDate(dateInput.value);
    dateInput.setCustomValidity("");
  });
  dateInput.addEventListener("blur", () => {
    dateInput.value = formatDate(dateInput.value);
  });
}

if (form) {
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    let hasError = false;

    if (moneyInput) {
      moneyInput.setCustomValidity("");
      const normalized = moneyInput.value || "";
      if (!normalized.trim()) {
        moneyInput.setCustomValidity("Informe um valor maior que zero.");
        hasError = true;
      }
      const digits = moneyInput.value.replace(/\D/g, "");
      const number = Number(digits || 0) / 100;
      if (number <= 0) {
        moneyInput.setCustomValidity("O valor deve ser maior que zero.");
        hasError = true;
      }
    }

    if (dateInput) {
      dateInput.setCustomValidity("");
      const date = parseDate(dateInput.value);

      if (date && date > todayMidnight()) {
        dateInput.setCustomValidity("A data não pode ser maior que a data atual.");
        hasError = true;
      }
    }

    if (fileInput) {
      fileInput.setCustomValidity("");
      if (!fileInput.files || fileInput.files.length === 0) {
        fileInput.setCustomValidity("Selecione um Arquivo");
        hasError = true;
      } else {
        const selectedFile = fileInput.files[0];
        const isPdf =
          selectedFile.type === "application/pdf" || selectedFile.name.toLowerCase().endsWith(".pdf");
        if (!isPdf) {
          fileInput.setCustomValidity("Envie um arquivo PDF.");
          hasError = true;
        }
      }
    }

    if (typeSelect) {
      typeSelect.setCustomValidity("");
      if (!typeSelect.value) {
        typeSelect.setCustomValidity("Selecione o tipo de comprovante.");
        hasError = true;
      }
    }

    if (hasError) {
      if (moneyInput) moneyInput.reportValidity();
      if (dateInput) dateInput.reportValidity();
      if (fileInput) fileInput.reportValidity();
      if (typeSelect) typeSelect.reportValidity();
      return;
    }

    const file = fileInput.files[0];
    const dataIso = toIsoLocalDate(dateInput.value);
    const tipoSelecionado = String(typeSelect?.value || "").trim().toUpperCase();
    if (tipoSelecionado !== "RECEITA" && tipoSelecionado !== "DESPESA") {
      if (typeSelect) {
        typeSelect.setCustomValidity("Selecione um tipo valido.");
        typeSelect.reportValidity();
      }
      return;
    }
    const tipo = tipoSelecionado;
    const accessToken = localStorage.getItem("sc_access_token");

    if (!accessToken) {
      window.location.href = "/login";
      return;
    }

    try {
      const arquivoPdf = await fileToBase64(file);
      const token = await ensureCsrfToken();
      const response = await fetch("/api/v1/itens", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-TOKEN": token,
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          valor: moneyToDecimal(moneyInput.value),
          data: dataIso,
          horarioCriacao: nowAsLocalDateTime(),
          arquivoPdf,
          tipo,
        }),
      });

      if (!response.ok) {
        const errorBody = await response.json().catch(() => ({}));
        const message =
          errorBody.message ||
          errorBody.error ||
          `Erro ${response.status} ao enviar comprovante.`;
        showConfirm(false, message);
        return;
      }

      showConfirm(true, "Seu comprovante foi salvo com sucesso.");
    } catch (error) {
      showConfirm(false, "Erro ao enviar comprovante. Tente novamente.");
    }
  });
}

if (confirmOverlay && confirmClose) {
  confirmClose.addEventListener("click", () => {
    confirmOverlay.classList.remove("is-visible");
    confirmOverlay.setAttribute("aria-hidden", "true");
    if (form && lastSubmitOk) {
      form.reset();
    }
    if (fileStatus && lastSubmitOk) {
      fileStatus.classList.remove("is-ready");
    }
    if (fileHint && lastSubmitOk) {
      fileHint.textContent = "Nenhum arquivo selecionado";
    }
    if (moneyInput && lastSubmitOk) {
      moneyInput.setCustomValidity("");
      moneyInput.value = "R$ 0,00";
    }
    if (dateInput && lastSubmitOk) {
      dateInput.setCustomValidity("");
      dateInput.value = "";
    }
    if (typeSelect && lastSubmitOk) {
      typeSelect.setCustomValidity("");
      typeSelect.value = "";
    }
  });
}
