// API Calls (static2) - Backend API wrapper
async function apiCall(endpoint, options = {}) {

    const response = await fetch(`${API_URL}${endpoint}`, {
        ...options,
        headers: {
            'Authorization': `Bearer ${localStorage.getItem('jwtToken')}`,
            'Content-Type': 'application/json',
            ...(options.headers || {})
        }
    });

    if (response.status === 401) {

        localStorage.removeItem('jwtToken');

        alert('Session expired. Please login again.');

        window.location.href = '/login.html';

        throw new Error('Unauthorized');
    }

    return response;
}
// Load all files from backend - returns flat list, no folder hierarchy
async function loadAllFiles() {
    try {
        const response = await apiCall('/drive');

        console.log('Status:', response.status);
        console.log('URL:', response.url);
        console.log('Content-Type:', response.headers.get('content-type'));

        const text = await response.text();
        console.log('Response Body:', text);

        return JSON.parse(text);

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

// Google sign-in: send id_token to backend to receive JWT
async function googleSignIn(idToken) {
    const res = await fetch(`${API_URL}/auth/google`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ idToken })
    });

    if (!res.ok) throw new Error('Google sign-in failed');
    const json = await res.json();
    if (json && json.success) {
        localStorage.setItem('jwtToken', json.data);
        return json.data;
    }
    throw new Error(json.message || 'Sign-in error');
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

        document.getElementById('userEmail').textContent = user.name || user.email || '';
        const avatarEl = document.getElementById('userAvatar');
        if (user.photoUrl) {
            avatarEl.innerHTML = `<img src="${user.photoUrl}" alt="avatar" style="width:100%;height:100%;border-radius:50%;object-fit:cover;">`;
        } else {
            avatarEl.textContent = user.name ? user.name.charAt(0).toUpperCase() : (user.email ? user.email.charAt(0).toUpperCase() : 'U');
        }
        return user;

    } catch (error) {
        console.error('Error loading user:', error);
        return null;
    }
}
