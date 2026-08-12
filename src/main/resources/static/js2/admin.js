// admin.js - Master Drive Management (Admin JWT Only)

let adminCurrentFolderId = null;
let adminAllDrives = [];
let adminContextMenuItem = null;

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

// ---------- CUSTOM ADMIN FETCH (NO ALERT) ----------
async function adminFetch(endpoint, options = {}) {
    const token = localStorage.getItem('jwtToken');
    if (!token) {
        localStorage.removeItem('jwtToken');
        window.location.href = '/login.html';
        throw new Error('Unauthorized');
    }

    const url = `${API_URL}${endpoint}`;

    const response = await fetch(url, {
        ...options,
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json',
            ...(options.headers || {})
        }
    });

    if (response.status === 401) {
        localStorage.removeItem('jwtToken');
        window.location.href = '/login.html';
        throw new Error('Unauthorized');
    }

    return response;
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

    // Admin user with valid token → show main app
    document.getElementById('mainApp').style.display = 'block';
    loadUserInfo(user);
    await loadMasterDrives();
    attachListeners();
});

function attachListeners() {
    document.getElementById('newFolderBtn').addEventListener('click', showCreateFolderModal);
    document.getElementById('createFolderBtn').addEventListener('click', createFolder);
}

// ---------- LOAD USER INFO ----------
function loadUserInfo(user) {
    const emailEl = document.getElementById('userEmail');
    const avatarEl = document.getElementById('userAvatar');
    if (user && user.email) {
        if (emailEl) emailEl.textContent = user.email;
        if (avatarEl) {
            if (user.photoUrl) {
                avatarEl.style.backgroundImage = `url(${user.photoUrl})`;
                avatarEl.style.backgroundSize = 'cover';
                avatarEl.textContent = '';
            } else {
                avatarEl.textContent = user.email.charAt(0).toUpperCase();
            }
        }
    } else {
        if (avatarEl) {
            avatarEl.textContent = 'A';
            avatarEl.style.background = '#1a73e8';
        }
    }
}

// ---------- LOAD DRIVES ----------
async function loadMasterDrives() {
    const container = document.getElementById('fileContainer');
    showLoading(container, true);

    try {
        let url = '/admin/drives';
        if (adminCurrentFolderId) {
            url += `/${adminCurrentFolderId}/contents`;
        }

        const response = await adminFetch(url);
        const drives = await response.json();
        adminAllDrives = drives;

        if (drives && drives.length > 0) {
            renderDrives(drives);
        } else {
            showEmptyState(container, 'This folder is empty');
        }
        updateBreadcrumb();
    } catch (error) {
        if (error.message === 'Unauthorized') return;
        console.error('Error loading drives:', error);
        showError(container);
    }
}

// ---------- RENDER & NAVIGATION ----------
function renderDrives(drives) {
    const container = document.getElementById('fileContainer');
    let html = '<div class="file-grid">';

    drives.forEach(item => {
        const icon = item.driveType === 'FOLDER' ? '📁' : '📄';
        const info = item.childrenCount > 0 ? `${item.childrenCount} item${item.childrenCount > 1 ? 's' : ''}` : 'Empty';
        const isFolder = item.driveType === 'FOLDER';
        const eId = escapeJSString(item.id);
        const eName = escapeJSString(item.name);

        let ondblclick = '';
        if (isFolder) ondblclick = `ondblclick="navigateToFolder('${eId}')"`;

        html += `
            <div class="file-item" data-id="${item.id}" ${ondblclick}>
                <div class="file-icon">${icon}</div>
                <div class="file-name">${escapeHtml(item.name)}</div>
                <div class="file-info">${info}</div>
                <div class="file-menu" onclick="showContextMenu(event, '${eId}', '${eName}', '${item.driveType}')">
                    ⋮
                </div>
            </div>
        `;
    });

    html += '</div>';
    container.innerHTML = html;
}

function navigateToFolder(folderId) {
    adminCurrentFolderId = folderId;
    loadMasterDrives();
}

function navigateToParent() {
    adminCurrentFolderId = null;
    loadMasterDrives();
}

function updateBreadcrumb() {
    const breadcrumb = document.getElementById('breadcrumb');
    if (!adminCurrentFolderId) {
        breadcrumb.innerHTML = '<span class="breadcrumb-item" onclick="navigateToParent()">Master Drives</span>';
        return;
    }
    const drive = adminAllDrives.find(d => d.id === adminCurrentFolderId);
    const name = drive ? drive.name : 'Folder';
    breadcrumb.innerHTML = `
        <span class="breadcrumb-item" onclick="navigateToParent()">Master Drives</span>
        <span class="breadcrumb-separator">/</span>
        <span class="breadcrumb-item">${escapeHtml(name)}</span>
    `;
}

// ---------- CRUD OPERATIONS ----------
function showCreateFolderModal() {
    document.getElementById('folderName').value = '';
    showModal('folderModal');
}

async function createFolder() {
    const name = document.getElementById('folderName').value.trim();
    if (!name) return alert('Please enter a folder name');

    const createBtn = document.getElementById('createFolderBtn');
    createBtn.textContent = 'Creating...';
    createBtn.disabled = true;

    try {
        let endpoint = '/admin/drives';
        let body = { name, parentId: adminCurrentFolderId };
        if (adminCurrentFolderId) {
            endpoint = `/admin/drives/${adminCurrentFolderId}/nested`;
        }

        const response = await adminFetch(endpoint, {
            method: 'POST',
            body: JSON.stringify(body)
        });

        if (!response.ok) throw new Error('Failed to create folder');

        closeModal('folderModal');
        await loadMasterDrives();
        alert('Folder created successfully');
    } catch (error) {
        if (error.message === 'Unauthorized') return;
        alert('Failed: ' + error.message);
    } finally {
        createBtn.textContent = 'Create';
        createBtn.disabled = false;
    }
}

async function deleteDrive(id, name) {
    if (!confirm(`Delete "${name}"? This cannot be undone.`)) return;

    try {
        const response = await adminFetch(`/admin/drives/${id}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Delete failed');
        await loadMasterDrives();
        alert(`"${name}" deleted successfully`);
    } catch (error) {
        if (error.message === 'Unauthorized') return;
        alert('Failed to delete: ' + error.message);
    }
}

// ---------- CONTEXT MENU ----------
function showContextMenu(event, id, name, type) {
    event.stopPropagation();
    const existingMenu = document.querySelector('.dropdown-menu');
    if (existingMenu) existingMenu.remove();

    const menu = document.createElement('div');
    menu.className = 'dropdown-menu show';
    menu.style.position = 'absolute';
    menu.style.top = `${event.clientY}px`;
    menu.style.left = `${event.clientX}px`;

    const items = [
        { icon: '🗑️', label: 'Delete', action: () => deleteDrive(id, name) }
    ];

    items.forEach(item => {
        const div = document.createElement('div');
        div.className = 'dropdown-item';
        div.innerHTML = `${item.icon} ${item.label}`;
        div.onclick = () => { item.action(); menu.remove(); };
        menu.appendChild(div);
    });

    document.body.appendChild(menu);
    setTimeout(() => {
        document.addEventListener('click', () => menu.remove(), { once: true });
    }, 0);
}

// ---------- UTILS ----------
function escapeJSString(str) { return (str || '').replace(/'/g, "\\'"); }
function showModal(id) { document.getElementById(id).classList.add('active'); }
function closeModal(id) { document.getElementById(id).classList.remove('active'); }