// api.js – API wrapper with deduplication and abort control

let _redirecting = false;

// Global deduplication maps
let activeControllers = new Map();   // Cancel pending requests
let requestTracker = new Map();      // Enforce 500ms minimum between identical endpoint calls

/**
 * apiCall – Make authenticated API request
 * @param {string} endpoint – API path (e.g., '/drive/root')
 * @param {object} options – fetch options (method, body, headers, etc.)
 * @param {boolean} options.skipDedupe – set true to bypass the 500ms minimum delay
 * @returns {Response|undefined} – Response object, or undefined if request was deduped/blocked
 */
async function apiCall(endpoint, options = {}) {
    // 1. Check for deduplication – unless skipDedupe is true
    const skipDedupe = options.skipDedupe || false;
    if (!skipDedupe) {
        const now = Date.now();
        const previous = requestTracker.get(endpoint);
        if (previous && (now - previous.timestamp < 500)) {
            console.warn(`⛔ BLOCKED: '${endpoint}' was called ${now - previous.timestamp}ms ago.`);
            // Silently block the request – returns undefined
            return;
        }
        requestTracker.set(endpoint, { timestamp: now });
    }

    // 2. Cancel any pending request to the exact same endpoint (AbortController)
    if (activeControllers.has(endpoint)) {
        activeControllers.get(endpoint).abort();
        activeControllers.delete(endpoint);
    }
    const controller = new AbortController();
    activeControllers.set(endpoint, controller);

    // 3. Execute the fetch
    const token = localStorage.getItem('jwtToken');
    try {
        const response = await fetch(`${API_URL}${endpoint}`, {
            ...options,
            headers: {
                'Authorization': `Bearer ${token}`,
                'Content-Type': 'application/json',
                ...(options.headers || {})
            },
            signal: controller.signal
        });

        activeControllers.delete(endpoint); // cleanup

        // 4. Handle 401 Unauthorized
        if (response.status === 401) {
            localStorage.removeItem('jwtToken');
            if (!_redirecting) {
                _redirecting = true;
                const msg = 'Session expired. Please login again.';
                if (typeof showToast === 'function') {
                    showToast(msg, 'error', 3000);
                } else {
                    alert(msg);
                }
                setTimeout(() => window.location.href = '/login.html', 1500);
            }
            throw new Error('Unauthorized');
        }
        return response;

    } catch (error) {
        activeControllers.delete(endpoint);
        if (error.name === 'AbortError') {
            console.warn(`Request to ${endpoint} was cancelled (duplicate overlap).`);
            return; // don't re‑throw
        }
        throw error;
    }
}

// ------------------------------------------------------------
// Public functions using apiCall
// ------------------------------------------------------------

async function getFilesForFolder(folderId, options = {}) {
    try {
        // Always use skipDedupe: true for file listings to ensure fresh data
        const endpoint = folderId ? `/drive/${folderId}/contents` : '/drive/root';
        const response = await apiCall(endpoint, { ...options, skipDedupe: true });
        if (!response) return []; // fallback – should not happen with skipDedupe
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const items = await response.json();
        return items.map(item => ({
            ...item,
            fileSize: item.size || item.fileSize || 0,
            fileType: item.contentType || item.fileType || '',
            driveType: item.driveType || (item.fileId ? 'FILE' : 'FOLDER'),
            accessType: item.accessType || 'PUBLIC',
            parentId: item.parentId || null,
            childrenCount: item.childrenCount || 0,
            hasChildren: item.childrenCount > 0
        }));
    } catch (error) {
        console.error('Error in getFilesForFolder:', error);
        throw error;
    }
}

async function getBreadcrumbPath(folderId) {
    if (!folderId) return [];
    const path = [];
    let currentId = folderId;
    const visited = new Set();
    while (currentId && !visited.has(currentId)) {
        visited.add(currentId);
        try {
            const response = await apiCall(`/drive/${currentId}`);
            if (!response.ok) break;
            const item = await response.json();
            path.unshift({ id: item.id, name: item.name });
            currentId = item.parentId || null;
        } catch (error) {
            console.error('Error fetching parent:', error);
            break;
        }
    }
    return path;
}

async function checkDuplicateName(name, parentId) {
    try {
        const items = await getFilesForFolder(parentId);
        const exists = items.some(item => item.name.toLowerCase() === name.toLowerCase());
        if (exists) {
            throw new Error(`An item with name "${name}" already exists in this location`);
        }
        return true;
    } catch (error) {
        if (error.message && error.message.includes('already exists')) {
            throw error;
        }
        console.error('Error checking duplicate:', error);
        return true;
    }
}

// api.js – add this function

async function getTrashItems(options = {}) {
    try {
        // Call the trash endpoint (adjust if your backend uses a different path)
        const response = await apiCall('/drive/trash', { ...options, skipDedupe: true });
        if (!response) return [];
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const items = await response.json();
        return items.map(item => ({
            ...item,
            fileSize: item.size || item.fileSize || 0,
            fileType: item.contentType || item.fileType || '',
            driveType: item.driveType || (item.fileId ? 'FILE' : 'FOLDER'),
            accessType: item.accessType || 'PUBLIC',
            parentId: item.parentId || null,
            childrenCount: item.childrenCount || 0,
            hasChildren: item.childrenCount > 0
        }));
    } catch (error) {
        console.error('Error fetching trash:', error);
        return [];
    }
}

// Add these to your api.js (if not already present)

async function getTrashItems(options = {}) {
    try {
        const response = await apiCall('/drive/trash', { ...options, skipDedupe: true });
        if (!response) return [];
        if (!response.ok) return [];
        const items = await response.json();
        return items.map(item => ({
            ...item,
            fileSize: item.size || 0,
            fileType: item.contentType || '',
            driveType: item.driveType || (item.fileId ? 'FILE' : 'FOLDER'),
            accessType: item.accessType || 'PUBLIC',
            parentId: item.parentId || null,
            childrenCount: item.childrenCount || 0,
            hasChildren: item.childrenCount > 0,
            deletedAt: item.deletedAt || null
        }));
    } catch (error) {
        console.error('Error fetching trash:', error);
        return [];
    }
}

async function restoreFromTrash(itemId) {
    const response = await apiCall(`/drive/trash/${itemId}/restore`, {
        method: 'POST'
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || 'Restore failed');
    }
    return true; // success, no body expected
}

async function permanentDeleteItem(itemId) {
    const response = await apiCall(`/drive/trash/${itemId}`, {
        method: 'DELETE'
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || 'Delete failed');
    }
    return true;
}

async function emptyTrashApi() {
    const response = await apiCall('/drive/trash/empty', {
        method: 'DELETE'
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || 'Empty trash failed');
    }
    return true;
}

// Expose them
window.getTrashItems = getTrashItems;
window.restoreFromTrash = restoreFromTrash;
window.permanentDeleteItem = permanentDeleteItem;
window.emptyTrashApi = emptyTrashApi;

// ------------------------------------------------------------
// Global exports (for use in inline event handlers)
// ------------------------------------------------------------
window.apiCall = apiCall;
window.getFilesForFolder = getFilesForFolder;
window.getBreadcrumbPath = getBreadcrumbPath;
window.checkDuplicateName = checkDuplicateName;
window.getTrashItems = getTrashItems;