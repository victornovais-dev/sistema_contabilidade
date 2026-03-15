const root = document.documentElement;
const toggle = document.querySelector(".theme-toggle");
const fileInput = document.querySelector(".file-input");
const fileHint = document.querySelector(".file-hint");
const fileStatus = document.querySelector(".file-status");
const moneyInput = document.querySelector(".money-input");
const dateInput = document.querySelector(".date-input");
const form = document.querySelector(".form");
const confirmOverlay = document.querySelector(".confirm-overlay");
const confirmClose = document.querySelector(".confirm-close");

const parseDate = (value) => {
  const digits = value.replace(/\D/g, "");
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
  const isDark = root.dataset.theme === "dark";
  toggle.setAttribute("aria-pressed", isDark ? "true" : "false");
  toggle.setAttribute("aria-label", isDark ? "Ativar modo claro" : "Ativar modo escuro");
  toggle.querySelector(".theme-icon").innerHTML = isDark
    ? '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="4.6" fill="currentColor"/><g stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><line x1="12" y1="2.4" x2="12" y2="5"/><line x1="12" y1="19" x2="12" y2="21.6"/><line x1="2.4" y1="12" x2="5" y2="12"/><line x1="19" y1="12" x2="21.6" y2="12"/><line x1="5.1" y1="5.1" x2="6.9" y2="6.9"/><line x1="17.1" y1="17.1" x2="18.9" y2="18.9"/><line x1="17.1" y1="6.9" x2="18.9" y2="5.1"/><line x1="5.1" y1="18.9" x2="6.9" y2="17.1"/></g></svg>'
    : '<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="12" cy="12" r="4.6" fill="currentColor"/><g stroke="currentColor" stroke-width="1.6" stroke-linecap="round"><line x1="12" y1="2.4" x2="12" y2="5"/><line x1="12" y1="19" x2="12" y2="21.6"/><line x1="2.4" y1="12" x2="5" y2="12"/><line x1="19" y1="12" x2="21.6" y2="12"/><line x1="5.1" y1="5.1" x2="6.9" y2="6.9"/><line x1="17.1" y1="17.1" x2="18.9" y2="18.9"/><line x1="17.1" y1="6.9" x2="18.9" y2="5.1"/><line x1="5.1" y1="18.9" x2="6.9" y2="17.1"/></g></svg>';
};

updateLabel();

toggle.addEventListener("click", () => {
  const isDark = root.dataset.theme === "dark";
  root.dataset.theme = isDark ? "light" : "dark";
  writeCookie("theme", root.dataset.theme);
  localStorage.setItem("theme", root.dataset.theme);
  updateLabel();
});

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
    const digits = value.replace(/\D/g, "").slice(0, 8);
    const parts = [];
    if (digits.length >= 1) parts.push(digits.slice(0, 2));
    if (digits.length >= 3) parts.push(digits.slice(2, 4));
    if (digits.length >= 5) parts.push(digits.slice(4, 8));
    return parts.join("/");
  };

  const updateDate = () => {
    dateInput.value = formatDate(dateInput.value);
    dateInput.setCustomValidity("");
  };

  dateInput.addEventListener("input", updateDate);
  dateInput.addEventListener("blur", updateDate);
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

    if (hasError) {
      if (moneyInput) moneyInput.reportValidity();
      if (dateInput) dateInput.reportValidity();
      if (fileInput) fileInput.reportValidity();
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
  });
}
