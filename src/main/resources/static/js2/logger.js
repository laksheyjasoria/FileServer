// logger.js - Logger Management (Admin JWT only)

let currentLoggers = [];
let filteredLoggers = [];

// ---------- TOKEN HELPERS ----------
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

// ---------- PAGE INIT ----------
document.addEventListener('DOMContentLoaded', async () => {
    const token = localStorage.getItem('jwtToken');
    if (!token) {
        window.location.href = '/login.html';
        return;
    }

    if (!isTokenValid()) {
        localStorage.removeItem('jwtToken');
        window.location.href = '/login.html';
        return;
    }

    const user = getUserFromToken();
    const isAdmin = user && user.role === 'ADMIN';
    if (!isAdmin) {
        window.location.href = '/index.html?error=unauthorized';
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
            if (typeof showToast === 'function') {
                showToast('Failed to load loggers: ' + e.message, 'error');
            }
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
        if (typeof showToast === 'function') {
            showToast('Please enter a logger name', 'warning');
        } else {
            alert('Please enter a logger name');
        }
        return;
    }

    const infoEnabled = document.getElementById('infoToggle').checked;
    const warnEnabled = document.getElementById('warnToggle').checked;

    try {
        const createRes = await loggerApiCall(`/logger/create?name=${encodeURIComponent(name)}`, {
            method: 'POST'
        });
        if (!createRes.ok) {
            const errorText = await createRes.text();
            if (typeof showToast === 'function') {
                showToast('Failed to create logger: ' + errorText, 'error');
            } else {
                alert('Failed to create logger: ' + errorText);
            }
            return;
        }
        const loggerId = await createRes.text();

        const updateRes = await loggerApiCall(
            `/logger/${loggerId}?info=${infoEnabled}&warn=${warnEnabled}`,
            { method: 'PUT' }
        );
        if (!updateRes.ok) {
            if (typeof showToast === 'function') {
                showToast('Logger created but failed to set initial toggles', 'warning');
            } else {
                alert('Logger created but failed to set initial toggles');
            }
        }

        if (typeof showToast === 'function') {
            showToast(`Logger "${name}" created successfully`, 'success');
        }
        hideCreateModal();
        await loadLoggers();
    } catch (e) {
        if (e.message !== 'Unauthorized') {
            if (typeof showToast === 'function') {
                showToast('Error: ' + e.message, 'error');
            } else {
                alert('Error: ' + e.message);
            }
        }
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
            if (typeof showToast === 'function') {
                showToast(`Updated ${type} to ${newValue ? 'ON' : 'OFF'}`, 'info');
            }
            await loadLoggers();
        } else {
            if (typeof showToast === 'function') {
                showToast('Update failed', 'error');
            } else {
                alert('Update failed');
            }
        }
    } catch (e) {
        if (e.message !== 'Unauthorized') {
            if (typeof showToast === 'function') {
                showToast('Error: ' + e.message, 'error');
            } else {
                alert('Error: ' + e.message);
            }
        }
    }
}

// ---------- DELETE ----------
async function deleteLogger(id) {
    const logger = currentLoggers.find(l => l.id === id);
    if (!logger) return;
    // Native confirm (since no modal system)
    if (!confirm(`Delete logger "${logger.name}"?`)) return;

    try {
        const res = await loggerApiCall(`/logger/${id}`, {
            method: 'DELETE'
        });
        if (res.ok) {
            if (typeof showToast === 'function') {
                showToast(`Logger "${logger.name}" deleted`, 'success');
            }
            await loadLoggers();
        } else {
            if (typeof showToast === 'function') {
                showToast('Delete failed', 'error');
            } else {
                alert('Delete failed');
            }
        }
    } catch (e) {
        if (e.message !== 'Unauthorized') {
            if (typeof showToast === 'function') {
                showToast('Error: ' + e.message, 'error');
            } else {
                alert('Error: ' + e.message);
            }
        }
    }
}

// ---------- UTILITIES ----------
function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// Note: showToast is provided globally by toast.js
// We removed the local showToast definition