// fileRenderer.js – File rendering and navigation

async function loadFiles() {
    const container = document.getElementById('fileContainer');
    if (!container) return;
    showLoading(container, true);
    try {
        const files = await getFilesForFolder(currentFolderId);
        allFiles = files;
        if (files && files.length > 0) renderFiles(files);
        else showEmptyState(container, 'This folder is empty');
        updateBreadcrumb();
        updateSelectionToolbar();
    } catch (error) {
        showError(container);
        safeToast('Failed to load files: ' + error.message, 'error');
    }
}

function renderFiles(files) {
    const container = document.getElementById('fileContainer');
    if (!files || files.length === 0) { showEmptyState(container); return; }

    let html = '<div class="file-grid">';
    const folders = files.filter(f => f.driveType === 'ROOT' || f.driveType === 'FOLDER');
    const items = files.filter(f => f.driveType === 'FILE');

    [...folders, ...items].forEach(item => {
        const isSelected = selectedItems.has(item.id);
        const icon = item.driveType === 'FILE' ? getFileIcon(item.name) : '📁';
        const info = item.driveType === 'FILE' ? formatFileSize(item.fileSize) :
            (item.childrenCount > 0 ? `${item.childrenCount} item(s)` : 'Empty');
        const isFolder = item.driveType === 'ROOT' || item.driveType === 'FOLDER';
        const isProtected = item.accessType === 'PROTECTED';
        const lockIcon = isProtected ? '🔒 ' : '';
        const eId = escapeJSString(item.id);
        const eName = escapeJSString(item.name);
        const eParentId = escapeJSString(item.parentId);
        const eFileType = escapeJSString(item.fileType || '');

        let doubleClickAction = '';
        if (isFolder) doubleClickAction = `openFolder('${eId}','${eName}',${isProtected})`;
        else {
            const viewable = item.fileType && (item.fileType.startsWith('image/') || item.fileType === 'application/pdf' || item.fileType.startsWith('video/'));
            doubleClickAction = viewable ? `viewFile('${eId}','${eName}')` : `downloadFile('${eId}','${eName}')`;
        }

        html += `
            <div class="file-item ${isSelected ? 'selected' : ''}" data-id="${item.id}" ondblclick="${doubleClickAction}" onclick="handleItemClick(event,'${eId}',${isFolder})">
                <input type="checkbox" class="checkbox" ${isSelected ? 'checked' : ''} onchange="toggleSelectItem(event,'${eId}',this.checked)">
                <div class="file-icon">${icon}</div>
                <div class="file-name">${lockIcon}${escapeHtml(item.name)}</div>
                <div class="file-info">${info}</div>
                <div class="file-menu" onclick="showContextMenu(event,'${eId}','${eName}','${item.driveType}','${eFileType}',${isProtected},'${eParentId}')">⋮</div>
            </div>
        `;
    });
    html += '</div>';
    container.innerHTML = html;
    updateSelectionToolbar();
}

function handleItemClick(event, id, isFolder) {
    if (event.target.type === 'checkbox' || event.target.classList.contains('file-menu')) return;
    if (!event.ctrlKey && !event.metaKey) {
        selectedItems.clear();
        selectedItems.add(id);
        renderFiles(allFiles);
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
    const checked = document.getElementById('selectAllCheckbox').checked;
    if (checked) allFiles.forEach(f => selectedItems.add(f.id));
    else selectedItems.clear();
    renderFiles(allFiles);
}

function updateSelectionToolbar() {
    const toolbar = document.getElementById('selectionToolbar');
    if (!toolbar) return;
    const count = selectedItems.size;
    const selectAll = document.getElementById('selectAllCheckbox');
    const countEl = document.getElementById('selectionCount');
    if (count > 0) {
        toolbar.classList.add('show');
        if (countEl) countEl.innerText = `${count} item${count > 1 ? 's' : ''} selected`;
        if (selectAll) {
            selectAll.checked = count === allFiles.length;
            selectAll.indeterminate = count > 0 && count < allFiles.length;
        }
    } else {
        toolbar.classList.remove('show');
        if (selectAll) {
            selectAll.checked = false;
            selectAll.indeterminate = false;
        }
    }
}

async function openFolder(folderId, folderName, isProtected) {
    if (isProtected) {
        const password = prompt(`Folder "${folderName}" is password protected. Enter password:`);
        if (!password) return;
        try {
            const response = await fetch(`${API_URL}/drive/${folderId}?password=${encodeURIComponent(password)}`, {
                headers: { 'Authorization': `Bearer ${jwtToken}` }
            });
            if (response.status === 403) { safeToast('Invalid password!', 'error'); return; }
            if (!response.ok) throw new Error('Failed to access folder');
            navigateToFolder(folderId);
        } catch (error) {
            safeToast('Failed to access folder: ' + error.message, 'error');
        }
    } else {
        navigateToFolder(folderId);
    }
}

function navigateToFolder(folderId) {
    currentFolderId = folderId;
    selectedItems.clear();
    loadFiles();
}

function navigateToParent() {
    currentFolderId = null;
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
        for (const item of path) {
            html += `<span class="breadcrumb-separator">/</span><span class="breadcrumb-item" onclick="navigateToFolder('${escapeJSString(item.id)}')">${escapeHtml(item.name)}</span>`;
        }
        breadcrumb.innerHTML = html;
    } catch (error) {
        breadcrumb.innerHTML = '<span class="breadcrumb-item" onclick="navigateToParent()">My Drive</span>';
    }
}

window.loadFiles = loadFiles;
window.renderFiles = renderFiles;
window.navigateToFolder = navigateToFolder;
window.navigateToParent = navigateToParent;
window.openFolder = openFolder;
window.updateBreadcrumb = updateBreadcrumb;
window.handleItemClick = handleItemClick;
window.toggleSelectItem = toggleSelectItem;
window.toggleSelectAll = toggleSelectAll;
window.updateSelectionToolbar = updateSelectionToolbar;