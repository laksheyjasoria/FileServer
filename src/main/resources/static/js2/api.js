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

        if (!response.ok) throw new Error('Failed to load files');
        const files = await response.json();
        return files.map(file => ({
            ...file,
            fileSize: file.size || 0,
            fileType: file.contentType || '',
            driveType: file.driveType || (file.fileId ? 'FILE' : 'FOLDER'),
            accessType: file.accessType || 'PUBLIC'
        }));

    } catch (error) {
        console.error('Error loading files:', error);
        throw error;
    }
}

// Get files for current folder view
async function getFilesForFolder(folderId) {
    try {
        const endpoint = folderId ? `/drive/${folderId}/contents` : '/drive/root';
        console.log('🔍 Fetching:', endpoint);  // 👈 log the URL

        const response = await apiCall(endpoint);
        console.log('📦 Response status:', response.status);

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const items = await response.json();
        console.log('📄 Raw API response:', items);  // 👈 see exactly what the backend returns
        console.log('📊 Number of items:', items.length);

        // Map to frontend format
        const mapped = items.map(item => ({
            ...item,
            fileSize: item.size || item.fileSize || 0,
            fileType: item.contentType || item.fileType || '',
            driveType: item.driveType || (item.fileId ? 'FILE' : 'FOLDER'),
            accessType: item.accessType || 'PUBLIC',
            parentId: item.parentId || null,
			childrenCount: item.childrenCount || 0,
			hasChildren: item.childrenCount > 0
        }));
        console.log('✅ Mapped with 	', mapped);
        return mapped;

    } catch (error) {
        console.error('❌ Error in getFilesForFolder:', error);
        throw error;
    }
}

// Get breadcrumb path from file IDs to root
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
        return true; // fallback: allow if we can't verify
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
