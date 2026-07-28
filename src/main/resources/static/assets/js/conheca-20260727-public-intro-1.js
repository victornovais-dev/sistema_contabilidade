(() => {
  "use strict";

  const root = document.documentElement;
  const themeToggle = document.querySelector(".theme-toggle");
  const form = document.getElementById("question-form");
  const nameInput = document.getElementById("question-name");
  const emailInput = document.getElementById("question-email");
  const questionInput = document.getElementById("question-text");
  const count = document.getElementById("question-count");
  const submitButton = document.getElementById("question-submit");
  const feedback = document.getElementById("question-feedback");

  const writeThemeCookie = (theme) => {
    const expires = new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toUTCString();
    document.cookie = `theme=${encodeURIComponent(theme)}; expires=${expires}; path=/; SameSite=Lax`;
  };

  const updateThemeLabel = () => {
    const dark = root.dataset.theme === "dark";
    themeToggle.setAttribute("aria-label", dark ? "Ativar tema claro" : "Ativar tema escuro");
  };

  themeToggle.addEventListener("click", () => {
    const theme = root.dataset.theme === "dark" ? "light" : "dark";
    root.dataset.theme = theme;
    writeThemeCookie(theme);
    try {
      localStorage.setItem("theme", theme);
    } catch (_) {
      // Cookie preserva preferência quando armazenamento local não está disponível.
    }
    updateThemeLabel();
  });

  const setFeedback = (message, state = "") => {
    feedback.textContent = message;
    if (state) {
      feedback.dataset.state = state;
    } else {
      delete feedback.dataset.state;
    }
  };

  const fetchCsrfToken = async () => {
    const response = await fetch("/api/v1/auth/csrf", {
      method: "GET",
      credentials: "same-origin",
    });
    if (!response.ok) {
      throw new Error("csrf");
    }
    const body = await response.json();
    if (!body.token) {
      throw new Error("csrf");
    }
    return body.token;
  };

  const errorMessage = (status) => {
    if (status === 429) {
      return "Muitas tentativas em pouco tempo. Aguarde um momento e tente novamente.";
    }
    if (status === 400) {
      return "Revise os campos. A dúvida deve ter entre 10 e 1200 caracteres.";
    }
    return "Não foi possível enviar agora. Tente novamente em alguns instantes.";
  };

  questionInput.addEventListener("input", () => {
    count.textContent = String(questionInput.value.length);
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    setFeedback("");

    if (!form.checkValidity()) {
      form.reportValidity();
      return;
    }

    submitButton.disabled = true;
    submitButton.querySelector("span:first-child").textContent = "Enviando...";

    try {
      const csrfToken = await fetchCsrfToken();
      const response = await fetch("/api/v1/duvidas", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-TOKEN": csrfToken,
        },
        body: JSON.stringify({
          nome: nameInput.value.trim(),
          email: emailInput.value.trim(),
          duvida: questionInput.value.trim(),
        }),
      });

      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        setFeedback(errorMessage(response.status), "error");
        return;
      }

      const protocol = body.protocolo ? ` Protocolo: ${body.protocolo}.` : "";
      setFeedback(`Dúvida enviada com sucesso.${protocol}`, "success");
      form.reset();
      count.textContent = "0";
    } catch (_) {
      setFeedback(
        "Não foi possível conectar ao atendimento. Verifique sua conexão e tente novamente.",
        "error",
      );
    } finally {
      submitButton.disabled = false;
      submitButton.querySelector("span:first-child").textContent = "Enviar dúvida";
    }
  });

  updateThemeLabel();
})();
