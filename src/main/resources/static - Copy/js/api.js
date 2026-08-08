// API Calls
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
        if (error.message.includes('already exists')) {
            throw error;
        }
        console.error('Error checking duplicate:', error);
        return true;
    }
}

async function loadUserInfo() {
    try {
        // Backend does not expose /auth/me in this version; decode JWT locally instead
        const token = jwtToken;
        if (!token) throw new Error('No token');
        const parts = token.split('.');
        if (parts.length === 3) {
            try {
                const payload = parts[1];
                const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
                const data = JSON.parse(decodeURIComponent(escape(json)));
                const user = { email: data.sub || data.email || '', role: data.role || '' };
                document.getElementById('userEmail').textContent = user.email || '';
                document.getElementById('userAvatar').textContent = user.email ? user.email.charAt(0).toUpperCase() : 'U';
                return user;
            } catch (e) {
                console.warn('Failed to parse JWT, attempting /auth/me fallback', e);
            }
        }

        // Fallback: try /auth/me in case backend implements it
        const response = await apiCall('/auth/me');
        const result = await response.json();

        if (!result || !result.success) {
            throw new Error(result ? result.message : 'Failed to load user');
        }

        const user = result.data;
        document.getElementById('userEmail').textContent = user.email || '';
        document.getElementById('userAvatar').textContent = user.email ? user.email.charAt(0).toUpperCase() : 'U';
        return user;

    } catch (error) {
        console.error('Error loading user:', error);
        return null;
    }
}