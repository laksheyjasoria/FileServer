// API Calls (static2)
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

async function checkDuplicateName(name, parentId) {
    try {
        let url = parentId
            ? `/drive/${parentId}/contents`
            : `/drive`;

        const response = await apiCall(url);
        const items = await response.json();

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
