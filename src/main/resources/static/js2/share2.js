// share2.js – logic for shared file/folder page with navigation

// Helper to safely escape strings for HTML onclick attributes
function escapeJSString(str) {
    return (str || '').replace(/'/g, "\\'");
}

const urlParams = new URLSearchParams(window.location.search);
const token = urlParams.get('token');

let currentShare = null;
let currentPassword = null;
let isExpired = false;
let selectedSharedItems = new Set();

// Navigation state
let sharedCurrentFolderId = null; 
let navHistory = []; // array of { id, name }

if (!token) {
    showError('Invalid share link. No token provided.');
} else {
    loadShareInfo();
}

async function loadShareInfo() {
    try {
        const headers = {};
        const jwtToken = localStorage.getItem('jwtToken');
        if (jwtToken) {
            headers['Authorization'] = 'Bearer ' + jwtToken;
        }

        const response = await fetch(`${API_URL}/share/${token}`, { headers });
        
        if (response.status === 401 || response.status === 404 || response.status === 410) {
            isExpired = true;
            showExpired();
            return;
        }
        if (!response.ok) throw new Error('Failed to load share information');
        currentShare = await response.json();

        if (currentShare.expiresAt && new Date(currentShare.expiresAt) < new Date()) {
            isExpired = true;
            showExpired();
            return;
        }
        await displayShareInfo(currentShare);
    } catch (error) {
        console.error('Error loading share:', error);
        showError(error.message);
    }
}

function showExpired(message = "This share link has expired or is no longer available.") {
    document.getElementById('loading').style.display = 'none';
    const contentArea = document.getElementById('contentArea');
    contentArea.style.display = 'block';
    contentArea.innerHTML = `
        <div class="expired-message">
            <div class="expired-icon">⏰</div>
            <h3>Link Not Found</h3>
            <p>${escapeHtml(message)}</p>
            <p style="margin-top:16px; font-size:13px; color:#5f6368;">Please contact the file owner for a new link.</p>
        </div>
    `;
}

function showError(message) {
    document.getElementById('loading').style.display = 'none';
    const contentArea = document.getElementById('contentArea');
    contentArea.style.display = 'block';
    contentArea.innerHTML = `
        <div class="error-message" style="display:block; text-align:center;">
            <div style="font-size:48px; margin-bottom:16px;">🔗</div>
            <h3>Link Not Found</h3>
            <p>${escapeHtml(message)}</p>
        </div>
    `;
    // No toast for this specific error – it's a full-page error.
}

async function displayShareInfo(share) {
    document.getElementById('loading').style.display = 'none';
    const contentArea = document.getElementById('contentArea');
    contentArea.style.display = 'block';

    const isExpiredStatus = share.expiresAt && new Date(share.expiresAt) < new Date();
    const shareInfoClass = isExpiredStatus ? 'share-info expired' : 'share-info';
    const expiresText = isExpiredStatus ? '⚠️ This link has expired' :
        (share.expiresAt ? `⏰ Expires on ${new Date(share.expiresAt).toLocaleDateString()}` : 'Never expires');

    let html = `
        <div class="file-info">
            <div class="file-icon">${getFileIcon(share.driveName)}</div>
            <div class="file-details">
                <div class="file-name">${escapeHtml(share.driveName)}</div>
                <div class="file-meta">Shared via ${share.shareType} link • ${share.viewCount} views</div>
            </div>
        </div>
        <div class="${shareInfoClass}">
            🔗 This link was created on ${new Date(share.createdAt).toLocaleDateString()}
            <br>${expiresText}
        </div>
    `;

    if (isExpiredStatus) {
        html += `
            <div class="expired-message" style="margin-top:20px;">
                <p style="color:#c5221f;">This link has expired. No further access is available.</p>
            </div>
        `;
        contentArea.innerHTML = html;
        return;
    }

    // For both FOLDER and MULTI, we display a folder-like view
    if (share.driveType === 'FOLDER' || share.driveType === 'MULTI') {
        sharedCurrentFolderId = null;
        navHistory = [];
        await loadCurrentFolder(); 
        return;
    }

    // --- FILE share logic ---
    const permission = share.permission; 
    const canView = permission === 'VIEW' || permission === 'VIEW_DOWNLOAD';
    const canDownload = permission === 'DOWNLOAD' || permission === 'VIEW_DOWNLOAD';

    if (share.shareType === 'PROTECTED') {
        html += `
            <div class="password-section">
                <label>This file is password protected</label>
                <input type="password" id="password" class="password-input" placeholder="Enter password">
                <div class="action-buttons" style="margin-top:16px;">
                    <button class="btn btn-primary" onclick="accessWithPassword()">Access File</button>
                </div>
            </div>
        `;
        contentArea.innerHTML = html;
    } else if (share.shareType === 'USER_ONLY') {
        const jwtToken = localStorage.getItem('jwtToken');
        if (jwtToken) {
            html += `
                <div class="login-section">
                    <p>✅ You are logged in. You can access this file.</p>
                    <div class="action-buttons">
                        ${canView ? `<button class="btn btn-primary" onclick="loadRootFileContent()">👁️ View File</button>` : ''}
                        ${canDownload ? `<button class="btn btn-secondary" onclick="downloadRootFile()">⬇️ Download</button>` : ''}
                    </div>
                </div>
            `;
            contentArea.innerHTML = html;
        } else {
            html += `
                <div class="login-section">
                    <p>🔐 This file is shared with registered users only.</p>
                    <p>Please login to access this file.</p>
                    <button class="btn btn-primary" onclick="redirectToLogin()">Login to Access</button>
                </div>
            `;
            contentArea.innerHTML = html;
        }
    } else {
        html += `
            <div class="action-buttons">
                ${canView ? `<button class="btn btn-primary" onclick="loadRootFileContent()">👁️ View File</button>` : ''}
                ${canDownload ? `<button class="btn btn-secondary" onclick="downloadRootFile()">⬇️ Download</button>` : ''}
            </div>
        `;
        contentArea.innerHTML = html;
    }
}

// ---------- FOLDER NAVIGATION ----------

async function loadCurrentFolder() {
    const contentArea = document.getElementById('contentArea');
    const folderId = sharedCurrentFolderId;
    const endpoint = folderId
        ? `/share/${token}/folder/${folderId}/contents`
        : `/share/${token}/contents`;

    const permission = currentShare ? currentShare.permission : null;
    const canDownload = permission === 'DOWNLOAD' || permission === 'VIEW_DOWNLOAD';

    contentArea.innerHTML = `
        <div class="folder-toolbar">
            <div>
                <span id="folderBreadcrumb"></span>
                <span style="margin-left:12px; font-size:13px; color:#5f6368;">Select items to download</span>
            </div>
            <div class="action-buttons">
                ${canDownload ? `<button class="btn btn-secondary" onclick="toggleSelectAllShared()">☑️ Select All</button>` : ''}
                ${canDownload ? `<button class="btn btn-primary" onclick="downloadSelectedShared()">⬇️ Download Selected</button>` : ''}
            </div>
        </div>
        <div id="folderGrid" class="folder-grid"></div>
    `;

    renderBreadcrumb();

    try {
        const headers = {};
        const jwtToken = localStorage.getItem('jwtToken');
        if (jwtToken) {
            headers['Authorization'] = 'Bearer ' + jwtToken;
        }

        const response = await fetch(`${API_URL}${endpoint}`, { headers });
        if (!response.ok) {
            throw new Error(`Failed to load folder contents (Status: ${response.status})`);
        }
        const items = await response.json();
        renderFolderGrid(items);
    } catch (error) {
        console.error('Error loading folder contents:', error);
        document.getElementById('folderGrid').innerHTML = `<p style="color:red; padding:20px;">❌ ${error.message}</p>`;
        // Toast for folder load error (not a "link not found" scenario)
        if (typeof showToast === 'function') {
            showToast('Failed to load folder contents: ' + error.message, 'error');
        }
    }
}

function renderBreadcrumb() {
    const container = document.getElementById('folderBreadcrumb');
    if (!sharedCurrentFolderId) {
        container.innerHTML = `<strong>${escapeHtml(currentShare.driveName)}</strong>`;
        return;
    }
    let html = `<span onclick="navigateToFolder(null)" style="cursor:pointer; color:#1a73e8;">${escapeHtml(currentShare.driveName)}</span>`;
    for (let i = 0; i < navHistory.length; i++) {
        const item = navHistory[i];
        html += ` / <span onclick="navigateToFolder('${escapeJSString(item.id)}')" style="cursor:pointer; color:#1a73e8;">${escapeHtml(item.name)}</span>`;
    }
    container.innerHTML = html;
}

async function navigateToFolder(folderId, folderName) {
    if (folderId === null) {
        sharedCurrentFolderId = null;
        navHistory = [];
    } else {
        if (navHistory.length === 0 || navHistory[navHistory.length - 1].id !== folderId) {
            navHistory.push({ id: folderId, name: folderName });
        }
        sharedCurrentFolderId = folderId;
    }
    await loadCurrentFolder(); 
}

function renderFolderGrid(items) {
    const grid = document.getElementById('folderGrid');
    if (!items || items.length === 0) {
        grid.innerHTML = '<p style="color:#5f6368; text-align:center; padding:20px;">This folder is empty.</p>';
        return;
    }

    let html = '';
    items.forEach(item => {
        const icon = item.driveType === 'FILE' ? getFileIcon(item.name) : '📁';
        const info = item.driveType === 'FILE' ? formatFileSize(item.size) : 'Folder';
        const isFolder = item.driveType === 'FOLDER';

        const eId = escapeJSString(item.id);
        const eName = escapeJSString(item.name);
        
        let ondblclick = '';
        if (isFolder) {
            ondblclick = `ondblclick="navigateToFolder('${eId}', '${eName}')"`;
        } else {
            ondblclick = `ondblclick="viewSharedFile('${eId}', '${eName}')"`;
        }
        
        html += `
            <div class="folder-item" data-id="${item.id}" ${ondblclick}>
                <input type="checkbox" class="checkbox" onchange="toggleSharedSelection('${eId}', this.checked)">
                <div class="icon">${icon}</div>
                <div class="name">${escapeHtml(item.name)}</div>
                <div class="info">${info}</div>
            </div>
        `;
    });
    grid.innerHTML = html;
}

function toggleSharedSelection(id, checked) {
    const itemDiv = document.querySelector(`#folderGrid .folder-item[data-id="${id}"]`);
    if (checked) {
        selectedSharedItems.add(id);
        if (itemDiv) itemDiv.classList.add('selected');
    } else {
        selectedSharedItems.delete(id);
        if (itemDiv) itemDiv.classList.remove('selected');
    }
}

function toggleSelectAllShared() {
    const allItems = document.querySelectorAll('#folderGrid .folder-item');
    if (allItems.length === 0) return;
    const allChecked = Array.from(allItems).every(item => {
        const cb = item.querySelector('.checkbox');
        return cb && cb.checked;
    });
    const newState = !allChecked;
    selectedSharedItems.clear();
    allItems.forEach(item => {
        const cb = item.querySelector('.checkbox');
        if (cb) {
            cb.checked = newState;
            const id = item.dataset.id;
            if (newState) {
                selectedSharedItems.add(id);
                item.classList.add('selected');
            } else {
                item.classList.remove('selected');
            }
        }
    });
}

async function downloadSelectedShared() {
    const ids = Array.from(selectedSharedItems);
    if (ids.length === 0) {
        if (typeof showToast === 'function') {
            showToast('Select at least one item to download.', 'warning');
        } else {
            alert('Select at least one item to download.');
        }
        return;
    }
    if (typeof showToast === 'function') {
        showToast('Preparing ZIP download...', 'info', 2000);
    }
    try {
        const response = await fetch(`${API_URL}/download/bulk/shared?token=${token}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(ids)
        });
        if (!response.ok) throw new Error('Download failed');
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'download.zip';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        if (typeof showToast === 'function') {
            showToast('Download completed successfully.', 'success');
        }
    } catch (error) {
        console.error('Error downloading items:', error);
        if (typeof showToast === 'function') {
            showToast('Download failed: ' + error.message, 'error');
        } else {
            alert('Download failed: ' + error.message);
        }
    }
}

// ---------- FILE VIEWER (Root & Specific Files) ----------

async function loadRootFileContent() {
    const viewerArea = document.getElementById('viewerArea');
    const contentArea = document.getElementById('contentArea');
    viewerArea.style.display = 'block';
    viewerArea.innerHTML = '<div class="loading"><div class="spinner"></div><div>Loading file...</div></div>';
    const buttons = document.querySelectorAll('.action-buttons .btn');
    buttons.forEach(btn => btn.disabled = true);
    try {
        let url = `${API_URL}/share/stream/${token}`;
        if (currentPassword) url += `?password=${encodeURIComponent(currentPassword)}`;

        const headers = {};
        const jwtToken = localStorage.getItem('jwtToken');
        if (jwtToken) headers['Authorization'] = 'Bearer ' + jwtToken;

        const response = await fetch(url, { headers });
        if (response.status === 403) throw new Error('Access denied. Invalid password or permissions.');
        if (response.status === 410) throw new Error('This share link has expired');
        if (!response.ok) throw new Error('Failed to load file');
        const blob = await response.blob();
        const fileUrl = URL.createObjectURL(blob);
        const contentType = blob.type;
        const ext = currentShare.driveName.split('.').pop().toLowerCase();
        displayFileInViewer(fileUrl, contentType, currentShare.driveName, ext);
    } catch (error) {
        viewerArea.innerHTML = `<div class="error-message" style="display:block; text-align:center; margin:20px;">❌ ${error.message}</div>`;
        if (typeof showToast === 'function') {
            showToast('Failed to load file: ' + error.message, 'error');
        }
    } finally {
        buttons.forEach(btn => btn.disabled = false);
    }
}

async function downloadRootFile() {
    downloadFile();
}

async function viewSharedFile(fileId, fileName) {
    const viewerArea = document.getElementById('viewerArea');
    const contentArea = document.getElementById('contentArea');
    viewerArea.style.display = 'block';
    viewerArea.innerHTML = '<div class="loading"><div class="spinner"></div><div>Loading file...</div></div>';
    const buttons = document.querySelectorAll('.action-buttons .btn');
    buttons.forEach(btn => btn.disabled = true);
    try {
        let url = `${API_URL}/share/stream/${token}`;
        if (fileId) url += `?fileId=${encodeURIComponent(fileId)}`;
        if (currentPassword) url += `&password=${encodeURIComponent(currentPassword)}`;

        const headers = {};
        const jwtToken = localStorage.getItem('jwtToken');
        if (jwtToken) headers['Authorization'] = 'Bearer ' + jwtToken;

        const response = await fetch(url, { headers });
        if (response.status === 403) throw new Error('Access denied. Invalid password or permissions.');
        if (response.status === 410) throw new Error('This share link has expired');
        if (!response.ok) throw new Error('Failed to load file');
        const blob = await response.blob();
        const fileUrl = URL.createObjectURL(blob);
        const contentType = blob.type;
        const ext = fileName.split('.').pop().toLowerCase();
        displayFileInViewer(fileUrl, contentType, fileName, ext);
    } catch (error) {
        viewerArea.innerHTML = `<div class="error-message" style="display:block; text-align:center; margin:20px;">❌ ${error.message}</div>`;
        if (typeof showToast === 'function') {
            showToast('Failed to load file: ' + error.message, 'error');
        }
    } finally {
        buttons.forEach(btn => btn.disabled = false);
    }
}

function displayFileInViewer(fileUrl, contentType, filename, ext) {
    const viewerArea = document.getElementById('viewerArea');
    const permission = currentShare ? currentShare.permission : null;
    const canDownload = permission === 'DOWNLOAD' || permission === 'VIEW_DOWNLOAD';
    if (contentType.startsWith('image/')) {
        viewerArea.innerHTML = `<div class="viewer-container"><img src="${fileUrl}" alt="${filename}"></div>`;
        return;
    }
    if (contentType === 'application/pdf') {
        viewerArea.innerHTML = `<div class="viewer-container"><iframe src="${fileUrl}" title="${filename}"></iframe></div>`;
        return;
    }
    if (contentType.startsWith('video/')) {
        viewerArea.innerHTML = `<div class="viewer-container"><video controls autoplay><source src="${fileUrl}" type="${contentType}">Your browser does not support video playback.</video></div>`;
        return;
    }
    if (contentType.startsWith('audio/')) {
        viewerArea.innerHTML = `<div class="viewer-container"><audio controls autoplay><source src="${fileUrl}" type="${contentType}">Your browser does not support audio playback.</audio></div>`;
        return;
    }
    if (contentType.startsWith('text/') || ['txt', 'log', 'md', 'csv', 'json', 'xml', 'html', 'css', 'js'].includes(ext)) {
        fetch(fileUrl)
            .then(res => res.text())
            .then(text => {
                viewerArea.innerHTML = `<div class="text-viewer">${escapeHtml(text)}</div>`;
                URL.revokeObjectURL(fileUrl);
            })
            .catch(() => { viewerArea.innerHTML = `<div class="text-viewer">Failed to load text content.</div>`; });
        return;
    }
    viewerArea.innerHTML = `
        <div class="viewer-container" style="flex-direction:column; padding:40px;">
            <div style="font-size:64px; margin-bottom:20px;">📄</div>
            <div style="margin-bottom:20px;">Preview not available for this file type.</div>
            ${canDownload ? `<button class="btn btn-primary" onclick="downloadFile()">⬇️ Download File</button>` : ''}
        </div>
    `;
    URL.revokeObjectURL(fileUrl);
}

async function downloadFile() {
    let url = `${API_URL}/share/download/${token}`;
    if (currentPassword) url += `?password=${encodeURIComponent(currentPassword)}`;
    if (typeof showToast === 'function') {
        showToast('Preparing download...', 'info', 2000);
    }
    try {
        const response = await fetch(url);
        if (!response.ok) throw new Error('Download failed');
        const blob = await response.blob();
        const fileUrl = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = fileUrl;
        a.download = currentShare ? currentShare.driveName : 'download';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(fileUrl);
        if (typeof showToast === 'function') {
            showToast('Download completed successfully.', 'success');
        }
    } catch (error) {
        console.error('Download error:', error);
        if (typeof showToast === 'function') {
            showToast('Download failed: ' + error.message, 'error');
        } else {
            alert('Download failed: ' + error.message);
        }
    }
}

// ---------- HELPER FUNCTIONS ----------

async function accessWithPassword() {
    const password = document.getElementById('password').value;
    if (!password) {
        if (typeof showToast === 'function') {
            showToast('Please enter the password', 'warning');
        } else {
            alert('Please enter the password');
        }
        return;
    }
    const button = document.querySelector('#contentArea .btn-primary');
    button.disabled = true;
    button.textContent = 'Verifying...';
    currentPassword = password;
    try {
        const response = await fetch(`${API_URL}/share/${token}?password=${encodeURIComponent(password)}`);
        if (response.status === 403) throw new Error('Invalid password');
        if (!response.ok) throw new Error('Access denied');
        const contentArea = document.getElementById('contentArea');
        const permission = currentShare ? currentShare.permission : null;
        const canView = permission === 'VIEW' || permission === 'VIEW_DOWNLOAD';
        const canDownload = permission === 'DOWNLOAD' || permission === 'VIEW_DOWNLOAD';
        contentArea.innerHTML = `
            <div class="file-info">
                <div class="file-icon">${getFileIcon(currentShare.driveName)}</div>
                <div class="file-details">
                    <div class="file-name">${escapeHtml(currentShare.driveName)}</div>
                    <div class="file-meta">Shared via ${currentShare.shareType} link</div>
                </div>
            </div>
            <div class="action-buttons">
                ${canView ? `<button class="btn btn-primary" onclick="loadRootFileContent()">👁️ View File</button>` : ''}
                ${canDownload ? `<button class="btn btn-secondary" onclick="downloadRootFile()">⬇️ Download</button>` : ''}
            </div>
        `;
        if (typeof showToast === 'function') {
            showToast('Access granted. You can now view or download the file.', 'success');
        }
    } catch (error) {
        console.error('Password access error:', error);
        if (typeof showToast === 'function') {
            showToast('Invalid password. Please try again.', 'error');
        } else {
            alert('Invalid password. Please try again.');
        }
        button.disabled = false;
        button.textContent = 'Access File';
    }
}

function redirectToLogin() {
    localStorage.setItem('redirectAfterLogin', window.location.href);
    window.location.href = '/login.html';
}

// ---------- Full-page error (no toast) ----------
function showError(message) {
    document.getElementById('loading').style.display = 'none';
    const contentArea = document.getElementById('contentArea');
    contentArea.style.display = 'block';
    contentArea.innerHTML = `
        <div class="error-message" style="display:block; text-align:center;">
            <div style="font-size:48px; margin-bottom:16px;">🔗</div>
            <h3>Link Not Found</h3>
            <p>${escapeHtml(message)}</p>
        </div>
    `;
    // No toast for this error – full-page error is sufficient.
}

// ---------- UTILITIES ----------
// The following functions are assumed to be provided by utils.js or app.js:
// - getFileIcon, formatFileSize, escapeHtml, showDownloadOverlay, hideDownloadOverlay
// They are kept as references; no changes needed.

// Make sure setupSidebarNavigation is called if sidebar exists, but not required for share page.