// app.js - Main Application Initialization

document.addEventListener('DOMContentLoaded', async () => {
    if (!jwtToken) {
        window.location.href = '/login.html';
        return;
    }
    setupEventListeners();
    await loadUserInfo();
    setupSidebarNavigation();

    // Only load files if we are on the main drive page (index.html)
    if (document.getElementById('fileContainer') && !window.location.pathname.includes('profile.html')) {
        await loadFiles();
    }
    setupSearch();
});

function setupEventListeners() {
    const newFolderBtn = document.getElementById('newFolderBtn');
    const uploadBtn = document.getElementById('uploadBtn');
    const fileInput = document.getElementById('fileInput');
    const closeUploadQueueBtn = document.getElementById('closeUploadQueueBtn');
    const createFolderBtn = document.getElementById('createFolderBtn');
    const shareType = document.getElementById('shareType');

    if (newFolderBtn) newFolderBtn.addEventListener('click', () => showCreateFolderModal());
    if (uploadBtn) uploadBtn.addEventListener('click', () => fileInput.click());
    if (closeUploadQueueBtn) closeUploadQueueBtn.addEventListener('click', () => {
        document.getElementById('uploadQueue').style.display = 'none';
    });
    if (createFolderBtn) createFolderBtn.addEventListener('click', createFolder);
    if (fileInput) fileInput.addEventListener('change', (e) => addToUploadQueue(e.target.files));
    if (shareType) shareType.addEventListener('change', toggleShareOptions);
}

async function loadUserInfo() {
    try {
        const user = getUserFromToken();
        const userNameEl = document.getElementById('userName');
        const avatarEl = document.getElementById('userAvatar');
        if (user && user.email) {
            let displayName = user.name || user.email;
            if (userNameEl) userNameEl.textContent = displayName;
            if (avatarEl) {
                if (user.photoUrl) {
                    avatarEl.style.backgroundImage = `url(${user.photoUrl})`;
                    avatarEl.style.backgroundSize = 'cover';
                    avatarEl.textContent = '';
                } else {
                    avatarEl.style.backgroundImage = 'none';
                    avatarEl.textContent = displayName.charAt(0).toUpperCase();
                }
            }
        }
    } catch (error) {
        console.error('Error loading user info:', error);
    }
}

function setupSidebarNavigation() {
    const sidebarItems = document.querySelectorAll('.sidebar-item');
    
    // 1. Check current URL and apply 'active' class automatically
    const currentPath = window.location.pathname;
    let activeView = 'my-drive';
    if (currentPath.includes('profile.html')) activeView = 'profile';
    else if (currentPath.includes('shared')) activeView = 'shared';
    else if (currentPath.includes('recent')) activeView = 'recent';
    
    sidebarItems.forEach(item => {
        item.classList.toggle('active', item.dataset.view === activeView);
    });

    // 2. Add click listeners for navigation
    sidebarItems.forEach(item => {
        item.addEventListener('click', () => {
            if (item.id === 'signOutBtn') {
                localStorage.removeItem('jwtToken');
                window.location.href = '/login.html';
                return;
            }

            document.querySelectorAll('.sidebar-item').forEach(i => i.classList.remove('active'));
            item.classList.add('active');
            const view = item.dataset.view;

			if (view === 'my-drive') {
			     window.location.href = '/index.html';
			     return;
			 }
			 if (view === 'profile') {
			     window.location.href = '/profile.html';
			     return;
			 }
			 if (view === 'shared') {
			     window.location.href = '/shared.html';
			     return;
			 }
			 if (view === 'shared-by-me') {
			     window.location.href = '/shared-by-me.html';
			     return;
			 }

            currentFolderId = null;
            selectedItems.clear();
            loadFiles();
        });
    });
}

function setupSearch() {
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            const searchTerm = e.target.value.toLowerCase();
            const filtered = allFiles.filter(f => f.name.toLowerCase().includes(searchTerm));
            renderFiles(filtered);
        });
    }
}

// ---------------------- SHARED WITH ME LOGIC ----------------------

async function loadSharedWithMe() {
    const container = document.getElementById('fileContainer');
    showLoading(container, true);
    document.getElementById('breadcrumb').innerHTML = '<span class="breadcrumb-item">Shared with me</span>';

    try {
        const response = await apiCall('/share/shared-with-me');
        if (!response.ok) throw new Error('Failed to load shared items');
        const shares = await response.json();

        if (shares && shares.length > 0) {
            renderSharedFiles(shares);
        } else {
            showEmptyState(container, 'No items shared with you');
        }
    } catch (error) {
        console.error('Error loading shared items:', error);
        showError(container);
    }
}

function renderSharedFiles(shares) {
    const container = document.getElementById('fileContainer');
    let html = '<div class="file-grid">';

    shares.forEach(item => {
        const isFolder = item.driveType === 'FOLDER' || item.driveType === 'MULTI';
        const icon = isFolder ? '📁' : getFileIcon(item.driveName);
        
        let infoText = `Shared by ${escapeHtml(item.createdBy)}`;
        if (!isFolder && item.fileSize > 0) {
            infoText = `${formatFileSize(item.fileSize)} • ${infoText}`;
        } else if (isFolder) {
            infoText = `Folder • ${infoText}`;
        }

        html += `
            <div class="file-item" onclick="handleSharedItemClick(event, '${escapeJSString(item.token)}')" ondblclick="openSharedItem('${escapeJSString(item.token)}')">
                <div class="file-icon">${icon}</div>
                <div class="file-name">${escapeHtml(item.driveName)}</div>
                <div class="file-info">${infoText}</div>
                <div class="file-menu" onclick="showSharedContextMenu(event, '${escapeJSString(item.token)}', '${escapeJSString(item.driveName)}', '${item.driveType}')">
                    ⋮
                </div>
            </div>
        `;
    });

    html += '</div>';
    container.innerHTML = html;
}

function handleSharedItemClick(event, token) {
    if (event.target.classList.contains('file-menu')) return;
    // Optional: selection logic can be added here later
}

function openSharedItem(token) {
    window.location.href = `/share2.html?token=${encodeURIComponent(token)}`;
}