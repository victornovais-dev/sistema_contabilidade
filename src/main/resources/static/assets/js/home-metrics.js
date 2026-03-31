(() => {
  const getAccessToken = () => localStorage.getItem("sc_access_token");

  const MONTH_ABBR = [
    "jan",
    "fev",
    "mar",
    "abr",
    "mai",
    "jun",
    "jul",
    "ago",
    "set",
    "out",
    "nov",
    "dez",
  ];

  const monthKey = (date) => {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, "0");
    return `${y}-${m}`;
  };

  const previousMonth = (date) => new Date(date.getFullYear(), date.getMonth() - 1, 1);

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
    if (!Number.isFinite(numeric)) return "0%";
    const abs = Math.abs(numeric);
    const digits = abs < 0.1 ? 1 : 0;
    return numeric.toLocaleString("pt-BR", {
      style: "percent",
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    });
  };

  const setText = (selector, text) => {
    const node = document.querySelector(selector);
    if (node) node.textContent = text;
  };

  const setPill = (metricKey, diffRatio, prevMonthDate) => {
    const node = document.querySelector(`[data-pill="${metricKey}"]`);
    if (!node) return;

    if (!Number.isFinite(diffRatio)) {
      const prevAbbr = MONTH_ABBR[prevMonthDate.getMonth()] || "";
      node.textContent = `— vs ${prevAbbr}`;
      node.hidden = false;
      return;
    }

    const arrow = diffRatio >= 0 ? "↑" : "↓";
    const prevAbbr = MONTH_ABBR[prevMonthDate.getMonth()] || "";
    node.textContent = `${arrow} ${formatPercent(diffRatio)} vs ${prevAbbr}`;
    node.hidden = false;
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

      setText('[data-metric="receitas"]', formatCurrency(receitas));
      setText('[data-metric="despesas"]', formatCurrency(despesas));
      setText('[data-metric="saldoFinal"]', formatCurrency(saldoFinal));
      setText('[data-metric="saldoEmCaixa"]', formatCurrency(saldoFinal));

      const now = new Date();
      const prev = previousMonth(now);
      const currentKey = `sc_home_metrics_${monthKey(now)}`;
      const prevKey = `sc_home_metrics_${monthKey(prev)}`;

      const prevSnapshot = (() => {
        try {
          const raw = localStorage.getItem(prevKey);
          return raw ? JSON.parse(raw) : null;
        } catch (error) {
          return null;
        }
      })();

      if (
        prevSnapshot &&
        Number.isFinite(Number(prevSnapshot.receitas)) &&
        Number.isFinite(Number(prevSnapshot.despesas)) &&
        Number.isFinite(Number(prevSnapshot.saldoFinal))
      ) {
        const prevReceitas = Number(prevSnapshot.receitas);
        const prevDespesas = Number(prevSnapshot.despesas);
        const prevSaldoFinal = Number(prevSnapshot.saldoFinal);

        setPill("receitas", prevReceitas !== 0 ? (receitas - prevReceitas) / prevReceitas : NaN, prev);
        setPill("despesas", prevDespesas !== 0 ? (despesas - prevDespesas) / prevDespesas : NaN, prev);
        setPill(
          "saldoFinal",
          prevSaldoFinal !== 0 ? (saldoFinal - prevSaldoFinal) / prevSaldoFinal : NaN,
          prev,
        );
      } else {
        setPill("receitas", NaN, prev);
        setPill("despesas", NaN, prev);
        setPill("saldoFinal", NaN, prev);
      }

      try {
        localStorage.setItem(
          currentKey,
          JSON.stringify({
            receitas,
            despesas,
            saldoFinal,
            updatedAt: new Date().toISOString(),
          }),
        );
      } catch (error) {
        // ignore
      }
    } catch (error) {
      // Best effort only; cards keep fallback values.
    }
  };

  load();
})();
