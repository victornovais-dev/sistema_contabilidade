const root = document.documentElement;
let toggle = document.querySelector(".theme-toggle");
const filterInput = document.querySelector(".filter-date-range");
const filterType = document.querySelector(".filter-type-select");
const filterClear = document.querySelector(".filter-clear");
const itemCards = Array.from(document.querySelectorAll(".item-card"));
const deleteButtons = Array.from(document.querySelectorAll(".delete-item"));
const confirmOverlay = document.querySelector(".confirm-overlay");
const confirmCancel = document.querySelector(".confirm-cancel");
const confirmDelete = document.querySelector(".confirm-delete");
let pendingDelete = null;

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

const applyFilter = () => {
  const dateQuery = filterInput ? filterInput.value.trim() : "";
  const typeQuery = filterType ? filterType.value.trim() : "";

  itemCards.forEach((card) => {
    const date = card.dataset.date || "";
    const type = card.dataset.type || "";

    let passDate = true;
    let passType = true;

    if (dateQuery) {
      const [startStr, endStr] = dateQuery.split(" até ").map((part) => part.trim());
      if (startStr && endStr) {
        const parse = (value) => {
          const [d, m, y] = value.split("/").map((v) => Number(v));
          if (!d || !m || !y) return null;
          const parsed = new Date(y, m - 1, d);
          parsed.setHours(0, 0, 0, 0);
          return parsed;
        };

        const start = parse(startStr);
        const end = parse(endStr);
        const current = parse(date);
        if (start && end && current) {
          passDate = current >= start && current <= end;
        }
      }
    }

    if (typeQuery) {
      passType = type === typeQuery;
    }

    card.style.display = passDate && passType ? "" : "none";
  });
};

if (filterInput) {
  if (window.flatpickr) {
    window.flatpickr(filterInput, {
      mode: "range",
      dateFormat: "d/m/Y",
      maxDate: "today",
      locale: {
        rangeSeparator: " até ",
      },
      onChange: applyFilter,
    });
  }
  filterInput.addEventListener("input", applyFilter);
}

if (filterType) {
  filterType.addEventListener("change", applyFilter);
}

if (filterClear) {
  filterClear.addEventListener("click", () => {
    if (filterInput) filterInput.value = "";
    if (filterType) filterType.value = "";
    applyFilter();
  });
}

deleteButtons.forEach((button) => {
  button.addEventListener("click", () => {
    pendingDelete = button.closest(".item-card");
    if (confirmOverlay) {
      confirmOverlay.classList.add("is-visible");
      confirmOverlay.setAttribute("aria-hidden", "false");
    }
  });
});

if (confirmCancel && confirmOverlay) {
  confirmCancel.addEventListener("click", () => {
    pendingDelete = null;
    confirmOverlay.classList.remove("is-visible");
    confirmOverlay.setAttribute("aria-hidden", "true");
  });
}

if (confirmDelete && confirmOverlay) {
  confirmDelete.addEventListener("click", () => {
    if (pendingDelete) {
      pendingDelete.remove();
      pendingDelete = null;
    }
    confirmOverlay.classList.remove("is-visible");
    confirmOverlay.setAttribute("aria-hidden", "true");
  });
}

bindThemeToggle();
