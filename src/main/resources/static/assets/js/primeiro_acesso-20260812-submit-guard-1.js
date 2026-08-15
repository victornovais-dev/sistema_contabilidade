const root = document.documentElement;
const toggle = document.querySelector(".theme-toggle");
const firstAccessForm = document.getElementById("first-access-form");
const feedback = document.getElementById("first-access-feedback");
const emailInput = document.getElementById("first-access-email");
const passwordInput = document.getElementById("nova-senha");
const confirmPasswordInput = document.getElementById("confirmar-nova-senha");
const passwordCriteriaItems = document.querySelectorAll("[data-password-rule]");
const passwordStrengthMeter = document.getElementById("password-strength-meter");
const passwordStrengthFill = document.querySelector(".password-strength-fill");
const passwordStrengthLabel = document.getElementById("password-strength-label");
const passwordMatchStatus = document.getElementById("password-match-status");
const firstAccessSubmitButton = document.getElementById("first-access-submit");
const expiredChallengeCard = document.getElementById("expired-challenge-card");
const expiredChallengeLoginLink = document.getElementById("expired-challenge-login-link");
let csrfToken = null;
let firstAccessSubmissionInProgress = false;

const FIRST_ACCESS_EMAIL_KEY = "sc_first_login_email";
const FIRST_ACCESS_MESSAGE_KEY = "sc_first_login_message";
const COGNITO_SPECIAL_CHARACTERS = "^$*.[\\]{}()?\"!@#%&/\\\\,><':;|_~`=+-";
const PASSWORD_RULES = {
  length: (password) => password.length >= 8,
  uppercase: (password) => /[A-Z]/.test(password),
  lowercase: (password) => /[a-z]/.test(password),
  number: (password) => /[0-9]/.test(password),
  special: (password) =>
    Array.from(password).some((character) => COGNITO_SPECIAL_CHARACTERS.includes(character)),
};
const PASSWORD_STRENGTH_LABELS = [
  "Muito fraca",
  "Muito fraca",
  "Fraca",
  "Média",
  "Forte",
  "Muito forte",
];

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

const clearFirstAccessState = () => {
  sessionStorage.removeItem(FIRST_ACCESS_EMAIL_KEY);
  sessionStorage.removeItem(FIRST_ACCESS_MESSAGE_KEY);
};

const showExpiredChallengeCard = () => {
  clearFirstAccessState();
  firstAccessForm.hidden = true;
  expiredChallengeCard.hidden = false;
  setFeedback("");
  expiredChallengeLoginLink.focus();
};

const setFirstAccessSubmissionInProgress = (inProgress) => {
  firstAccessSubmissionInProgress = inProgress;
  firstAccessSubmitButton.disabled = inProgress;
  firstAccessSubmitButton.setAttribute("aria-busy", String(inProgress));
};

const updatePasswordCriteria = () => {
  const password = passwordInput.value;
  let validRuleCount = 0;
  passwordCriteriaItems.forEach((item) => {
    const rule = PASSWORD_RULES[item.dataset.passwordRule];
    const isValid = typeof rule === "function" && rule(password);
    if (isValid) {
      validRuleCount += 1;
    }
    item.classList.toggle("is-valid", isValid);
    item.setAttribute("aria-label", `${item.textContent}: ${isValid ? "atendido" : "não atendido"}`);
  });

  const strengthPercentage = Math.round(
    (validRuleCount / Math.max(passwordCriteriaItems.length, 1)) * 100,
  );
  const strengthLabel = PASSWORD_STRENGTH_LABELS[validRuleCount] || "Muito fraca";
  passwordStrengthFill.style.width = `${strengthPercentage}%`;
  passwordStrengthLabel.textContent = strengthLabel;
  passwordStrengthMeter.setAttribute("aria-valuenow", String(strengthPercentage));
  passwordStrengthMeter.setAttribute("aria-valuetext", strengthLabel);
};

const updatePasswordMatch = () => {
  const confirmation = confirmPasswordInput.value;
  const hasConfirmation = confirmation.length > 0;
  const passwordsMatch = hasConfirmation && passwordInput.value === confirmation;

  passwordMatchStatus.hidden = !hasConfirmation;
  passwordMatchStatus.classList.toggle("is-valid", passwordsMatch);
  passwordMatchStatus.textContent = passwordsMatch
    ? "As senhas coincidem."
    : "As senhas não coincidem.";
  confirmPasswordInput.setCustomValidity(
    hasConfirmation && !passwordsMatch ? "As senhas não coincidem." : "",
  );
  return passwordsMatch;
};

const bootstrapPage = () => {
  const email = sessionStorage.getItem(FIRST_ACCESS_EMAIL_KEY) || "";
  sessionStorage.removeItem(FIRST_ACCESS_MESSAGE_KEY);
  emailInput.value = email;
  if (!email) {
    emailInput.placeholder = "Usuario do primeiro acesso";
  }
  setFeedback("");
};

const handleAuthenticatedResponse = async (data) => {
  if (!data.accessToken) {
    setFeedback("Resposta sem token de acesso", "error");
    return false;
  }

  clearFirstAccessState();

  if (window.SCAuth?.storeAccessToken) {
    await window.SCAuth.storeAccessToken(data.accessToken);
  } else {
    localStorage.setItem("sc_access_token", data.accessToken);
  }
  setFeedback("Senha atualizada com sucesso. Redirecionando...", "success");
  window.location.href = "/home";
  return true;
};

updateLabel();
bootstrapPage();
updatePasswordCriteria();
updatePasswordMatch();

toggle.addEventListener("click", () => {
  const isDark = root.dataset.theme === "dark";
  root.dataset.theme = isDark ? "light" : "dark";
  writeCookie("theme", root.dataset.theme);
  localStorage.setItem("theme", root.dataset.theme);
  updateLabel();
});

passwordInput.addEventListener("input", () => {
  updatePasswordCriteria();
  updatePasswordMatch();
});
confirmPasswordInput.addEventListener("input", updatePasswordMatch);

firstAccessForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (firstAccessSubmissionInProgress) {
    return;
  }

  const novaSenha = passwordInput.value;
  const confirmarSenha = confirmPasswordInput.value;

  if (novaSenha !== confirmarSenha || !updatePasswordMatch()) {
    setFeedback("A confirmacao da nova senha nao confere", "error");
    confirmPasswordInput.reportValidity();
    return;
  }

  setFirstAccessSubmissionInProgress(true);
  setFeedback("Salvando nova senha...");
  let authenticated = false;
  try {
    if (!csrfToken) {
      await carregarCsrfToken();
    }

    const response = await fetch("/api/v1/auth/complete-new-password", {
      method: "POST",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": csrfToken,
      },
      body: JSON.stringify({ novaSenha }),
    });
    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
      if (response.status === 401) {
        showExpiredChallengeCard();
        return;
      }
      setFeedback(
        data.message || data.error || "Nao foi possivel concluir o primeiro acesso",
        "error",
      );
      return;
    }

    authenticated = await handleAuthenticatedResponse(data);
  } catch (error) {
    setFeedback("Erro de conexao com o servidor", "error");
  } finally {
    if (!authenticated) {
      setFirstAccessSubmissionInProgress(false);
    }
  }
});

window.SCAuth?.waitUntilReady?.().then((authenticated) => {
  if (authenticated) {
    window.location.href = "/home";
  }
});
