// API Calls (static2) - Backend API wrapper
async function apiCall(endpoint, options = {}) {
    const defaultOptions = {
        headers: {
            'Authorization': `Bearer ${jwtToken}`,
            'Content-Type': 'application/json'
        }
    };

    const response = await fetch(`${API_URL}${endpoint}`, {
        ...defaultOptions,
        ...options,
        headers: { ...defaultOptions.headers, ...options.headers }
    });

    if (response.status === 401) {
        localStorage.removeItem('jwtToken');
        window.location.href = '/login.html';
        throw new Error('Unauthorized');
    }

    return response;
}

// Load all files from backend - returns flat list, no folder hierarchy
async function loadAllFiles() {
    try {
        const response = await apiCall('/drive');
        if (!response.ok) throw new Error('Failed to load files');
        return await response.json();
    } catch (error) {
        console.error('Error loading files:', error);
        throw error;
    }
}

// Get files for current folder view
async function getFilesForFolder(folderId) {
    try {
        const allFiles = await loadAllFiles();

        // Filter: if folderId provided, show only direct children; otherwise show root items
        if (folderId) {
            return allFiles.filter(f => f.parentId === folderId);
        } else {
            return allFiles.filter(f => !f.parentId || f.parentId === null);
        }
    } catch (error) {
        console.error('Error filtering files:', error);
        throw error;
    }
}

// Get breadcrumb path from file IDs to root
async function getBreadcrumbPath(folderId) {
    try {
        const allFiles = await loadAllFiles();
        const path = [];
        let currentId = folderId;
        const visited = new Set(); // Prevent infinite loops

        while (currentId && !visited.has(currentId)) {
            visited.add(currentId);
            const item = allFiles.find(f => f.id === currentId);
            if (!item) break;

            path.unshift({ id: item.id, name: item.name });
            currentId = item.parentId;
        }

        return path;
    } catch (error) {
        console.error('Error getting breadcrumb:', error);
        return [];
    }
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

async function loadUserInfo() {
    try {
        const user = getUserFromToken();
        if (!user || !user.email) {
            // Fallback: try to fetch from /auth/me if backend provides it
            try {
                const response = await apiCall('/auth/me');
                const result = await response.json();
                if (result && result.data) {
                    document.getElementById('userEmail').textContent = result.data.email || '';
                    document.getElementById('userAvatar').textContent = (result.data.email || 'U').charAt(0).toUpperCase();
                    return result.data;
                }
            } catch (e) {
                console.warn('No /auth/me endpoint; using token claims');
            }
            return null;
        }

        document.getElementById('userEmail').textContent = user.email || '';
        document.getElementById('userAvatar').textContent = user.email ? user.email.charAt(0).toUpperCase() : 'U';
        return user;

    } catch (error) {
        console.error('Error loading user:', error);
        return null;
    }
}
