(() => {
  const nameEl = document.querySelector("[data-first-name]");
  const prefixEl = document.querySelector("[data-welcome-prefix]");
  const monthEl = document.querySelector("[data-current-month]");
  if (!nameEl) return;

  const formatCurrentMonth = () => {
    if (!monthEl) return;
    const label = new Date().toLocaleDateString("pt-BR", {
      month: "long",
      year: "numeric",
    });
    monthEl.textContent = label.charAt(0).toUpperCase() + label.slice(1);
  };

  const setName = (rawName) => {
    const clean = typeof rawName === "string" ? rawName.trim() : "";
    const first = clean.split(/\s+/).filter(Boolean)[0] || "";
    if (!first) {
      nameEl.textContent = "";
      nameEl.hidden = true;
      if (prefixEl) prefixEl.textContent = "Bem-vindo de volta";
      return;
    }
    if (prefixEl) prefixEl.textContent = "Bem-vindo de volta,";
    nameEl.hidden = false;
    nameEl.textContent = first;
  };

  const load = async () => {
    formatCurrentMonth();

    try {
      const response = await fetch("/api/v1/usuarios/me", { credentials: "same-origin" });
      if (!response.ok) throw new Error("Falha ao carregar usu\u00E1rio");
      const data = await response.json().catch(() => ({}));
      setName(data.nome);
    } catch (error) {
      setName("");
    }
  };

  load();
})();
