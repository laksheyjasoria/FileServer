// logger.js - Logger Management (Admin JWT only)

let currentLoggers = [];
let filteredLoggers = [];

document.addEventListener('DOMContentLoaded', async () => {
    const user = getUserFromToken();
    const isAdmin = user && user.role === 'ADMIN';

    if (!isAdmin || !isTokenValid()) {
        localStorage.removeItem('jwtToken');
        window.location.href = '/login.html';
        return;
    }

    document.getElementById('mainApp').style.display = 'block';
    loadUserInfo(user);
    await loadLoggers();

    // Event listeners
    document.getElementById('createLoggerBtn').addEventListener('click', showCreateModal);
    document.getElementById('cancelModalBtn').addEventListener('click', hideCreateModal);
    document.getElementById('confirmCreateBtn').addEventListener('click', createLogger);
    document.getElementById('searchInput').addEventListener('input', filterLoggers);
});

// ---------- USER INFO ----------
function loadUserInfo(user) {
    const emailEl = document.getElementById('userEmail');
    const avatarEl = document.getElementById('userAvatar');
    if (user && user.email) {
        emailEl.textContent = user.email;
        if (user.photoUrl) {
            avatarEl.style.backgroundImage = `url(${user.photoUrl})`;
            avatarEl.style.backgroundSize = 'cover';
            avatarEl.textContent = '';
        } else {
            avatarEl.textContent = user.email.charAt(0).toUpperCase();
        }
    } else {
        avatarEl.textContent = 'A';
        avatarEl.style.background = '#1a73e8';
    }
}

// ---------- TOKEN HELPERS ----------
function getUserFromToken() {
    const token = localStorage.getItem('jwtToken');
    if (!token) return null;
    const payload = decodeJwt(token);
    if (!payload) return null;
    return {
        email: payload.sub || payload.username || '',
        role: payload.role || ''
    };
}

function isTokenValid() {
    const token = localStorage.getItem('jwtToken');
    if (!token) return false;
    const payload = decodeJwt(token);
    if (!payload) return false;
    if (payload.exp) {
        const now = Math.floor(Date.now() / 1000);
        if (payload.exp < now) return false;
    }
    return true;
}

function decodeJwt(token) {
    try {
        const parts = token.split('.');
        if (parts.length !== 3) return null;
        const payload = parts[1];
        const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
        return JSON.parse(decodeURIComponent(escape(json)));
    } catch (e) {
        return null;
    }
}

// ---------- API CALLS ----------
async function loggerApiCall(endpoint, options = {}) {
    try {
        return await apiCall(endpoint, options);
    } catch (error) {
        if (error.message === 'Unauthorized') throw error;
        throw error;
    }
}

// ---------- LOAD LOGGERS ----------
async function loadLoggers() {
    const container = document.getElementById('loggerContainer');
    container.innerHTML = '<div class="loading">Loading loggers...</div>';

    try {
        const res = await loggerApiCall('/logger/list');
        const data = await res.json();
        currentLoggers = data;
        filteredLoggers = data;
        renderLoggers(data);
    } catch (e) {
        if (e.message !== 'Unauthorized') {
            container.innerHTML = '<div class="empty-state">Failed to load loggers.</div>';
        }
    }
}

// ---------- RENDER ----------
function renderLoggers(loggers) {
    const container = document.getElementById('loggerContainer');
    if (!loggers || loggers.length === 0) {
        container.innerHTML = `
            <div class="empty-state">No loggers configured.</div>
        `;
        return;
    }

    let html = `
        <table class="logger-table">
            <thead>
                <tr>
                    <th>Name</th>
                    <th>Info</th>
                    <th>Warn</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
    `;

    loggers.forEach(logger => {
        const infoOn = logger.infoEnabled || false;
        const warnOn = logger.warnEnabled || false;
        html += `
            <tr>
                <td><strong>${escapeHtml(logger.name)}</strong></td>
                <td>
                    <button class="toggle-btn ${infoOn ? 'on' : 'off'}" onclick="toggleLogger('${logger.id}', 'info', ${!infoOn})">
                        ${infoOn ? 'ON' : 'OFF'}
                    </button>
                </td>
                <td>
                    <button class="toggle-btn ${warnOn ? 'on' : 'off'}" onclick="toggleLogger('${logger.id}', 'warn', ${!warnOn})">
                        ${warnOn ? 'ON' : 'OFF'}
                    </button>
                </td>
                <td>
                    <button class="delete-btn" onclick="deleteLogger('${logger.id}')" title="Delete">🗑️</button>
                </td>
            </tr>
        `;
    });

    html += `</tbody></table>`;
    container.innerHTML = html;
}

