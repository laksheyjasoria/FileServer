// API Configuration (static2 - backend-compatible adjustments)
const API_URL = window.location.origin;
const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB chunks

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

// Helper: refresh jwtToken from localStorage (useful after login)
function refreshToken() {
    jwtToken = localStorage.getItem('jwtToken');
    return jwtToken;
}

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
    // Use the global jwtToken variable
    const payload = decodeJwt(jwtToken);
    if (!payload) return null;
    return {
        email: payload.sub || payload.username || '',
        role: payload.role || ''
    };
}