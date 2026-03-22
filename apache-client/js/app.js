/**
 * app.js — Async JavaScript client for the Secure App.
 *
 * All communication with the backend services is done asynchronously
 * using the Fetch API over HTTPS/TLS.
 *
 * Security practices:
 *  - JWT token stored in sessionStorage (cleared on tab close)
 *  - Authorization: Bearer header on every protected request
 *  - No sensitive data logged to console in production
 *  - All requests use HTTPS (enforced by Apache + TLS)
 */

"use strict";

// ─── Token helpers ────────────────────────────────────────────────────────────

function saveSession(token, username) {
  sessionStorage.setItem(CONFIG.TOKEN_KEY, token);
  sessionStorage.setItem(CONFIG.USER_KEY, username);
}

function clearSession() {
  sessionStorage.removeItem(CONFIG.TOKEN_KEY);
  sessionStorage.removeItem(CONFIG.USER_KEY);
}

function getToken()    { return sessionStorage.getItem(CONFIG.TOKEN_KEY); }
function getUsername() { return sessionStorage.getItem(CONFIG.USER_KEY); }
function isLoggedIn()  { return !!getToken(); }

// ─── HTTP helpers ─────────────────────────────────────────────────────────────

/**
 * Wrapper around fetch that adds a timeout and returns parsed JSON.
 * Throws on network error or non-2xx status.
 */
async function apiFetch(url, options = {}) {
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), CONFIG.TIMEOUT_MS);

  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    clearTimeout(id);

    const json = await response.json();
    if (!response.ok) {
      throw new Error(json.error || json.message || `HTTP ${response.status}`);
    }
    return json;
  } catch (err) {
    clearTimeout(id);
    if (err.name === "AbortError") throw new Error("Request timed out");
    throw err;
  }
}

/**
 * Authenticated fetch — adds Authorization: Bearer <token>.
 */
async function authFetch(url, options = {}) {
  const token = getToken();
  if (!token) throw new Error("Not authenticated");
  return apiFetch(url, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`,
      ...(options.headers || {}),
    },
  });
}

// ─── UI helpers ───────────────────────────────────────────────────────────────

function showMessage(elementId, text, type = "success") {
  const el = document.getElementById(elementId);
  if (!el) return;
  el.textContent = text;
  el.className = `message ${type}`;
}

function setResult(elementId, data, type = "success") {
  const el = document.getElementById(elementId);
  if (!el) return;
  el.textContent = typeof data === "object"
    ? JSON.stringify(data, null, 2)
    : data;
  el.className = `result-box ${type}`;
}

function setLoading(elementId, text = "Loading...") {
  const el = document.getElementById(elementId);
  if (!el) return;
  el.textContent = text;
  el.className = "result-box loading";
}

function updateStatusBar() {
  const tokenEl = document.getElementById("tokenStatus");
  if (!tokenEl) return;
  if (isLoggedIn()) {
    const user = getUsername();
    tokenEl.textContent = `🔑 Authenticated as: ${user} | Token: active`;
  } else {
    tokenEl.textContent = "";
  }
}

// ─── Auth state management ────────────────────────────────────────────────────

function showDashboard() {
  document.getElementById("authSection").classList.add("hidden");
  document.getElementById("dashboardSection").classList.remove("hidden");
  document.getElementById("userInfo").classList.remove("hidden");
  document.getElementById("usernameDisplay").textContent = `👤 ${getUsername()}`;
  updateStatusBar();
}

function showAuthSection() {
  document.getElementById("authSection").classList.remove("hidden");
  document.getElementById("dashboardSection").classList.add("hidden");
  document.getElementById("userInfo").classList.add("hidden");
  updateStatusBar();
}

// ─── Tab switching ────────────────────────────────────────────────────────────

function switchTab(tab) {
  document.querySelectorAll(".tab-btn").forEach((btn, i) => {
    btn.classList.toggle("active", (i === 0 && tab === "login") || (i === 1 && tab === "register"));
  });
  document.getElementById("loginTab").classList.toggle("hidden", tab !== "login");
  document.getElementById("registerTab").classList.toggle("hidden", tab !== "register");
}

// ─── Login ────────────────────────────────────────────────────────────────────

async function login() {
  const username = document.getElementById("loginUser").value.trim();
  const password = document.getElementById("loginPass").value;

  if (!username || !password) {
    showMessage("loginMsg", "Please enter username and password.", "error");
    return;
  }

  const btn = document.getElementById("loginBtn");
  btn.disabled = true;
  btn.textContent = "Signing in…";
  showMessage("loginMsg", "", "");

  try {
    const data = await apiFetch(`${CONFIG.LOGIN_SERVICE_URL}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });

    saveSession(data.token, data.username);
    showMessage("loginMsg", "✅ Login successful!", "success");

    setTimeout(() => showDashboard(), 600);
  } catch (err) {
    showMessage("loginMsg", `❌ ${err.message}`, "error");
  } finally {
    btn.disabled = false;
    btn.textContent = "Login";
  }
}

