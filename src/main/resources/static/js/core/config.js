// ============================================================
// config.js – Global configuration and state
// ============================================================

const API_URL = window.location.origin;
const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

// Global state
let jwtToken = localStorage.getItem("jwtToken");
let currentFolderId = null;
let currentView = "my-drive";
let allFiles = [];
let selectedItems = new Set();
let uploadQueue = [];
let activeUploads = new Map();
let contextMenuItem = null;
let pendingAction = null;
let pendingItems = [];

// ============================================================
// TOKEN HELPERS
// ============================================================

function decodeJwt(token) {
  if (!token) return null;
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const payload = parts[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(decodeURIComponent(escape(json)));
  } catch (e) {
    console.error("❌ Failed to decode token:", e);
    return null;
  }
}

function getUserFromToken() {
  const payload = decodeJwt(jwtToken);
  if (!payload) return null;
  return {
    email: payload.sub || payload.username || "",
    name: payload.name || payload.sub || "",
    role: payload.role || "",
    photoUrl: payload.photoUrl || null,
  };
}

function getCurrentUserRole() {
  const user = getUserFromToken();
  return user ? user.role : null;
}

function isTokenValid() {
  if (!jwtToken) return false;
  const payload = decodeJwt(jwtToken);
  if (!payload) return false;
  if (payload.exp) {
    const now = Math.floor(Date.now() / 1000);
    if (payload.exp < now) return false;
  }
  return true;
}

function refreshToken() {
  jwtToken = localStorage.getItem("jwtToken");
  return jwtToken;
}

window.getUserFromToken = getUserFromToken;
window.getCurrentUserRole = getCurrentUserRole;
window.isTokenValid = isTokenValid;
window.refreshToken = refreshToken;

// ============================================================
// USER DATA CACHING
// ============================================================

const USER_DATA_KEY = "userData";

window.setUserData = function (user) {
  localStorage.setItem(USER_DATA_KEY, JSON.stringify(user));
};

window.getUserData = function () {
  try {
    const raw = localStorage.getItem(USER_DATA_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
};

window.clearUserData = function () {
  localStorage.removeItem(USER_DATA_KEY);
  if (typeof window.clearAvatarCache === "function") {
    window.clearAvatarCache();
  }
};

// ============================================================
// AVATAR CACHING (shared across pages via sessionStorage)
// ============================================================

const AVATAR_CACHE_KEY = "avatarDataUrl";
const AVATAR_PHOTO_URL_KEY = "avatarPhotoUrl";

let avatarCachePromise = null;

window.getCachedAvatarDataUrl = function () {
  try {
    const data = sessionStorage.getItem(AVATAR_CACHE_KEY);
    console.log('🔍 getCachedAvatarDataUrl:', data ? 'found (length: ' + data.length + ')' : 'null');
    return data || null;
  } catch {
    return null;
  }
};

window.setCachedAvatarDataUrl = function (dataUrl, photoUrl) {
  try {
    if (dataUrl && photoUrl) {
      // Fix MIME type if it's octet-stream
      let fixedDataUrl = dataUrl;
      if (dataUrl.startsWith('data:application/octet-stream;base64,')) {
        fixedDataUrl = dataUrl.replace('data:application/octet-stream;base64,', 'data:image/jpeg;base64,');
        console.log('🔄 Fixed MIME type from octet-stream to image/jpeg');
      }
      sessionStorage.setItem(AVATAR_CACHE_KEY, fixedDataUrl);
      sessionStorage.setItem(AVATAR_PHOTO_URL_KEY, photoUrl);
      console.log('✅ Avatar cached (length:', fixedDataUrl.length, ')');
    } else {
      sessionStorage.removeItem(AVATAR_CACHE_KEY);
      sessionStorage.removeItem(AVATAR_PHOTO_URL_KEY);
      console.log('🗑️ Avatar cache cleared');
    }
  } catch (e) {
    console.warn('Failed to store avatar cache:', e);
  }
};

window.clearAvatarCache = function () {
  window.setCachedAvatarDataUrl(null, null);
  avatarCachePromise = null;
  console.log('🗑️ Avatar cache cleared');
};

window.fetchAndCacheAvatar = function () {
  if (avatarCachePromise) return avatarCachePromise;

  const user = window.getUserData() || window.getUserFromToken();
  if (!user || !user.photoUrl) {
    window.setCachedAvatarDataUrl(null, null);
    return Promise.resolve(null);
  }

  const cached = window.getCachedAvatarDataUrl();
  if (cached) {
    return Promise.resolve(cached);
  }

  const token = localStorage.getItem("jwtToken");
  if (!token) return Promise.resolve(null);

  avatarCachePromise = (async function () {
    try {
      console.log("📡 Fetching avatar...");
      const signedRes = await fetch(`/api/files/${user.photoUrl}/signed/session`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!signedRes.ok) throw new Error(`HTTP ${signedRes.status}`);
      const { url: signedUrl } = await signedRes.json();

      const imgRes = await fetch(signedUrl);
      if (!imgRes.ok) throw new Error(`Image HTTP ${imgRes.status}`);
      const blob = await imgRes.blob();
      if (blob.size === 0) throw new Error("Empty image");

      const dataUrl = await new Promise((resolve) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.readAsDataURL(blob);
      });

      window.setCachedAvatarDataUrl(dataUrl, user.photoUrl);
      console.log("✅ Avatar cached");
      return dataUrl;
    } catch (e) {
      console.warn("Avatar fetch failed:", e);
      window.setCachedAvatarDataUrl(null, null);
      return null;
    } finally {
      avatarCachePromise = null;
    }
  })();

  return avatarCachePromise;
};

// ============================================================
// CONSOLE HELPERS
// ============================================================

window.printUserData = function () {
  const user = window.getUserData() || window.getUserFromToken();
  console.log("📦 Cached user data:", user);
  const avatar = window.getCachedAvatarDataUrl();
  console.log("🖼️ Cached avatar:", avatar ? "yes" : "no");
  return user;
};

console.log("✅ config.js loaded");