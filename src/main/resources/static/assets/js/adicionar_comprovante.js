const root = document.documentElement;
const navbarRoot = document.querySelector("#navbar-root");
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
  updateLabel();
  toggle.addEventListener("click", () => {
    const isDark = root.dataset.theme === "dark";
    root.dataset.theme = isDark ? "light" : "dark";
    writeCookie("theme", root.dataset.theme);
    localStorage.setItem("theme", root.dataset.theme);
    updateLabel();
  });
};

if (navbarRoot) {
  fetch("partials/navbar.html")
    .then((response) => response.text())
    .then((html) => {
      navbarRoot.innerHTML = html;
      bindThemeToggle();
    })
    .catch(() => {
      bindThemeToggle();
    });
} else {
  bindThemeToggle();
}

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
  form.addEventListener("submit", (event) => {
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
    if (confirmOverlay) {
      confirmOverlay.classList.add("is-visible");
      confirmOverlay.setAttribute("aria-hidden", "false");
    }
  });
}

if (confirmOverlay && confirmClose) {
  confirmClose.addEventListener("click", () => {
    confirmOverlay.classList.remove("is-visible");
    confirmOverlay.setAttribute("aria-hidden", "true");
    if (form) {
      form.reset();
    }
    if (fileStatus) {
      fileStatus.classList.remove("is-ready");
    }
    if (fileHint) {
      fileHint.textContent = "Nenhum arquivo selecionado";
    }
    if (moneyInput) {
      moneyInput.setCustomValidity("");
      moneyInput.value = "R$ 0,00";
    }
    if (dateInput) {
      dateInput.setCustomValidity("");
      dateInput.value = "";
    }
    if (typeSelect) {
      typeSelect.setCustomValidity("");
      typeSelect.value = "";
    }
  });
}
