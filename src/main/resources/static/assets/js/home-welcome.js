(() => {
  const nameEl = document.querySelector("[data-first-name]");
  const prefixEl = document.querySelector("[data-welcome-prefix]");
  if (!nameEl) return;

  const setName = (rawName) => {
    const clean = typeof rawName === "string" ? rawName.trim() : "";
    const first = clean.split(/\s+/).filter(Boolean)[0] || "";
    if (!first) {
      nameEl.textContent = "";
      nameEl.hidden = true;
      if (prefixEl) prefixEl.textContent = "Bem vindo de volta";
      return;
    }
    if (prefixEl) prefixEl.textContent = "Bem vindo de volta,";
    nameEl.hidden = false;
    nameEl.textContent = first;
  };

  const load = async () => {
    try {
      const response = await fetch("/api/v1/usuarios/me", { credentials: "same-origin" });
      if (!response.ok) throw new Error("Falha ao carregar usuário");
      const data = await response.json().catch(() => ({}));
      setName(data.nome);
    } catch (error) {
      setName("");
    }
  };

  load();
})();
