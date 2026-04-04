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

  const formatPercent = (ratio) => {
    const numeric = Number(ratio);
    if (!Number.isFinite(numeric)) {
      return "0%";
    }
    return numeric.toLocaleString("pt-BR", {
      style: "percent",
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    });
  };

  const clamp01 = (value) => Math.min(1, Math.max(0, Number(value)));

  const utilizedHue = (ratio) => {
    const normalized = clamp01(ratio);
    return (1 - normalized) * 120;
  };

  const setText = (selector, text) => {
    const node = document.querySelector(selector);
    if (node) node.textContent = text;
  };

  const setPillText = (metric, text) => {
    const node = document.querySelector(`[data-pill="${metric}"]`);
    if (!node) return;
    if (!text) {
      node.textContent = "";
      node.hidden = true;
      return;
    }
    node.textContent = text;
    node.hidden = false;
  };

  const setUtilizadoStyles = (ratio) => {
    const node = document.querySelector('[data-metric="utilizado"]');
    if (!node) return;
    node.style.setProperty("--util-hue", utilizedHue(ratio).toFixed(1));
    node.style.setProperty("--util-hue2", Math.max(0, utilizedHue(ratio) - 18).toFixed(1));
  };

  const load = async () => {
    const accessToken = getAccessToken();
    if (!accessToken) return;

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
      const utilizadoRatio = receitas > 0 ? despesas / receitas : 0;

      setText('[data-metric="receitas"]', formatCurrency(receitas));
      setText('[data-metric="despesas"]', formatCurrency(despesas));
      setText('[data-metric="utilizado"]', formatPercent(utilizadoRatio));
      setText('[data-metric="saldoEmCaixa"]', formatCurrency(saldoFinal));
      setUtilizadoStyles(utilizadoRatio);

      setPillText("receitas", "");
      setPillText("despesas", "");
      setPillText("utilizado", "");
    } catch (error) {
      // Best effort only; cards keep fallback values.
    }
  };

  load();
})();