// ---------- FILTER (search) ----------
function filterLoggers() {
    const query = document.getElementById('searchInput').value.toLowerCase().trim();
    if (!query) {
        filteredLoggers = currentLoggers;
    } else {
        filteredLoggers = currentLoggers.filter(l => l.name.toLowerCase().includes(query));
    }
    renderLoggers(filteredLoggers);
}

// ---------- MODAL ----------
function showCreateModal() {
    document.getElementById('loggerNameInput').value = '';
    document.getElementById('infoToggle').checked = true;
    document.getElementById('warnToggle').checked = true;
    document.getElementById('createModal').classList.add('active');
}

function hideCreateModal() {
    document.getElementById('createModal').classList.remove('active');
}

// ---------- CREATE ----------
async function createLogger() {
    const nameInput = document.getElementById('loggerNameInput');
    const name = nameInput.value.trim();
    if (!name) {
        alert('Please enter a logger name');
        return;
    }

    const infoEnabled = document.getElementById('infoToggle').checked;
    const warnEnabled = document.getElementById('warnToggle').checked;

    // The backend create endpoint only takes name; we need separate update to set toggles.
    // We'll create first, then update with the desired settings.
    try {
        const createRes = await loggerApiCall(`/logger/create?name=${encodeURIComponent(name)}`, {
            method: 'POST'
        });
        if (!createRes.ok) {
            const errorText = await createRes.text();
            alert('Failed to create logger: ' + errorText);
            return;
        }
        const loggerId = await createRes.text(); // returns the ID

        // Now update toggles
        const updateRes = await loggerApiCall(
            `/logger/${loggerId}?info=${infoEnabled}&warn=${warnEnabled}`,
            { method: 'PUT' }
        );
        if (!updateRes.ok) {
            alert('Logger created but failed to set initial toggles');
        }

        showToast(`Logger "${name}" created`);
        hideCreateModal();
        await loadLoggers();
    } catch (e) {
        if (e.message !== 'Unauthorized') alert('Error: ' + e.message);
    }
}

// ---------- TOGGLE ----------
async function toggleLogger(id, type, newValue) {
    const logger = currentLoggers.find(l => l.id === id);
    if (!logger) return;

    const info = (type === 'info') ? newValue : logger.infoEnabled;
    const warn = (type === 'warn') ? newValue : logger.warnEnabled;

    try {
        const res = await loggerApiCall(
            `/logger/${id}?info=${info}&warn=${warn}`,
            { method: 'PUT' }
        );
        if (res.ok) {
            showToast(`Updated ${type} to ${newValue ? 'ON' : 'OFF'}`);
            await loadLoggers();
        } else {
            alert('Update failed');
        }
    } catch (e) {
        if (e.message !== 'Unauthorized') alert('Error: ' + e.message);
    }
}

// ---------- DELETE ----------
async function deleteLogger(id) {
    const logger = currentLoggers.find(l => l.id === id);
    if (!logger) return;
    if (!confirm(`Delete logger "${logger.name}"?`)) return;

    try {
        const res = await loggerApiCall(`/logger/${id}`, {
            method: 'DELETE'
        });
        if (res.ok) {
            showToast(`Logger "${logger.name}" deleted`);
            await loadLoggers();
        } else {
            alert('Delete failed');
        }
    } catch (e) {
        if (e.message !== 'Unauthorized') alert('Error: ' + e.message);
    }
}

// ---------- UTILITIES ----------
function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

function showToast(msg) {
    const toast = document.getElementById('toast');
    toast.textContent = msg;
    toast.style.display = 'block';
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => {
        toast.style.display = 'none';
    }, 3000);
}