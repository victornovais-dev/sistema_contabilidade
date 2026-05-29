(() => {
  const storageKey = "sc_home_selected_role";
  let cachedRole = null;
  let cachedPromise = null;

  const getAccessToken = () => localStorage.getItem("sc_access_token");
  const getSelectedRole = () => String(localStorage.getItem(storageKey) || "").trim();
  const buildRoleQuery = () => {
    const role = getSelectedRole();
    return role ? `?${new URLSearchParams({ role }).toString()}` : "";
  };

  const loadDashboard = async () => {
    const accessToken = getAccessToken();
    if (!accessToken) {
      return null;
    }

    const response = await fetch(`/api/v1/home/dashboard${buildRoleQuery()}`, {
      method: "GET",
      credentials: "same-origin",
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
    });

    if (response.status === 401) {
      window.location.href = "/login";
      return null;
    }
    if (!response.ok) {
      return null;
    }
    return response.json().catch(() => null);
  };

  window.scHomeDashboardData = {
    load() {
      const currentRole = getSelectedRole();
      if (!cachedPromise || cachedRole !== currentRole) {
        cachedRole = currentRole;
        cachedPromise = loadDashboard();
      }
      return cachedPromise;
    },
    invalidate() {
      cachedRole = null;
      cachedPromise = null;
    },
  };

  window.addEventListener("sc:home-role-change", () => {
    window.scHomeDashboardData?.invalidate();
  });
})();
