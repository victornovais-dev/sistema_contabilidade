(() => {
  const container = document.querySelector("[data-latest-launches]");
  if (!container) return;

  const formatCurrency = (value, type) => {
    const numericValue = Number(value || 0);
    const absoluteValue = Math.abs(numericValue);
    const formatted = absoluteValue.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
    return type === "RECEITA" ? `+${formatted}` : `-${formatted}`;
  };

  const formatDate = (value) => {
    if (!value) return "--/--";
    const date = new Date(`${value}T00:00:00`);
    if (!Number.isFinite(date.getTime())) return "--/--";
    return date.toLocaleDateString("pt-BR", {
      day: "2-digit",
      month: "2-digit",
    });
  };

  const formatDescricao = (value) => {
    const key = String(value || "").trim().toUpperCase();
    const labels = {
      ALUGUEL: "Aluguel",
      ENERGIA: "Energia el\u00E9trica",
      AGUA: "\u00C1gua",
      SERVICOS: "Servi\u00E7os",
      IMPOSTOS: "Impostos",
      MATERIAIS: "Materiais",
      OUTROS: "Outros",
    };
    return labels[key] || (value ? String(value) : "Sem descri\u00E7\u00E3o");
  };

  const formatNome = (value) => {
    const text = String(value || "").trim();
    return text || "Sem nome";
  };

  const renderEmptyState = (message) => {
    container.innerHTML = `<div class="ledger-empty">${message}</div>`;
  };

  const createItemMarkup = (item) => {
    const tipo = String(item?.tipo || "").toUpperCase();
    const positive = tipo === "RECEITA";
    const iconClass = positive ? "ledger-icon-positive" : "ledger-icon-negative";
    const valueClass = positive ? "ledger-value-positive" : "ledger-value-negative";
    const arrow = positive ? "&uarr;" : "&darr;";
    const tipoLabel = positive ? "Receita" : "Despesa";

    return `
      <article class="ledger-item">
        <div class="ledger-icon ${iconClass}">${arrow}</div>
        <div class="ledger-copy">
          <h3>${formatNome(item?.razaoSocialNome)}</h3>
          <p>${tipoLabel} &middot; ${formatDescricao(item?.descricao)} &middot; ${formatDate(item?.data)}</p>
        </div>
        <strong class="ledger-value ${valueClass}">${formatCurrency(item?.valor, tipo)}</strong>
      </article>
    `;
  };

  const load = async () => {
    try {
      const data = await window.scHomeDashboardData?.load();
      const latestItems = Array.isArray(data?.ultimosLancamentos) ? data.ultimosLancamentos : [];

      if (latestItems.length === 0) {
        renderEmptyState("N\u00E3o h\u00E1 dados para mostrar.");
        return;
      }

      container.innerHTML = latestItems.map(createItemMarkup).join("");
    } catch (error) {
      renderEmptyState("N\u00E3o foi poss\u00EDvel carregar os lan\u00E7amentos.");
    }
  };

  load();
  window.addEventListener("sc:home-role-change", () => {
    load();
  });
})();
