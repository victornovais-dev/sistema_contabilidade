(() => {
  const feedback = document.getElementById("roles-feedback");
  const createRoleForm = document.getElementById("create-role-form");
  const createPermissionForm = document.getElementById("create-permission-form");
  const assignPermissionForm = document.getElementById("assign-permission-form");
  const roleNameInput = document.getElementById("role-name");
  const permissionNameInput = document.getElementById("permission-name");
  const assignRoleSelect = document.getElementById("assign-role-select");
  const assignPermissionSelect = document.getElementById("assign-permission-select");
  const rolesList = document.getElementById("roles-list");
  const permissionsList = document.getElementById("permissions-list");
  const rolesCount = document.getElementById("roles-count");
  const permissionsCount = document.getElementById("permissions-count");

  let csrfToken = null;

  const getAccessToken = () => localStorage.getItem("sc_access_token");

  const showFeedback = (message, type = "") => {
    if (!feedback) return;
    feedback.hidden = false;
    feedback.textContent = message;
    feedback.classList.toggle("is-error", type === "error");
    feedback.classList.toggle("is-success", type === "success");
  };

  const clearFeedback = () => {
    if (!feedback) return;
    feedback.hidden = true;
    feedback.textContent = "";
    feedback.classList.remove("is-error", "is-success");
  };

  const escapeHtml = (value) =>
    String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");

  const extractErrorMessage = async (response, fallback) => {
    try {
      const payload = await response.json();
      if (payload && typeof payload === "object") {
        return payload.message || payload.error || fallback;
      }
    } catch (error) {
      return fallback;
    }
    return fallback;
  };

  const buildAuthHeaders = () => {
    const accessToken = getAccessToken();
    if (!accessToken) {
      window.location.href = "/login";
      throw new Error("Usuario nao autenticado.");
    }
    return { Authorization: `Bearer ${accessToken}` };
  };

  const ensureCsrfToken = async (forceRefresh = false) => {
    if (!forceRefresh && csrfToken) return csrfToken;

    const response = await fetch("/api/v1/auth/csrf", {
      method: "GET",
      credentials: "same-origin",
      cache: "no-store",
      headers: buildAuthHeaders(),
    });

    if (!response.ok) {
      throw new Error("Falha ao obter token CSRF.");
    }

    const data = await response.json();
    csrfToken = data.token || null;
    if (!csrfToken) {
      throw new Error("Token CSRF ausente.");
    }
    return csrfToken;
  };

  const fetchJson = async (url) => {
    const response = await fetch(url, {
      method: "GET",
      credentials: "same-origin",
      headers: buildAuthHeaders(),
    });

    if (response.status === 401) {
      window.location.href = "/login";
      throw new Error("Sessao expirada.");
    }

    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, "Falha ao carregar dados."));
    }

    return response.json();
  };

  const postJson = async (url, body) => {
    const token = await ensureCsrfToken();

    let response = await fetch(url, {
      method: "POST",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-TOKEN": token,
        ...buildAuthHeaders(),
      },
      body: JSON.stringify(body),
    });

    if (response.status === 403) {
      const refreshedToken = await ensureCsrfToken(true);
      response = await fetch(url, {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-TOKEN": refreshedToken,
          ...buildAuthHeaders(),
        },
        body: JSON.stringify(body),
      });
    }

    if (response.status === 401) {
      window.location.href = "/login";
      throw new Error("Sessao expirada.");
    }

    if (!response.ok) {
      throw new Error(await extractErrorMessage(response, "Falha ao salvar dados."));
    }

    return response.json();
  };

  const setSelectOptions = (select, items) => {
    if (!(select instanceof HTMLSelectElement)) return;

    select.innerHTML = '<option value="" selected disabled>Selecione</option>';

    items.forEach((item) => {
      const option = document.createElement("option");
      option.value = item;
      option.textContent = item;
      select.appendChild(option);
    });
  };

  const renderRoles = (roles) => {
    const safeRoles = Array.isArray(roles) ? roles : [];
    if (rolesCount) {
      rolesCount.textContent = String(safeRoles.length);
    }

    if (!rolesList) return;

    if (safeRoles.length === 0) {
      rolesList.innerHTML = '<div class="report-row-empty"><span>Nenhuma role cadastrada.</span></div>';
      return;
    }

    rolesList.innerHTML = safeRoles
      .map((role) => {
        const nome = escapeHtml(role?.nome || "");
        const permissoes = Array.isArray(role?.permissoes) ? role.permissoes : [];
        const chips =
          permissoes.length > 0
            ? permissoes
                .slice()
                .sort((a, b) => String(a?.nome || "").localeCompare(String(b?.nome || ""), "pt-BR"))
                .map(
                  (permissao) =>
                    `<span class="permission-chip">${escapeHtml(permissao?.nome || "")}</span>`,
                )
                .join("")
            : '<div class="report-row-empty"><span>Sem permissoes vinculadas.</span></div>';

        return `
          <article class="report-row">
            <div class="role-row-header">
              <h3 class="role-row-title">${nome}</h3>
              <span class="report-count">${permissoes.length}</span>
            </div>
            <div class="permission-chips">${chips}</div>
          </article>
        `;
      })
      .join("");
  };

  const renderPermissions = (permissions) => {
    const safePermissions = Array.isArray(permissions) ? permissions : [];
    if (permissionsCount) {
      permissionsCount.textContent = String(safePermissions.length);
    }

    if (!permissionsList) return;

    if (safePermissions.length === 0) {
      permissionsList.innerHTML =
        '<div class="report-row-empty"><span>Nenhuma permissao cadastrada.</span></div>';
      return;
    }

    permissionsList.innerHTML = safePermissions
      .map(
        (permission) => `
          <article class="report-row">
            <span class="permission-name">${escapeHtml(permission?.nome || "")}</span>
          </article>
        `,
      )
      .join("");
  };

  const refresh = async () => {
    const [roles, permissions] = await Promise.all([
      fetchJson("/api/v1/admin/roles"),
      fetchJson("/api/v1/admin/permissoes"),
    ]);

    renderRoles(roles);
    renderPermissions(permissions);

    setSelectOptions(
      assignRoleSelect,
      Array.isArray(roles) ? roles.map((role) => String(role?.nome || "").trim()).filter(Boolean) : [],
    );

    setSelectOptions(
      assignPermissionSelect,
      Array.isArray(permissions)
        ? permissions.map((permission) => String(permission?.nome || "").trim()).filter(Boolean)
        : [],
    );
  };

  createRoleForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearFeedback();

    const nome = String(roleNameInput?.value || "").trim();
    if (!nome) return;

    try {
      await postJson("/api/v1/admin/roles", { nome });
      if (roleNameInput) roleNameInput.value = "";
      await refresh();
      showFeedback("Role criada com sucesso.", "success");
    } catch (error) {
      showFeedback(error instanceof Error ? error.message : "Falha ao criar role.", "error");
    }
  });

  createPermissionForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearFeedback();

    const nome = String(permissionNameInput?.value || "").trim();
    if (!nome) return;

    try {
      await postJson("/api/v1/admin/permissoes", { nome });
      if (permissionNameInput) permissionNameInput.value = "";
      await refresh();
      showFeedback("Permissao criada com sucesso.", "success");
    } catch (error) {
      showFeedback(
        error instanceof Error ? error.message : "Falha ao criar permissao.",
        "error",
      );
    }
  });

  assignPermissionForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    clearFeedback();

    const roleNome = String(assignRoleSelect?.value || "").trim();
    const permissao = String(assignPermissionSelect?.value || "").trim();

    if (!roleNome || !permissao) return;

    try {
      await postJson(`/api/v1/admin/roles/${encodeURIComponent(roleNome)}/permissoes`, {
        permissao,
      });

      if (assignRoleSelect) assignRoleSelect.selectedIndex = 0;
      if (assignPermissionSelect) assignPermissionSelect.selectedIndex = 0;

      await refresh();
      showFeedback("Permissao vinculada com sucesso.", "success");
    } catch (error) {
      showFeedback(
        error instanceof Error ? error.message : "Falha ao vincular permissao.",
        "error",
      );
    }
  });

  refresh().catch((error) => {
    showFeedback(
      error instanceof Error ? error.message : "Falha ao carregar dados de RBAC.",
      "error",
    );
  });
})();
