// File Operations (static2 - adjusted endpoints)
async function loadFiles() {
    const container = document.getElementById('fileContainer');
    showLoading(container, true);

    try {
        // Load files for current folder view
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

    const folders = files.filter(f => f.driveType === 'ROOT' || f.driveType === 'FOLDER');
    const fileItems = files.filter(f => f.driveType === 'FILE');

    let html = '<div class="file-grid">';

    [...folders, ...fileItems].forEach(item => {
        const isSelected = selectedItems.has(item.id);
        const icon = item.driveType === 'FILE' ? getFileIcon(item.name) : '📁';
        const info = item.driveType === 'FILE' ? formatFileSize(item.fileSize) : (item.hasChildren ? 'Contains items' : 'Empty');
        const isFolder = item.driveType === 'ROOT' || item.driveType === 'FOLDER';
        const isProtected = item.accessType === 'PROTECTED';
        const safeId = encodeURIComponent(item.id);
        const safeName = encodeURIComponent(item.name).replace(/'/g, '%27');
        const safeType = encodeURIComponent(item.driveType);
        const safeFileType = encodeURIComponent(item.fileType || '');
        const safeParentId = item.parentId ? encodeURIComponent(item.parentId) : '';
        const lockIcon = isProtected ? '🔒 ' : '';

        let doubleClickAction = '';
        if (isFolder) {
            doubleClickAction = `openFolder(decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'), ${isProtected})`;
        } else {
            const isViewable = item.fileType && (
                item.fileType.startsWith('image/') ||
                item.fileType === 'application/pdf' ||
                item.fileType.startsWith('video/')
            );
            if (isViewable) {
                doubleClickAction = `viewFile(decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'))`;
            } else {
                doubleClickAction = `downloadFile(decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'))`;
            }
        }

        html += `
            <div class="file-item ${isSelected ? 'selected' : ''}" 
                 data-id="${item.id}" 
                 data-parent-id="${item.parentId || ''}"
                 data-type="${item.driveType}" 
                 data-name="${escapeHtml(item.name)}"
                 ondblclick="${doubleClickAction}"
                 onclick="handleItemClick(event, decodeURIComponent('${safeId}'), ${isFolder})">
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
}

function handleItemClick(event, id, isFolder) {
    if (event.target.type === 'checkbox' || event.target.classList.contains('file-menu')) {
        return;
    }

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
    if (checked) {
        selectedItems.add(id);
    } else {
        selectedItems.delete(id);
    }
    renderFiles(allFiles);
}

function toggleSelectAll() {
    const selectAll = document.getElementById('selectAllCheckbox').checked;
    if (selectAll) {
        allFiles.forEach(f => selectedItems.add(f.id));
    } else {
        selectedItems.clear();
    }
    renderFiles(allFiles);
}

function updateSelectionToolbar() {
    const toolbar = document.getElementById('selectionToolbar');
    const count = selectedItems.size;
    const selectAllCheckbox = document.getElementById('selectAllCheckbox');

    if (count > 0) {
        toolbar.classList.add('show');
        document.getElementById('selectionCount').innerText = `${count} item${count > 1 ? 's' : ''} selected`;
        selectAllCheckbox.checked = count === allFiles.length;
        selectAllCheckbox.indeterminate = count > 0 && count < allFiles.length;
    } else {
        toolbar.classList.remove('show');
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

            if (response.status === 403) {
                alert('Invalid password!');
                return;
            }

            if (!response.ok) {
                throw new Error('Failed to access folder');
            }

            navigateToFolder(folderId);
        } catch (error) {
            console.error('Error accessing protected folder:', error);
            alert('Failed to access folder');
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
            html += '<span class="breadcrumb-separator">/</span>';
            html += `<span class="breadcrumb-item" onclick="navigateToFolder(decodeURIComponent('${encodeURIComponent(item.id)}'))">${escapeHtml(item.name)}</span>`;
        }

        breadcrumb.innerHTML = html;
    } catch (error) {
        console.error('Error loading path:', error);
        breadcrumb.innerHTML = '<span class="breadcrumb-item" onclick="navigateToParent()">My Drive</span>';
    }
}

function viewFile(fileId, filename) {
    // Open the dedicated viewer page
    window.open(`${API_URL}/viewer.html?id=${fileId}&token=${jwtToken}`, '_blank');
}

async function downloadFile(fileId, filename) {
    try {
        // Use /download/{id} endpoint with Authorization header
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
        console.error('Error downloading file:', error);
        alert('Failed to download file');
    }
}

async function deleteItem(id) {
    try {
        const item = allFiles.find(f => f.id === id);

        if (!item) {
            alert('Item not found');
            return;
        }

        if (!confirm(`Are you sure you want to delete "${item.name}"?`)) return;

        // Use POST /resources/action endpoint
        const response = await apiCall('/resources/action', {
            method: 'POST',
            body: JSON.stringify({
                ids: [id],
                action: 'DELETE'
            })
        });

        if (!response.ok) {
            throw new Error(`Delete failed with status ${response.status}`);
        }

        selectedItems.delete(id);
        await loadFiles();
        alert(`"${item.name}" deleted successfully`);

    } catch (error) {
        console.error('Error deleting item:', error);
        alert('Failed to delete item: ' + error.message);
    }
}

async function deleteSelected() {
    if (selectedItems.size === 0) return;

    const itemsToDelete = Array.from(selectedItems).map(id =>
        allFiles.find(f => f.id === id)
    ).filter(item => item != null);

    if (itemsToDelete.length === 0) {
        alert('No items to delete');
        return;
    }

    const confirmMsg = `Delete ${itemsToDelete.length} item(s)? This cannot be undone.`;
    if (!confirm(confirmMsg)) return;

    try {
        // Use POST /resources/action endpoint for bulk delete
        const response = await apiCall('/resources/action', {
            method: 'POST',
            body: JSON.stringify({
                ids: Array.from(selectedItems),
                action: 'DELETE'
            })
        });

        if (!response.ok) {
            throw new Error(`Delete failed with status ${response.status}`);
        }

        selectedItems.clear();
        await loadFiles();
        alert(`Deleted ${itemsToDelete.length} item(s) successfully`);

    } catch (error) {
        console.error('Error deleting items:', error);
        alert('Failed to delete items: ' + error.message);
    }
}

async function downloadSelected() {
    const files = Array.from(selectedItems)
        .map(id => allFiles.find(file => file.id === id))
        .filter(file => file && file.driveType === 'FILE');
    if (!files.length) {
        alert('Select at least one file to download.');
        return;
    }
    for (const file of files) {
        await downloadFile(file.id, file.name);
    }
}
