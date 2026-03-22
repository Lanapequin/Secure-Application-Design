"use strict";

const getToken    = () => sessionStorage.getItem(CONFIG.TOKEN_KEY);
const getUsername = () => sessionStorage.getItem(CONFIG.USERNAME_KEY);
const isLoggedIn  = () => !!getToken();

function saveSession(token, username) {
  sessionStorage.setItem(CONFIG.TOKEN_KEY,    token);
  sessionStorage.setItem(CONFIG.USERNAME_KEY, username);
}
function clearSession() {
  sessionStorage.removeItem(CONFIG.TOKEN_KEY);
  sessionStorage.removeItem(CONFIG.USERNAME_KEY);
}

async function http(path, options = {}) {
  const ctrl = new AbortController();
  const tid  = setTimeout(() => ctrl.abort(), CONFIG.TIMEOUT_MS);
  try {
    const res  = await fetch(CONFIG.SPRING_URL + path,
                             { ...options, signal: ctrl.signal });
    const json = await res.json();
    if (!res.ok) throw new Error(json.error || json.message || `HTTP ${res.status}`);
    return json;
  } catch (e) {
    if (e.name === "AbortError") throw new Error("Request timed out");
    throw e;
  } finally {
    clearTimeout(tid);
  }
}

async function authHttp(path, options = {}) {
  const token = getToken();
  if (!token) throw new Error("Not authenticated");
  return http(path, {
    ...options,
    headers: {
      "Content-Type":  "application/json",
      "Authorization": `Bearer ${token}`,
      ...(options.headers || {}),
    },
  });
}

function msg(id, text, type) {
  const el = document.getElementById(id);
  el.textContent = text;
  el.className   = "msg " + (type || "");
}

function result(id, data, isErr) {
  const el = document.getElementById(id);
  el.textContent = typeof data === "object"
                 ? JSON.stringify(data, null, 2)
                 : String(data);
  el.className = "result" + (isErr ? " err" : "");
}

function loading(id) {
  const el = document.getElementById(id);
  el.textContent = "Loading…";
  el.className   = "result loading";
}

function showTab(tab) {
  const isLogin = tab === "login";
  document.getElementById("tabLogin").classList.toggle("active",  isLogin);
  document.getElementById("tabRegister").classList.toggle("active", !isLogin);
  document.getElementById("panelLogin").classList.toggle("hidden",    !isLogin);
  document.getElementById("panelRegister").classList.toggle("hidden",  isLogin);
}

async function login() {
  const username = document.getElementById("loginUser").value.trim();
  const password = document.getElementById("loginPass").value;
  if (!username || !password) { msg("loginMsg","Fill in all fields","err"); return; }

  msg("loginMsg", "Signing in…", "");
  try {
    const data = await http("/api/auth/login", {
      method:  "POST",
      headers: { "Content-Type": "application/json" },
      body:    JSON.stringify({ username, password }),
    });
    saveSession(data.token, data.username);
    msg("loginMsg", "✅ Login successful!", "ok");
    setTimeout(showDashboard, 500);
  } catch (e) {
    msg("loginMsg", "❌ " + e.message, "err");
  }
}

async function register() {
  const username = document.getElementById("regUser").value.trim();
  const password = document.getElementById("regPass").value;
  if (!username || !password) { msg("registerMsg","Fill in all fields","err"); return; }

  msg("registerMsg", "Creating account…", "");
  try {
    const data = await http("/api/auth/register", {
      method:  "POST",
      headers: { "Content-Type": "application/json" },
      body:    JSON.stringify({ username, password }),
    });
    msg("registerMsg", "✅ " + data.message, "ok");
    setTimeout(() => showTab("login"), 1500);
  } catch (e) {
    msg("registerMsg", "❌ " + e.message, "err");
  }
}

function logout() {
  clearSession();
  showAuth();
  ["helloResult","dataResult","secureResult"].forEach(id => {
    const el = document.getElementById(id);
    if (el) { el.textContent = ""; el.className = "result"; }
  });
}

async function call(path, boxId) {
  loading(boxId);
  try {
    const data = await authHttp(path);
    result(boxId, data, false);
  } catch (e) {
    result(boxId, "Error: " + e.message, true);
  }
}

function showDashboard() {
  const authCard  = document.getElementById("authCard");
  const dashboard = document.getElementById("dashboard");
  const headerUser = document.getElementById("headerUser");
  const headerName = document.getElementById("headerName");
  const tokenStatus = document.getElementById("tokenStatus");

  if (authCard)   authCard.classList.add("hidden");
  if (dashboard)  dashboard.classList.remove("hidden");
  if (headerUser) headerUser.classList.remove("hidden");
  if (headerName) headerName.textContent = "👤 " + getUsername();
  if (tokenStatus) tokenStatus.textContent = "🔑 Authenticated as: " + getUsername();
}

function showAuth() {
  const authCard  = document.getElementById("authCard");
  const dashboard = document.getElementById("dashboard");
  const headerUser = document.getElementById("headerUser");
  const tokenStatus = document.getElementById("tokenStatus");

  if (authCard)    authCard.classList.remove("hidden");
  if (dashboard)   dashboard.classList.add("hidden");
  if (headerUser)  headerUser.classList.add("hidden");
  if (tokenStatus) tokenStatus.textContent = "";
}

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("loginPass")
          .addEventListener("keydown", e => e.key === "Enter" && login());
  document.getElementById("loginUser")
          .addEventListener("keydown", e => {
            if (e.key === "Enter")
              document.getElementById("loginPass").focus();
          });

  isLoggedIn() ? showDashboard() : showAuth();
});