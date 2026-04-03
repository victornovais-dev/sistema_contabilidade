(() => {
  const getAccessToken = () => localStorage.getItem("sc_access_token");

  const formatCurrency = (value) => {
    const numericValue = Number(value || 0);
    const signal = numericValue < 0 ? "-" : "";
    const absoluteValue = Math.abs(numericValue);
    return `${signal}${absoluteValue.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    })}`;
  };

  const setText = (selector, text) => {
    const node = document.querySelector(selector);
    if (node) node.textContent = text;
  };

  const hidePills = () => {
    document.querySelectorAll("[data-pill]").forEach((node) => {
      if (node instanceof HTMLElement) {
        node.hidden = true;
        node.textContent = "";
      }
    });
  };

  const load = async () => {
    const accessToken = getAccessToken();
    if (!accessToken) return;

    hidePills();

    try {
      const response = await fetch("/api/v1/relatorios/financeiro", {
        method: "GET",
        credentials: "same-origin",
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });
      if (!response.ok) return;

      const data = await response.json().catch(() => ({}));

      const receitas = Number(data.totalReceitas || 0);
      const despesas = Number(data.totalDespesas || 0);
      const saldoFinal = Number(data.saldoFinal || 0);

      setText('[data-metric="receitas"]', formatCurrency(receitas));
      setText('[data-metric="despesas"]', formatCurrency(despesas));
      setText('[data-metric="saldoFinal"]', formatCurrency(saldoFinal));
      setText('[data-metric="saldoEmCaixa"]', formatCurrency(saldoFinal));
    } catch (error) {
      // Best effort only; cards keep fallback values.
    }
  };

  load();
})();

