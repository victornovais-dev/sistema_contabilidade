(() => {
  const chart = document.querySelector("[data-income-expense-chart]");
  if (!chart) return;

  const escapeHtml = (value) =>
    String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");

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
        <span class="chart-month-label">${escapeHtml(bucket.label)}</span>
      </div>
    `;
  };

  const load = async () => {
    try {
      const data = await window.scHomeDashboardData?.load();
      const buckets = Array.isArray(data?.graficoMensal) ? data.graficoMensal : [];

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
  window.addEventListener("sc:home-role-change", () => {
    load();
  });
})();
