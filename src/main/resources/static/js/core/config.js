// config.js – Global configuration and state
const API_URL = window.location.origin;
const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

// Global state
let jwtToken = localStorage.getItem('jwtToken');
let currentFolderId = null;
let currentView = 'my-drive';
let allFiles = [];
let selectedItems = new Set();
let uploadQueue = [];
let activeUploads = new Map();
let contextMenuItem = null;
let pendingAction = null;
let pendingItems = [];

// Token helpers
function decodeJwt(token) {
    if (!token) return null;
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return null;
        const payload = parts[1];
        const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
        return JSON.parse(decodeURIComponent(escape(json)));
    } catch (e) {
        console.error('Failed to decode token', e);
        return null;
    }
}

function getUserFromToken() {
    const payload = decodeJwt(jwtToken);
    if (!payload) return null;
    return {
        email: payload.sub || payload.username || '',
        name: payload.name || payload.sub || '',
        role: payload.role || '',
        photoUrl: payload.photoUrl || null
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
    jwtToken = localStorage.getItem('jwtToken');
    return jwtToken;
}

// Expose globally
window.getUserFromToken = getUserFromToken;
window.getCurrentUserRole = getCurrentUserRole;
window.isTokenValid = isTokenValid;
window.refreshToken = refreshToken;