// ─── Register ─────────────────────────────────────────────────────────────────

async function register() {
  const username = document.getElementById("regUser").value.trim();
  const password = document.getElementById("regPass").value;

  if (!username || !password) {
    showMessage("registerMsg", "Please fill in all fields.", "error");
    return;
  }
  if (password.length < 6) {
    showMessage("registerMsg", "Password must be at least 6 characters.", "error");
    return;
  }

  showMessage("registerMsg", "", "");

  try {
    const data = await apiFetch(`${CONFIG.LOGIN_SERVICE_URL}/api/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });

    showMessage("registerMsg", `✅ ${data.message} — You can now log in.`, "success");
    setTimeout(() => switchTab("login"), 1500);
  } catch (err) {
    showMessage("registerMsg", `❌ ${err.message}`, "error");
  }
}

// ─── Logout ───────────────────────────────────────────────────────────────────

function logout() {
  clearSession();
  showAuthSection();
  document.getElementById("loginUser").value = "";
  document.getElementById("loginPass").value = "";
  // Clear all result boxes
  ["helloResult","dataResult","secureResult"].forEach(id => {
    const el = document.getElementById(id);
    if (el) { el.textContent = ""; el.className = "result-box"; }
  });
}

// ─── Backend API calls ────────────────────────────────────────────────────────

async function callHello() {
  setLoading("helloResult", "Calling /api/hello on backend service…");
  try {
    const data = await authFetch(`${CONFIG.BACKEND_SERVICE_URL}/api/hello`);
    setResult("helloResult", data, "success");
  } catch (err) {
    setResult("helloResult", `Error: ${err.message}`, "error");
  }
}

async function callData() {
  setLoading("dataResult", "Fetching /api/data from backend service…");
  try {
    const data = await authFetch(`${CONFIG.BACKEND_SERVICE_URL}/api/data`);
    setResult("dataResult", data, "success");
  } catch (err) {
    setResult("dataResult", `Error: ${err.message}`, "error");
  }
}

async function callSecureInfo() {
  setLoading("secureResult", "Fetching /api/secure-info from backend service…");
  try {
    const data = await authFetch(`${CONFIG.BACKEND_SERVICE_URL}/api/secure-info`);
    setResult("secureResult", data, "success");
  } catch (err) {
    setResult("secureResult", `Error: ${err.message}`, "error");
  }
}

// ─── Bootstrap ────────────────────────────────────────────────────────────────

document.addEventListener("DOMContentLoaded", () => {
  // Wire logout button
  document.getElementById("logoutBtn").addEventListener("click", logout);

  // Keyboard: press Enter to log in
  document.getElementById("loginPass").addEventListener("keydown", (e) => {
    if (e.key === "Enter") login();
  });
  document.getElementById("loginUser").addEventListener("keydown", (e) => {
    if (e.key === "Enter") document.getElementById("loginPass").focus();
  });

  // Restore session if token still exists
  if (isLoggedIn()) {
    showDashboard();
  } else {
    showAuthSection();
  }

  updateStatusBar();
});
