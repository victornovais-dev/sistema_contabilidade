const root = document.documentElement;
const toggle = document.querySelector(".theme-toggle");
const loginForm = document.getElementById("login-form");
const feedback = document.getElementById("login-feedback");
const emailInput = document.getElementById("email");
const passwordInput = document.getElementById("senha");
let csrfToken = null;

const FIRST_ACCESS_EMAIL_KEY = "sc_first_login_email";
const FIRST_ACCESS_MESSAGE_KEY = "sc_first_login_message";

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

const carregarCsrfToken = async () => {
  if (window.SCAuth?.ensureCsrfToken) {
    csrfToken = await window.SCAuth.ensureCsrfToken();
    if (csrfToken) {
      return;
    }
  }
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
    throw new Error("Token CSRF ausente na resposta");
  }
};

const setFeedback = (message, state = "") => {
  feedback.textContent = message || "";
  if (state) {
    feedback.dataset.state = state;
    return;
  }
  delete feedback.dataset.state;
};

const redirectToFirstAccess = (email, message) => {
  sessionStorage.setItem(FIRST_ACCESS_EMAIL_KEY, email);
  if (message) {
    sessionStorage.setItem(FIRST_ACCESS_MESSAGE_KEY, message);
  } else {
    sessionStorage.removeItem(FIRST_ACCESS_MESSAGE_KEY);
  }
  window.location.href = "/primeiro_acesso";
};

const handleAuthenticatedResponse = async (data) => {
  if (!data.accessToken) {
    setFeedback("Resposta sem token de acesso", "error");
    return;
  }

  sessionStorage.removeItem(FIRST_ACCESS_EMAIL_KEY);
  sessionStorage.removeItem(FIRST_ACCESS_MESSAGE_KEY);

  if (window.SCAuth?.storeAccessToken) {
    await window.SCAuth.storeAccessToken(data.accessToken);
  } else {
    localStorage.setItem("sc_access_token", data.accessToken);
  }
  setFeedback("Login realizado com sucesso. Redirecionando...", "success");
  window.location.href = "/home";
};

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

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  setFeedback("Autenticando...");

  const email = emailInput.value.trim();
  const senha = passwordInput.value;

  try {
    if (!csrfToken) {
      await carregarCsrfToken();
    }

    const response = await fetch("/api/v1/auth/login", {
      method: "POST",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": csrfToken,
      },
      body: JSON.stringify({ email, senha }),
    });
    const data = await response.json().catch(() => ({}));

    if (response.status === 202 && data.challengeRequired) {
      redirectToFirstAccess(email, data.message);
      return;
    }

    if (!response.ok) {
      setFeedback(data.message || data.error || "Falha no login", "error");
      return;
    }

    await handleAuthenticatedResponse(data);
  } catch (error) {
    setFeedback("Erro de conexao com o servidor", "error");
  }
});

window.SCAuth?.waitUntilReady?.().then((authenticated) => {
  if (authenticated && window.location.pathname === "/login") {
    window.location.href = "/home";
  }
});
