const root = document.documentElement;
let toggle = document.querySelector(".theme-toggle");
const fileInput = document.querySelector(".file-input");
const fileHint = document.querySelector(".file-hint");
const fileStatus = document.querySelector(".file-status");
const dropOverlay = document.querySelector(".drop-overlay");
const moneyInput = document.querySelector(".money-input");
const dateInput = document.querySelector(".date-input");
const typeSelect = document.querySelector("select[name=\"entry_type\"]");
const descricaoSelect = document.querySelector("select[name=\"descricao\"]");
const customSelects = document.querySelectorAll("[data-custom-select]");
const razaoSocialNomeInput = document.querySelector("input[name=\"razao_social_nome\"]");
const cnpjCpfInput = document.querySelector("input[name=\"cnpj_cpf\"]");
const observacaoInput = document.querySelector("textarea[name=\"observacao\"]");
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

const sanitizeRazaoSocial = (value) => {
  if (!value) return "";
  const cleaned = value.replace(/[^\p{L}\p{N} .&'/-]/gu, "");
  return cleaned.replace(/\s{2,}/g, " ").toUpperCase();
};

const formatCpfCnpj = (value) => {
  const digits = (value || "").replace(/\D/g, "").slice(0, 14);
  if (digits.length <= 11) {
    const cpf = digits.slice(0, 11);
    let out = cpf.slice(0, 3);
    if (cpf.length > 3) out += "." + cpf.slice(3, 6);
    if (cpf.length > 6) out += "." + cpf.slice(6, 9);
    if (cpf.length > 9) out += "-" + cpf.slice(9, 11);
    return out;
  }
  const cnpj = digits.slice(0, 14);
  let out = cnpj.slice(0, 2);
  if (cnpj.length > 2) out += "." + cnpj.slice(2, 5);
  if (cnpj.length > 5) out += "." + cnpj.slice(5, 8);
  if (cnpj.length > 8) out += "/" + cnpj.slice(8, 12);
  if (cnpj.length > 12) out += "-" + cnpj.slice(12, 14);
  return out;
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

const filesToBase64 = async (files) => {
  if (!files || files.length === 0) return [];
  const array = Array.from(files);
  const encoded = [];
  for (const file of array) {
    encoded.push(await fileToBase64(file));
  }
  return encoded;
};

const filesToNames = (files) =>
  files && files.length > 0 ? Array.from(files).map((file) => file.name) : [];

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

const bindCustomSelect = (selectElement) => {
  if (!(selectElement instanceof HTMLSelectElement)) return null;
  const wrapper = selectElement.closest("[data-custom-select]");
  if (!wrapper) return null;
  const trigger = wrapper.querySelector(".custom-select-trigger");
  const menu = wrapper.querySelector(".custom-select-menu");
  const options = wrapper.querySelectorAll(".custom-select-option");
  if (!(trigger instanceof HTMLButtonElement) || !(menu instanceof HTMLElement)) return null;

  const close = () => {
    menu.hidden = true;
    trigger.setAttribute("aria-expanded", "false");
  };

  const open = () => {
    menu.hidden = false;
    trigger.setAttribute("aria-expanded", "true");
  };

  const syncFromSelect = () => {
    const selected = selectElement.selectedOptions && selectElement.selectedOptions.length > 0
      ? selectElement.selectedOptions[0]
      : null;
    const label = selected ? String(selected.textContent || "").trim() : "";
    trigger.textContent = label || "Selecione";

    options.forEach((node) => {
      if (!(node instanceof HTMLElement)) return;
      node.classList.toggle("is-active", (node.dataset.value || "") === selectElement.value);
    });
  };

  trigger.addEventListener("click", (event) => {
    event.preventDefault();
    trigger.classList.remove("is-invalid");
    if (menu.hidden) open();
    else close();
  });

  options.forEach((option) => {
    option.addEventListener("click", () => {
      const value = option.dataset.value || "";
      selectElement.value = value;
      selectElement.dispatchEvent(new Event("change", { bubbles: true }));
      selectElement.setCustomValidity("");
      trigger.classList.remove("is-invalid");
      syncFromSelect();
      close();
    });
  });

  document.addEventListener(
    "mousedown",
    (event) => {
      const target = event.target;
      if (!(target instanceof Node)) return;
      if (trigger.contains(target)) return;
      if (menu.contains(target)) return;
      close();
    },
    { capture: true },
  );

  selectElement.addEventListener("change", syncFromSelect);
  syncFromSelect();

  return { trigger, open, close, syncFromSelect };
};

const customType = typeSelect ? bindCustomSelect(typeSelect) : null;
const customDescricao = descricaoSelect ? bindCustomSelect(descricaoSelect) : null;

if (fileInput && fileHint && fileStatus) {
  fileInput.addEventListener("change", () => {
    const files = fileInput.files ? Array.from(fileInput.files) : [];
    if (files.length === 0) {
      fileHint.textContent = "Nenhum arquivo selecionado";
      fileStatus.classList.remove("is-ready");
      return;
    }
    if (files.length === 1) {
      fileHint.textContent = files[0].name;
    } else {
      fileHint.textContent = `${files.length} arquivos selecionados`;
    }
    fileStatus.classList.add("is-ready");
  });
}

if (fileInput) {
  const setDroppedFiles = (files) => {
    if (!files || files.length === 0) return;
    const dataTransfer = new DataTransfer();
    Array.from(files).forEach((file) => dataTransfer.items.add(file));
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
    const files = event.dataTransfer && event.dataTransfer.files ? event.dataTransfer.files : null;
    setDroppedFiles(files);
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

if (razaoSocialNomeInput) {
  const applyRazaoMask = () => {
    razaoSocialNomeInput.value = sanitizeRazaoSocial(razaoSocialNomeInput.value);
  };
  razaoSocialNomeInput.addEventListener("input", applyRazaoMask);
  razaoSocialNomeInput.addEventListener("blur", applyRazaoMask);
}

if (cnpjCpfInput) {
  const applyCpfCnpjMask = () => {
    cnpjCpfInput.value = formatCpfCnpj(cnpjCpfInput.value);
  };
  cnpjCpfInput.addEventListener("input", applyCpfCnpjMask);
  cnpjCpfInput.addEventListener("blur", applyCpfCnpjMask);
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
      const files = fileInput.files ? Array.from(fileInput.files) : [];
      if (files.length > 0) {
        const invalid = files.find(
          (file) =>
            file.type !== "application/pdf" && !file.name.toLowerCase().endsWith(".pdf"),
        );
        if (invalid) {
          fileInput.setCustomValidity("Envie somente arquivos PDF.");
          hasError = true;
        }
      }
    }

    if (typeSelect) {
      typeSelect.setCustomValidity("");
      if (!typeSelect.value) {
        typeSelect.setCustomValidity("Selecione o tipo de lançamento.");
        if (customType?.trigger) {
          customType.trigger.classList.add("is-invalid");
          customType.open();
          customType.trigger.focus();
        }
        hasError = true;
      }
    }

    if (descricaoSelect) {
      descricaoSelect.setCustomValidity("");
      if (!descricaoSelect.value) {
        descricaoSelect.setCustomValidity("Selecione a descrição.");
        if (customDescricao?.trigger) {
          customDescricao.trigger.classList.add("is-invalid");
          customDescricao.open();
          customDescricao.trigger.focus();
        }
        hasError = true;
      }
    }

    if (hasError) {
      if (moneyInput) moneyInput.reportValidity();
      if (dateInput) dateInput.reportValidity();
      if (fileInput) fileInput.reportValidity();
      if (typeSelect) typeSelect.reportValidity();
      if (descricaoSelect) descricaoSelect.reportValidity();
      return;
    }

    const files = fileInput.files ? Array.from(fileInput.files) : [];
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
      const arquivosPdf = await filesToBase64(files);
      const nomesArquivos = filesToNames(files);
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
          arquivosPdf,
          nomesArquivos,
          tipo,
          descricao: descricaoSelect?.value || null,
          razaoSocialNome: razaoSocialNomeInput?.value || null,
          cnpjCpf: cnpjCpfInput?.value || null,
          observacao: observacaoInput?.value || null,
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
    if (descricaoSelect && lastSubmitOk) {
      descricaoSelect.setCustomValidity("");
      descricaoSelect.value = "";
    }
    if (razaoSocialNomeInput && lastSubmitOk) {
      razaoSocialNomeInput.setCustomValidity("");
      razaoSocialNomeInput.value = "";
    }
    if (cnpjCpfInput && lastSubmitOk) {
      cnpjCpfInput.setCustomValidity("");
      cnpjCpfInput.value = "";
    }
    if (observacaoInput && lastSubmitOk) {
      observacaoInput.setCustomValidity("");
      observacaoInput.value = "";
    }
  });
}
