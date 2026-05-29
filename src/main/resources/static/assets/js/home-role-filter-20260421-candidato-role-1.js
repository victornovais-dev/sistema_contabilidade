(() => {
  const storageKey = "sc_home_selected_role";
  const technicalRoles = new Set(["ADMIN", "CONTABIL", "MANAGER", "SUPPORT", "CANDIDATO"]);
  const roleFilterBox = document.getElementById("home-role-filter-box");
  const roleFilterSelect = document.getElementById("home-role-filter-select");
  const roleDropdown =
    typeof window.createRoleDropdown === "function" && roleFilterSelect
      ? window.createRoleDropdown({
          select: roleFilterSelect,
          onChange: (value) => {
            setSelectedRole(value || "");
          },
        })
      : null;

  const getAccessToken = () => localStorage.getItem("sc_access_token");

  const getSelectedRole = () => String(localStorage.getItem(storageKey) || "").trim();

  const setSelectedRole = (role) => {
    const normalizedRole = String(role || "").trim();
    if (normalizedRole) {
      localStorage.setItem(storageKey, normalizedRole);
    } else {
      localStorage.removeItem(storageKey);
    }

    window.dispatchEvent(
      new CustomEvent("sc:home-role-change", {
        detail: { role: normalizedRole },
      }),
    );
  };

  const orderRoles = (roles) => {
    const normalizedRoles = Array.isArray(roles)
      ? [
          ...new Set(
            roles
              .map((role) => String(role || "").trim())
              .filter((role) => role && !technicalRoles.has(role.toUpperCase())),
          ),
        ].sort((a, b) => a.localeCompare(b, "pt-BR"))
      : [];
    return normalizedRoles;
  };

  const hideRoleFilter = () => {
    if (roleFilterBox) {
      roleFilterBox.hidden = true;
    }
    if (roleFilterSelect) {
      roleFilterSelect.innerHTML = "";
    }
    roleDropdown?.clear();
    setSelectedRole("");
  };

  const renderCustomOptions = (roles) => {
    roleDropdown?.setOptions(roles);
  };

  const applyRoleOptions = (roles) => {
    if (!roleFilterBox || !roleFilterSelect) return;

    const orderedRoles = orderRoles(roles);
    if (orderedRoles.length === 0) {
      hideRoleFilter();
      return;
    }

    renderCustomOptions(orderedRoles);

    const currentRole = getSelectedRole();
    const nextRole = orderedRoles.includes(currentRole) ? currentRole : orderedRoles[0];

    roleFilterSelect.value = nextRole;
    roleFilterBox.hidden = false;
    roleDropdown?.setValue(nextRole);
    setSelectedRole(nextRole);
  };

  const loadRoleOptions = async () => {
    const accessToken = getAccessToken();
    if (!accessToken || !roleFilterBox || !roleFilterSelect) return;

    const response = await fetch("/api/v1/itens/roles", {
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
      hideRoleFilter();
      return;
    }

    const roles = await response.json();
    applyRoleOptions(roles);
  };
  loadRoleOptions().catch(() => {
    hideRoleFilter();
  });
})();
