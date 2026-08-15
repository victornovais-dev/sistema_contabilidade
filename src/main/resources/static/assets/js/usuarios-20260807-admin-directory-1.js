const usersTableBody = document.getElementById("users-table-body");
const usersSearch = document.getElementById("users-search");
const usersCount = document.getElementById("users-count");
const usersFeedback = document.getElementById("users-feedback");
const usersRetry = document.getElementById("users-retry");

const ADMIN_ROUTE_CONFIG_ERROR_MESSAGE = "Rotas administrativas indisponiveis para a sessao atual.";

let adminRoutePaths = null;
let users = [];

const getAccessToken = () => {
  if (window.SCAuth?.getAccessToken) {
    return window.SCAuth.getAccessToken();
  }
  return localStorage.getItem("sc_access_token") || "";
};

const normalizeSearch = (value) => String(value || "").trim().toLocaleLowerCase("pt-BR");

const resolveAdminRoutePaths = async (forceRefresh = false) => {
  if (!forceRefresh && adminRoutePaths) {
    return adminRoutePaths;
  }
  if (!window.SCAuth?.requireAdminRouteConfig) {
    throw new Error(ADMIN_ROUTE_CONFIG_ERROR_MESSAGE);
  }

  adminRoutePaths = await window.SCAuth.requireAdminRouteConfig(forceRefresh);
  return adminRoutePaths;
};

const setFeedback = (message, isError = false) => {
  usersFeedback.textContent = message || "";
  usersFeedback.classList.toggle("is-error", isError);
};

const filteredUsers = () => {
  const query = normalizeSearch(usersSearch.value);
  if (!query) {
    return users;
  }
  return users.filter((user) => {
    const id = normalizeSearch(user.id);
    const email = normalizeSearch(user.email);
    return id.includes(query) || email.includes(query);
  });
};

const renderUsers = () => {
  const visibleUsers = filteredUsers();
  usersTableBody.replaceChildren();

  visibleUsers.forEach((user) => {
    const row = document.createElement("tr");
    const id = document.createElement("td");
    const email = document.createElement("td");

    id.textContent = user.id || "-";
    email.textContent = user.email || "-";
    email.className = "user-email";
    row.append(id, email);
    usersTableBody.appendChild(row);
  });

  if (visibleUsers.length === 0) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = 2;
    cell.className = "empty-row";
    cell.textContent = users.length === 0 ? "Nenhum usuário cadastrado." : "Nenhum usuário encontrado.";
    row.appendChild(cell);
    usersTableBody.appendChild(row);
  }

  usersCount.textContent = `${visibleUsers.length} de ${users.length} usuário(s)`;
};

const loadUsers = async () => {
  usersRetry.hidden = true;
  setFeedback("Carregando usuários...");

  try {
    const { adminUserApiBasePath } = await resolveAdminRoutePaths();
    const response = await fetch(adminUserApiBasePath, {
      method: "GET",
      headers: {
        Authorization: `Bearer ${getAccessToken()}`,
      },
      credentials: "same-origin",
      cache: "no-store",
    });

    if (response.status === 401) {
      window.location.href = "/login";
      return;
    }
    if (response.status === 403) {
      throw new Error("Você não tem permissão para consultar usuários.");
    }
    if (!response.ok) {
      throw new Error("Não foi possível carregar os usuários.");
    }

    const payload = await response.json();
    users = Array.isArray(payload) ? payload : [];
    setFeedback("");
    renderUsers();
  } catch (error) {
    users = [];
    renderUsers();
    usersRetry.hidden = false;
    setFeedback(error instanceof Error ? error.message : "Erro de conexão com o servidor.", true);
  }
};

usersSearch.addEventListener("input", renderUsers);
usersRetry.addEventListener("click", () => {
  void loadUsers();
});
window.addEventListener("sc:routes-updated", () => {
  adminRoutePaths = null;
});

if (!getAccessToken()) {
  window.location.href = "/login";
} else {
  void loadUsers();
}
