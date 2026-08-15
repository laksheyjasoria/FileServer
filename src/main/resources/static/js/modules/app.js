// app.js – Main file manager (uses globals from config.js)

// Globals are already defined in config.js:
// currentFolderId, allFiles, selectedItems, etc.

// ============================
// INIT
// ============================
document.addEventListener('DOMContentLoaded', async () => {
    if (!jwtToken) {
        window.location.href = '/login.html';
        return;
    }

    setupEventListeners();
    await loadUserInfo();
    await loadFiles();
    setupSidebarNavigation();
    setupSearch();
});

function setupEventListeners() {
    const newFolderBtn = document.getElementById('newFolderBtn');
    const uploadBtn = document.getElementById('uploadBtn');
    const fileInput = document.getElementById('fileInput');
    const createFolderBtn = document.getElementById('createFolderBtn');
    const shareType = document.getElementById('shareType');

    if (newFolderBtn) newFolderBtn.addEventListener('click', () => showCreateFolderModal());
    if (uploadBtn) uploadBtn.addEventListener('click', () => fileInput.click());
    if (fileInput) fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) {
            addToUploadQueue(e.target.files);
            e.target.value = '';
        }
    });
    if (createFolderBtn) createFolderBtn.addEventListener('click', createFolder);
    if (shareType) shareType.addEventListener('change', toggleShareOptions);
}

function setupSearch() {
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            const term = e.target.value.toLowerCase();
            const filtered = allFiles.filter(f => f.name.toLowerCase().includes(term));
            renderFiles(filtered);
        });
    }
}

// ============================
// LOAD FILES
// ============================
async function loadFiles() {
    const container = document.getElementById('fileContainer');
    showLoading(container, true);

    try {
        const files = await getFilesForFolder(currentFolderId);
        allFiles = files;
        renderFiles(files);
        updateBreadcrumb();
    } catch (error) {
        console.error('Error loading files:', error);
        showError(container);
    }
}

