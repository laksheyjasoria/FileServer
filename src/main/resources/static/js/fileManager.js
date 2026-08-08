// File Operations
async function loadAllFiles() {
    try {
        // This is now only used for search; we'll keep it as is,
        // but you may want to implement a dedicated search endpoint.
        const response = await apiCall('/drive');
        if (!response.ok) throw new Error('Failed to load files');
        const files = await response.json();
        return files.map(file => ({
            ...file,
            fileSize: file.size || 0,
            fileType: file.contentType || '',
            driveType: file.driveType || (file.fileId ? 'FILE' : 'FOLDER'),
            accessType: file.accessType || 'PUBLIC'
        }));
    } catch (error) {
        console.error('Error loading all files:', error);
        return [];
    }
}

function renderFiles(files) {
	console.log('🖼️ renderFiles called with', files.length, 'items:', files);
    const container = document.getElementById('fileContainer');
	
	console.log('📦 Container element:', container);
	console.log('📐 Container display style:', getComputedStyle(container).display);

    if (!files || files.length === 0) {
        showEmptyState(container);
        return;
    }

    const folders = files.filter(f => f.driveType === 'ROOT' || f.driveType === 'FOLDER');
    const fileItems = files.filter(f => f.driveType === 'FILE');

    let html = '<div class="file-grid">';

    [...folders, ...fileItems].forEach(item => {
		console.log('driveType values:', files.map(f => f.driveType));
        const isSelected = selectedItems.has(item.id);
        const icon = item.driveType === 'FILE' ? getFileIcon(item.name) : '📁';
        const info = item.driveType === 'FILE' ? formatFileSize(item.fileSize) : (item.hasChildren ? 'Contains items' : 'Empty');
        const isFolder = item.driveType === 'ROOT' || item.driveType === 'FOLDER';
        const isProtected = item.accessType === 'PROTECTED';
        const lockIcon = isProtected ? '🔒 ' : '';

        let doubleClickAction = '';
        if (isFolder) {
            doubleClickAction = `openFolder(${item.id}, '${escapeHtml(item.name)}', ${isProtected})`;
        } else {
            const isViewable = item.fileType && (
                item.fileType.startsWith('image/') ||
                item.fileType === 'application/pdf' ||
                item.fileType.startsWith('video/')
            );
            if (isViewable) {
                doubleClickAction = `viewFile(${item.id}, '${escapeHtml(item.name)}')`;
            } else {
                doubleClickAction = `downloadFile(${item.id}, '${escapeHtml(item.name)}')`;
            }
        }

        html += `
            <div class="file-item ${isSelected ? 'selected' : ''}" 
                 data-id="${item.id}" 
                 data-parent-id="${item.parentId || ''}"
                 data-type="${item.driveType}" 
                 data-name="${escapeHtml(item.name)}"
                 ondblclick="${doubleClickAction}"
                 onclick="handleItemClick(event, ${item.id}, ${isFolder})">
                <input type="checkbox" class="checkbox" ${isSelected ? 'checked' : ''} onchange="toggleSelectItem(event, ${item.id}, this.checked)">
                <div class="file-icon">${icon}</div>
                <div class="file-name">${lockIcon}${escapeHtml(item.name)}</div>
                <div class="file-info">${info}</div>
                <div class="file-menu" onclick="showContextMenu(event, ${item.id}, '${escapeHtml(item.name)}', '${item.driveType}', '${item.fileType || ''}', ${isProtected}, ${item.parentId || 'null'})">
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
        const response = await apiCall(`/drive/path/${currentFolderId}`);
        const path = await response.text();
        const parts = path.split('/').filter(p => p);
        let html = '<span class="breadcrumb-item" onclick="navigateToParent()">My Drive</span>';
        for (let i = 0; i < parts.length; i++) {
            html += '<span class="breadcrumb-separator">/</span>';
            html += `<span class="breadcrumb-item">${escapeHtml(parts[i])}</span>`;
        }
        breadcrumb.innerHTML = html;
    } catch (error) {
        console.error('Error loading path:', error);
    }
}

function viewFile(fileId, filename) {
    // Open the dedicated viewer page
    window.open(`${API_URL}/viewer.html?id=${fileId}&token=${jwtToken}`, '_blank');
}

async function downloadFile(fileId, filename) {
    try {
        const response = await fetch(`${API_URL}/download/${fileId}?token=${jwtToken}`);
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
        // Try to get item from cache first
        let item = allFiles.find(f => f.id === id);

        if (!item) {
            // If not in cache, fetch from API
            const response = await fetch(`${API_URL}/drive/${id}`, {
                headers: { 'Authorization': `Bearer ${jwtToken}` }
            });

            if (!response.ok) {
                alert(`Failed to get item details. Please refresh the page.`);
                return;
            }

            item = await response.json();
        }

        let password = null;
        if (item.accessType === 'PROTECTED') {
            password = prompt(`"${item.name}" is password protected. Enter password to delete:`);
            if (password === null) return;
            if (!password || password.trim() === '') {
                alert('Password is required to delete this protected item');
                return;
            }
        }

        const confirmMsg = password
            ? `Are you sure you want to delete "${item.name}"? This action cannot be undone.\n\nNote: This is a password protected item.`
            : `Are you sure you want to delete "${item.name}"?`;

        if (!confirm(confirmMsg)) return;

        let deleteUrl = `/resources/action`;
        // backend expects a resource action request for DELETE
        const body = { action: 'DELETE', ids: [id] };
        if (password) {
            deleteUrl += `?password=${encodeURIComponent(password)}`;
        }

        const deleteResponse = await fetch(`${API_URL}${deleteUrl}`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${jwtToken}`, 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });

        if (deleteResponse.status === 204) {
            selectedItems.delete(id);
            await loadFiles();
            alert(`"${item.name}" deleted successfully`);
            return;
        }

        if (deleteResponse.status === 403) {
            alert('Invalid password! Cannot delete protected item.');
            return;
        }

        if (!deleteResponse.ok) {
            throw new Error(`Delete failed with status ${deleteResponse.status}`);
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

    const itemsToDelete = [];
    const passwordMap = new Map();

    // First, collect all items and passwords
    for (const id of selectedItems) {
        try {
            // Use fetch directly instead of apiCall to avoid JSON parsing issues
            const response = await fetch(`${API_URL}/drive/${id}`, {
                headers: { 'Authorization': `Bearer ${jwtToken}` }
            });

            if (!response.ok) {
                console.error(`Failed to fetch item ${id}:`, response.status);
                // Try to get the item from allFiles array instead
                const itemFromCache = allFiles.find(f => f.id === id);
                if (itemFromCache) {
                    itemsToDelete.push(itemFromCache);
                    if (itemFromCache.accessType === 'PROTECTED') {
                        const password = prompt(`"${itemFromCache.name}" is password protected. Enter password to delete it:`);
                        if (password === null) return;
                        if (!password || password.trim() === '') {
                            alert(`Password is required to delete "${itemFromCache.name}". Delete cancelled.`);
                            return;
                        }
                        passwordMap.set(id, password);
                    }
                    continue;
                }
                alert(`Failed to get details for item ID ${id}. Please refresh and try again.`);
                return;
            }

            const item = await response.json();
            itemsToDelete.push(item);

            if (item.accessType === 'PROTECTED') {
                const password = prompt(`"${item.name}" is password protected. Enter password to delete it:`);
                if (password === null) return;
                if (!password || password.trim() === '') {
                    alert(`Password is required to delete "${item.name}". Delete cancelled.`);
                    return;
                }
                passwordMap.set(id, password);
            }
        } catch (error) {
            console.error('Error fetching item details:', error);
            // Try to get from cache
            const itemFromCache = allFiles.find(f => f.id === id);
            if (itemFromCache) {
                itemsToDelete.push(itemFromCache);
                if (itemFromCache.accessType === 'PROTECTED') {
                    const password = prompt(`"${itemFromCache.name}" is password protected. Enter password to delete it:`);
                    if (password === null) return;
                    if (!password || password.trim() === '') {
                        alert(`Password is required to delete "${itemFromCache.name}". Delete cancelled.`);
                        return;
                    }
                    passwordMap.set(id, password);
                }
                continue;
            }
            alert(`Failed to get item details. Please refresh the page and try again.\n\nError: ${error.message}`);
            return;
        }
    }

    if (itemsToDelete.length === 0) {
        alert('No items to delete');
        return;
    }

    const confirmMsg = `Delete ${itemsToDelete.length} item(s)? This cannot be undone.`;
    if (!confirm(confirmMsg)) return;

    let successCount = 0;
    let failCount = 0;
    const failedItems = [];

    for (const item of itemsToDelete) {
        try {
            // Use resources action endpoint for bulk deletes
            const password = passwordMap.get(item.id);
            // password is currently not appended to this generic endpoint; backend may need adjustment
            const deleteResponse = await fetch(`${API_URL}/resources/action`, {
                method: 'POST',
                headers: { 'Authorization': `Bearer ${jwtToken}`, 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: 'DELETE', ids: [item.id] })
            });

            if (deleteResponse.status === 204 || deleteResponse.ok) {
                successCount++;
                selectedItems.delete(item.id);
            } else if (deleteResponse.status === 403) {
                failCount++;
                failedItems.push(`${item.name} (invalid password)`);
            } else {
                failCount++;
                failedItems.push(item.name);
            }
        } catch (error) {
            console.error(`Error deleting ${item.name}:`, error);
            failCount++;
            failedItems.push(item.name);
        }
    }

    await loadFiles();

    if (successCount > 0) {
        let message = `Deleted ${successCount} item(s) successfully.`;
        if (failCount > 0) {
            message += `\nFailed to delete ${failCount} item(s): ${failedItems.join(', ')}`;
        }
        alert(message);
    } else if (failCount > 0) {
        alert(`Failed to delete ${failCount} item(s): ${failedItems.join(', ')}\n\nPlease check passwords and try again.`);
    }
}
async function refreshFileList() {
    try {
        // Delegate to main loader which handles normalization
        await loadFiles();
    } catch (error) {
        console.error('Error refreshing file list:', error);
    }
}