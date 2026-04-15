(() => {
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

  const setText = (selector, text) => {
    const node = document.querySelector(selector);
    if (node) node.textContent = text;
  };

  const load = async () => {
    try {
      const data = await window.scHomeDashboardData?.load();
      if (!data) return;
      const receitasTotais = Number(data.totalReceitas || 0);
      const despesasTotais = Number(data.totalDespesas || 0);
      const utilizado = receitasTotais > 0 ? despesasTotais / receitasTotais : 0;
      const saldoFinal = Number(data.saldoFinal || 0);

      setText('[data-metric="receitasTotais"]', formatCurrency(receitasTotais));
      setText('[data-metric="despesasTotais"]', formatCurrency(despesasTotais));
      setText('[data-metric="utilizado"]', formatPercent(utilizado));
      setText('[data-metric="saldoEmCaixa"]', formatCurrency(saldoFinal));
    } catch (error) {
      // Best effort only; cards keep fallback values.
    }
  };

  load();
  window.addEventListener("sc:home-role-change", () => {
    load();
  });
})();
