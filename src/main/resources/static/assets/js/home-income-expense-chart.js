(() => {
  const chart = document.querySelector("[data-income-expense-chart]");
  if (!chart) return;

  const getAccessToken = () => localStorage.getItem("sc_access_token");

  const monthFormatter = new Intl.DateTimeFormat("pt-BR", { month: "short" });

  const normalizeMonthLabel = (date) => {
    const label = monthFormatter.format(date).replace(".", "");
    return label.charAt(0).toUpperCase() + label.slice(1);
  };

  const getMonthBuckets = () => {
    const now = new Date();
    now.setDate(1);
    now.setHours(0, 0, 0, 0);

    return Array.from({ length: 6 }, (_, index) => {
      const date = new Date(now.getFullYear(), now.getMonth() - (5 - index), 1);
      return {
        key: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`,
        label: normalizeMonthLabel(date),
        income: 0,
        expense: 0,
        isCurrent: index === 5,
      };
    });
  };

  const createMonthMarkup = (bucket, maxValue) => {
    const incomeHeight = maxValue > 0 ? Math.max((bucket.income / maxValue) * 100, bucket.income > 0 ? 12 : 0) : 0;
    const expenseHeight =
      maxValue > 0 ? Math.max((bucket.expense / maxValue) * 100, bucket.expense > 0 ? 12 : 0) : 0;

    return `
      <div class="chart-month ${bucket.isCurrent ? "chart-month-highlight" : ""}">
        <div class="chart-bars">
          <span class="chart-bar chart-bar-income" style="--bar-height: ${incomeHeight}%"></span>
          <span class="chart-bar chart-bar-expense" style="--bar-height: ${expenseHeight}%"></span>
        </div>
        <span class="chart-month-label">${bucket.label}</span>
      </div>
    `;
  };

  const load = async () => {
    const accessToken = getAccessToken();
    if (!accessToken) return;

    try {
      const response = await fetch("/api/v1/itens", {
        method: "GET",
        credentials: "same-origin",
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });

      if (response.status === 401) {
        window.location.href = "/login";
        return;
      }

      if (!response.ok) {
        return;
      }

      const items = await response.json().catch(() => []);
      const buckets = getMonthBuckets();
      const bucketMap = new Map(buckets.map((bucket) => [bucket.key, bucket]));

      if (Array.isArray(items)) {
        items.forEach((item) => {
          const rawDate = item?.data;
          const tipo = String(item?.tipo || "").toUpperCase();
          const valor = Math.abs(Number(item?.valor || 0));
          if (!rawDate || !valor) return;

          const date = new Date(`${rawDate}T00:00:00`);
          if (!Number.isFinite(date.getTime())) return;

          const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
          const bucket = bucketMap.get(key);
          if (!bucket) return;

          if (tipo === "RECEITA") {
            bucket.income += valor;
            return;
          }

          if (tipo === "DESPESA") {
            bucket.expense += valor;
          }
        });
      }

      const maxValue = buckets.reduce(
        (currentMax, bucket) => Math.max(currentMax, bucket.income, bucket.expense),
        0,
      );

      chart.innerHTML = buckets.map((bucket) => createMonthMarkup(bucket, maxValue)).join("");
    } catch (error) {
      // Keep fallback markup already present in the HTML.
    }
  };

  load();
})();