function renderFiles(files) {
    const container = document.getElementById('fileContainer');
    if (!files || files.length === 0) {
        showEmptyState(container);
        return;
    }

    const folders = files.filter(f => f.driveType === 'FOLDER' || f.driveType === 'ROOT');
    const fileItems = files.filter(f => f.driveType === 'FILE');

    let html = '<div class="file-grid">';

    folders.forEach(item => {
        const isSelected = selectedItems.has(item.id);
        const icon = '📁';
        const info = item.hasChildren ? 'Contains items' : 'Empty';
        const isProtected = item.accessType === 'PROTECTED';
        const lockIcon = isProtected ? '🔒 ' : '';
        const safeId = encodeURIComponent(item.id);
        const safeName = encodeURIComponent(item.name).replace(/'/g, '%27');
        const safeType = encodeURIComponent(item.driveType);
        const safeFileType = encodeURIComponent(item.fileType || '');
        const safeParentId = item.parentId ? encodeURIComponent(item.parentId) : '';

        html += `
            <div class="file-item ${isSelected ? 'selected' : ''}" 
                 data-id="${item.id}" 
                 data-parent-id="${item.parentId || ''}"
                 data-type="${item.driveType}" 
                 data-name="${escapeHtml(item.name)}"
                 ondblclick="openFolder(decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'), ${isProtected})"
                 onclick="handleItemClick(event, decodeURIComponent('${safeId}'), true)">
                <input type="checkbox" class="checkbox" ${isSelected ? 'checked' : ''} onchange="toggleSelectItem(event, decodeURIComponent('${safeId}'), this.checked)">
                <div class="file-icon">${icon}</div>
                <div class="file-name">${lockIcon}${escapeHtml(item.name)}</div>
                <div class="file-info">${info}</div>
                <div class="file-menu" onclick="showContextMenu(event, decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'), decodeURIComponent('${safeType}'), decodeURIComponent('${safeFileType}'), ${isProtected}, '${safeParentId}' ? decodeURIComponent('${safeParentId}') : null)">
                    ⋮
                </div>
            </div>
        `;
    });

    fileItems.forEach(item => {
        const isSelected = selectedItems.has(item.id);
        const icon = getFileIcon(item.name);
        const info = formatFileSize(item.fileSize);
        const isProtected = item.accessType === 'PROTECTED';
        const lockIcon = isProtected ? '🔒 ' : '';
        const safeId = encodeURIComponent(item.id);
        const safeName = encodeURIComponent(item.name).replace(/'/g, '%27');
        const safeType = encodeURIComponent(item.driveType);
        const safeFileType = encodeURIComponent(item.fileType || '');
        const safeParentId = item.parentId ? encodeURIComponent(item.parentId) : '';
        const isViewable = item.fileType && (item.fileType.startsWith('image/') || item.fileType === 'application/pdf' || item.fileType.startsWith('video/'));

        let doubleClickAction = isViewable ? `viewFile(decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'))` : `downloadFile(decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'))`;

        html += `
            <div class="file-item ${isSelected ? 'selected' : ''}" 
                 data-id="${item.id}" 
                 data-parent-id="${item.parentId || ''}"
                 data-type="${item.driveType}" 
                 data-name="${escapeHtml(item.name)}"
                 ondblclick="${doubleClickAction}"
                 onclick="handleItemClick(event, decodeURIComponent('${safeId}'), false)">
                <input type="checkbox" class="checkbox" ${isSelected ? 'checked' : ''} onchange="toggleSelectItem(event, decodeURIComponent('${safeId}'), this.checked)">
                <div class="file-icon">${icon}</div>
                <div class="file-name">${lockIcon}${escapeHtml(item.name)}</div>
                <div class="file-info">${info}</div>
                <div class="file-menu" onclick="showContextMenu(event, decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'), decodeURIComponent('${safeType}'), decodeURIComponent('${safeFileType}'), ${isProtected}, '${safeParentId}' ? decodeURIComponent('${safeParentId}') : null)">
                    ⋮
                </div>
            </div>
        `;
    });

    html += '</div>';
    container.innerHTML = html;
    updateSelectionToolbar();

    // Force container to expand
    container.style.height = 'auto';
    container.style.minHeight = '400px';
    container.style.overflow = 'visible';
}

// ============================
// NAVIGATION
// ============================
function openFolder(folderId, folderName, isProtected) {
    if (isProtected) {
        const password = prompt(`Folder "${folderName}" is password protected. Enter password:`);
        if (!password) return;
        // (Implement password check if needed)
    }
    navigateToFolder(folderId);
}

function navigateToFolder(folderId) {
    currentFolderId = folderId; // global
    selectedItems.clear();
    loadFiles();
}

function navigateToParent() {
    currentFolderId = null; // global
    selectedItems.clear();
    loadFiles();
}

async function updateBreadcrumb() {
    const breadcrumb = document.getElementById('breadcrumb');
    if (!currentFolderId) {
        breadcrumb.innerHTML = '<span class="breadcrumb-item" onclick="navigateToParent()">My Drive</span>';
        return;
    }
    try {
        const path = await getBreadcrumbPath(currentFolderId);
        let html = '<span class="breadcrumb-item" onclick="navigateToParent()">My Drive</span>';
        path.forEach(item => {
            html += '<span class="breadcrumb-separator">/</span>';
            html += `<span class="breadcrumb-item" onclick="navigateToFolder('${item.id}')">${escapeHtml(item.name)}</span>`;
        });
        breadcrumb.innerHTML = html;
    } catch (error) {
        breadcrumb.innerHTML = '<span class="breadcrumb-item" onclick="navigateToParent()">My Drive</span>';
    }
}

// ============================
// SELECTION
// ============================
function handleItemClick(event, id, isFolder) {
    if (event.target.type === 'checkbox' || event.target.classList.contains('file-menu')) return;
    if (!event.ctrlKey && !event.metaKey) {
        if (!selectedItems.has(id)) {
            selectedItems.clear();
            selectedItems.add(id);
            renderFiles(allFiles);
        }
    } else {
        toggleSelectItem(event, id, !selectedItems.has(id));
    }
}

function toggleSelectItem(event, id, checked) {
    event.stopPropagation();
    if (checked) selectedItems.add(id);
    else selectedItems.delete(id);
    renderFiles(allFiles);
}

function toggleSelectAll() {
    const selectAll = document.getElementById('selectAllCheckbox').checked;
    if (selectAll) allFiles.forEach(f => selectedItems.add(f.id));
    else selectedItems.clear();
    renderFiles(allFiles);
}

function updateSelectionToolbar() {
    const toolbar = document.getElementById('selectionToolbar');
    const count = selectedItems.size;
    const selectAll = document.getElementById('selectAllCheckbox');
    if (count > 0) {
        toolbar.classList.add('show');
        document.getElementById('selectionCount').innerText = `${count} item${count > 1 ? 's' : ''} selected`;
        if (selectAll) {
            selectAll.checked = count === allFiles.length;
            selectAll.indeterminate = count > 0 && count < allFiles.length;
        }
    } else {
        toolbar.classList.remove('show');
    }
}

// ============================
// FOLDER OPERATIONS
// ============================
function showCreateFolderModal() {
    resetFolderModal();
    showModal('folderModal');
}

function togglePasswordField() {
    const isPrivate = document.getElementById('isPrivate').checked;
    const passwordField = document.getElementById('passwordField');
    const passwordInput = document.getElementById('folderPassword');
    if (isPrivate) {
        passwordField.style.display = 'block';
        passwordInput.required = true;
    } else {
        passwordField.style.display = 'none';
        passwordInput.required = false;
        passwordInput.value = '';
    }
}

function resetFolderModal() {
    document.getElementById('folderName').value = '';
    document.getElementById('isPrivate').checked = false;
    document.getElementById('passwordField').style.display = 'none';
    document.getElementById('folderPassword').value = '';
}

async function createFolder() {
    const name = document.getElementById('folderName').value.trim();
    if (!name) {
        alert('Please enter a folder name');
        return;
    }
    const isPrivate = document.getElementById('isPrivate').checked;
    const password = document.getElementById('folderPassword').value;

    if (isPrivate && (!password || password.length < 4)) {
        alert('Please enter a password (minimum 4 characters) for private folder');
        return;
    }

    try {
        await checkDuplicateName(name, currentFolderId);
    } catch (error) {
        alert(error.message);
        return;
    }

    const createBtn = document.querySelector('#folderModal .btn-primary');
    const originalText = createBtn.textContent;
    createBtn.textContent = 'Creating...';
    createBtn.disabled = true;

    try {
        const payload = {
            action: 'CREATE_FOLDER',
            ids: [],
            destination: currentFolderId || null,
            name: name
        };
        const response = await apiCall('/resources/action', {
            method: 'POST',
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || 'Failed to create folder');
        }
        alert(`Folder "${name}" created successfully.`);
        closeModal('folderModal');
        resetFolderModal();
        await loadFiles();
    } catch (error) {
        console.error('Error creating folder:', error);
        alert('Failed to create folder: ' + error.message);
    } finally {
        createBtn.textContent = originalText;
        createBtn.disabled = false;
    }
}

// ============================
// CONTEXT MENU
// ============================
function showContextMenu(event, id, name, type, fileType, isProtected, parentId) {
    event.stopPropagation();
    contextMenuItem = { id, name, type, fileType, parentId, isProtected };

    const existingMenu = document.querySelector('.dropdown-menu');
    if (existingMenu) existingMenu.remove();

    const menu = document.createElement('div');
    menu.className = 'dropdown-menu show';
    menu.style.position = 'absolute';
    menu.style.top = `${event.clientY}px`;
    menu.style.left = `${event.clientX}px`;

    let items = [];

    if (type === 'FILE') {
        const isViewable = fileType && (
            fileType.startsWith('image/') ||
            fileType === 'application/pdf' ||
            fileType.startsWith('video/')
        );
        items = [
            ...(isViewable ? [{ icon: '👁️', label: 'View', action: () => viewFile(id, name) }] : []),
            { icon: '⬇️', label: 'Download', action: () => downloadFile(id, name) },
            { icon: '✏️', label: 'Rename', action: () => showRenameModal(id, name) },
            { icon: '📋', label: 'Copy', action: () => copyItem(id) },
            { icon: '📁', label: 'Move', action: () => moveItem(id) },
            { icon: '🔗', label: 'Share', action: () => showShareModal(id, name) },
            { icon: '🗑️', label: 'Delete', action: () => deleteItem(id) }
        ];
    } else {
        items = [
            { icon: '📂', label: 'Open', action: () => openFolder(id, name, isProtected) },
            { icon: '✏️', label: 'Rename', action: () => showRenameModal(id, name) },
            { icon: '📁', label: 'Move', action: () => moveItem(id) },
            { icon: '🔗', label: 'Share', action: () => showShareModal(id, name) },
            { icon: '🗑️', label: 'Delete', action: () => deleteItem(id) }
        ];
    }

    items.forEach(item => {
        const div = document.createElement('div');
        div.className = 'dropdown-item';
        div.innerHTML = `${item.icon} ${item.label}`;
        div.onclick = () => {
            item.action();
            menu.remove();
        };
        menu.appendChild(div);
    });

    document.body.appendChild(menu);

    setTimeout(() => {
        document.addEventListener('click', () => menu.remove(), { once: true });
    }, 0);
}

// ============================
// FILE OPERATIONS
// ============================
function showRenameModal(id, currentName) {
    contextMenuItem = { id, name: currentName };
    document.getElementById('newName').value = currentName;
    showModal('renameModal');
}

async function executeRename() {
    const newName = document.getElementById('newName').value.trim();
    if (!newName) return;
    try {
        await checkDuplicateName(newName, contextMenuItem.parentId || currentFolderId);
    } catch (error) {
        alert(error.message);
        return;
    }
    try {
        const response = await apiCall('/resources/action', {
            method: 'POST',
            body: JSON.stringify({
                ids: [contextMenuItem.id],
                action: 'RENAME',
                name: newName
            })
        });
        if (!response.ok) throw new Error('Rename failed');
        closeModal('renameModal');
        await loadFiles();
    } catch (error) {
        alert('Failed to rename: ' + error.message);
    }
}

async function deleteItem(id) {
    const item = allFiles.find(f => f.id === id);
    if (!item) { alert('Item not found'); return; }
    if (!confirm(`Are you sure you want to delete "${item.name}"?`)) return;
    try {
        const response = await apiCall('/resources/action', {
            method: 'POST',
            body: JSON.stringify({ ids: [id], action: 'DELETE' })
        });
        if (!response.ok) throw new Error('Delete failed');
        selectedItems.delete(id);
        await loadFiles();
        safeToast(`"${item.name}" deleted successfully`, 'success');
    } catch (error) {
        safeToast('Failed to delete: ' + error.message, 'error');
    }
}

async function deleteSelected() {
    if (selectedItems.size === 0) return;
    const itemsToDelete = Array.from(selectedItems).map(id => allFiles.find(f => f.id === id)).filter(item => item != null);
    if (!confirm(`Delete ${itemsToDelete.length} item(s)?`)) return;
    try {
        const response = await apiCall('/resources/action', {
            method: 'POST',
            body: JSON.stringify({ ids: Array.from(selectedItems), action: 'DELETE' })
        });
        if (!response.ok) throw new Error('Bulk delete failed');
        selectedItems.clear();
        await loadFiles();
        safeToast(`Deleted ${itemsToDelete.length} item(s)`, 'success');
    } catch (error) {
        safeToast('Failed to delete: ' + error.message, 'error');
    }
}

async function downloadFile(fileId, filename) {
    try {
        const response = await apiCall(`/download/${fileId}`);
        if (!response.ok) throw new Error('Download failed');
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
    } catch (error) {
        safeToast('Download failed: ' + error.message, 'error');
    }
}

function viewFile(fileId, filename) {
    window.open(`/viewer.html?id=${fileId}&token=${jwtToken}`, '_blank');
}

async function copyItem(id) {
    pendingAction = 'copy';
    pendingItems = [{ id, password: null }];
    await selectDestination();
}

async function moveItem(id) {
    pendingAction = 'move';
    pendingItems = [{ id, password: null }];
    await selectDestination();
}

async function copySelected() {
    pendingAction = 'copy';
    pendingItems = Array.from(selectedItems).map(id => ({ id, password: null }));
    await selectDestination();
}

async function moveSelected() {
    pendingAction = 'move';
    pendingItems = Array.from(selectedItems).map(id => ({ id, password: null }));
    await selectDestination();
}

async function selectDestination() {
    const folders = allFiles.filter(f => (f.driveType === 'FOLDER' || f.driveType === 'ROOT') && !pendingItems.map(p => p.id).includes(f.id));
    const select = document.getElementById('destinationFolder');
    select.innerHTML = '<option value="">Root</option>';
    folders.forEach(f => {
        select.innerHTML += `<option value="${f.id}">${escapeHtml(f.name)}</option>`;
    });
    document.getElementById('moveModalTitle').innerText = pendingAction === 'copy' ? 'Copy to' : 'Move to';
    showModal('moveModal');
}

async function executeMove() {
    const destId = document.getElementById('destinationFolder').value;
    const destinationId = destId || null;
    let success = 0, fail = 0;
    for (const item of pendingItems) {
        try {
            const response = await apiCall('/resources/action', {
                method: 'POST',
                body: JSON.stringify({
                    ids: [item.id],
                    action: pendingAction === 'copy' ? 'COPY' : 'MOVE',
                    destination: destinationId
                })
            });
            if (!response.ok) throw new Error(`${pendingAction} failed`);
            success++;
        } catch (error) {
            fail++;
        }
    }
    const action = pendingAction;
    closeModal('moveModal');
    if (action === 'move') selectedItems.clear();
    await loadFiles();
    pendingItems = [];
    pendingAction = null;
    if (success > 0) {
        safeToast(`${action === 'copy' ? 'Copied' : 'Moved'} ${success} item(s)${fail > 0 ? `, ${fail} failed` : ''}`, 'success');
    } else {
        safeToast(`Failed to ${action} ${fail} item(s)`, 'error');
    }
}

// ============================
// USER INFO
// ============================
async function loadUserInfo() {
    try {
        const user = getUserFromToken();
        if (!user || !user.email) {
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

// ============================
// EXPOSE GLOBALLY
// ============================
window.loadFiles = loadFiles;
window.renderFiles = renderFiles;
window.openFolder = openFolder;
window.navigateToFolder = navigateToFolder;
window.navigateToParent = navigateToParent;
window.handleItemClick = handleItemClick;
window.toggleSelectItem = toggleSelectItem;
window.toggleSelectAll = toggleSelectAll;
window.showCreateFolderModal = showCreateFolderModal;
window.togglePasswordField = togglePasswordField;
window.resetFolderModal = resetFolderModal;
window.createFolder = createFolder;
window.showContextMenu = showContextMenu;
window.showRenameModal = showRenameModal;
window.executeRename = executeRename;
window.deleteItem = deleteItem;
window.deleteSelected = deleteSelected;
window.downloadFile = downloadFile;
window.viewFile = viewFile;
window.copyItem = copyItem;
window.moveItem = moveItem;
window.copySelected = copySelected;
window.moveSelected = moveSelected;
window.selectDestination = selectDestination;
window.executeMove = executeMove;
window.updateSelectionToolbar = updateSelectionToolbar;
window.loadUserInfo = loadUserInfo;