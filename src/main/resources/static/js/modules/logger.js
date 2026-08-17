// logger.js – Logger Management (Admin only) with debug support

let currentLoggers = [];
let filteredLoggers = [];

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

async function loggerApiCall(endpoint, options = {}) {
    try {
        return await apiCall(endpoint, options);
    } catch (error) {
        if (error.message === 'Unauthorized') throw error;
        throw error;
    }
}

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
    loadUserInfoIntoUI();
    await loadLoggers();

    document.getElementById('createLoggerBtn').addEventListener('click', showCreateModal);
    document.getElementById('cancelModalBtn').addEventListener('click', hideCreateModal);
    document.getElementById('confirmCreateBtn').addEventListener('click', createLogger);
    document.getElementById('searchInput').addEventListener('input', filterLoggers);
});

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
            safeToast('Failed to load loggers: ' + e.message, 'error');
        }
    }
}

function renderLoggers(loggers) {
    const container = document.getElementById('loggerContainer');
    if (!loggers || loggers.length === 0) {
        container.innerHTML = `<div class="empty-state">No loggers configured.</div>`;
        return;
    }
    let html = `
        <table class="logger-table">
            <thead>
                <tr>
                    <th>Name</th>
                    <th>Info</th>
                    <th>Warn</th>
                    <th>Debug</th>
                    <th>Actions</th>
                </tr>
            </thead>
            <tbody>
    `;
    loggers.forEach(logger => {
        // Ensure boolean values (fallback to false if undefined)
        const infoOn = !!logger.infoEnabled;
        const warnOn = !!logger.warnEnabled;
        const debugOn = !!logger.debugEnabled;
        html += `
            <tr>
                <td><strong>${escapeHtml(logger.name)}</strong></td>
                <td><button class="toggle-btn ${infoOn ? 'on' : 'off'}" onclick="toggleLogger('${logger.id}','info',${!infoOn})">${infoOn ? 'ON' : 'OFF'}</button></td>
                <td><button class="toggle-btn ${warnOn ? 'on' : 'off'}" onclick="toggleLogger('${logger.id}','warn',${!warnOn})">${warnOn ? 'ON' : 'OFF'}</button></td>
                <td><button class="toggle-btn ${debugOn ? 'on' : 'off'}" onclick="toggleLogger('${logger.id}','debug',${!debugOn})">${debugOn ? 'ON' : 'OFF'}</button></td>
                <td><button class="delete-btn" onclick="deleteLogger('${logger.id}')" title="Delete">🗑️</button></td>
            </tr>
        `;
    });
    html += `</tbody></table>`;
    container.innerHTML = html;
}

function filterLoggers() {
    const query = document.getElementById('searchInput').value.toLowerCase().trim();
    filteredLoggers = query ? currentLoggers.filter(l => l.name.toLowerCase().includes(query)) : currentLoggers;
    renderLoggers(filteredLoggers);
}

function showCreateModal() {
    document.getElementById('loggerNameInput').value = '';
    document.getElementById('infoToggle').checked = true;
    document.getElementById('warnToggle').checked = true;
    document.getElementById('debugToggle').checked = false; // default off
    document.getElementById('createModal').classList.add('active');
}

function hideCreateModal() {
    document.getElementById('createModal').classList.remove('active');
}

async function createLogger() {
    const name = document.getElementById('loggerNameInput').value.trim();
    if (!name) {
        safeToast('Please enter a logger name', 'warning');
        return;
    }
    const infoEnabled = document.getElementById('infoToggle').checked;
    const warnEnabled = document.getElementById('warnToggle').checked;
    const debugEnabled = document.getElementById('debugToggle').checked;

    try {
        const createRes = await loggerApiCall(`/logger/create?name=${encodeURIComponent(name)}`, { method: 'POST' });
        if (!createRes.ok) {
            const err = await createRes.text();
            safeToast('Failed to create logger: ' + err, 'error');
            return;
        }
        const loggerId = await createRes.text();
        // Update all toggles in one call
        const updateRes = await loggerApiCall(
            `/logger/${loggerId}?info=${infoEnabled}&warn=${warnEnabled}&debug=${debugEnabled}`,
            { method: 'PUT' }
        );
        if (!updateRes.ok) {
            safeToast('Logger created but failed to set toggles', 'warning');
        }
        safeToast(`Logger "${name}" created`, 'success');
        hideCreateModal();
        await loadLoggers();
    } catch (e) {
        if (e.message !== 'Unauthorized') {
            safeToast('Error: ' + e.message, 'error');
        }
    }
}

async function toggleLogger(id, type, newValue) {
    const logger = currentLoggers.find(l => l.id === id);
    if (!logger) return;
    // Build new values preserving others
    const info = (type === 'info') ? newValue : logger.infoEnabled;
    const warn = (type === 'warn') ? newValue : logger.warnEnabled;
    const debug = (type === 'debug') ? newValue : logger.debugEnabled;
    try {
        const res = await loggerApiCall(`/logger/${id}?info=${info}&warn=${warn}&debug=${debug}`, { method: 'PUT' });
        if (res.ok) {
            safeToast(`Updated ${type} to ${newValue ? 'ON' : 'OFF'}`, 'info');
            await loadLoggers();
        } else {
            safeToast('Update failed', 'error');
        }
    } catch (e) {
        if (e.message !== 'Unauthorized') {
            safeToast('Error: ' + e.message, 'error');
        }
    }
}

async function deleteLogger(id) {
    const logger = currentLoggers.find(l => l.id === id);
    if (!logger) return;
    const confirmed = await showConfirm(`Delete logger "${logger.name}"?`, 'Delete Logger', 'Delete', 'Cancel', 'danger');
    if (!confirmed) return;
    try {
        const res = await loggerApiCall(`/logger/${id}`, { method: 'DELETE' });
        if (res.ok) {
            safeToast(`Logger "${logger.name}" deleted`, 'success');
            await loadLoggers();
        } else {
            safeToast('Delete failed', 'error');
        }
    } catch (e) {
        if (e.message !== 'Unauthorized') {
            safeToast('Error: ' + e.message, 'error');
        }
    }
}