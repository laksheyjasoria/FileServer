// trash.js – Complete updated version (robust for missing UI elements)

// ============================
// INIT
// ============================
document.addEventListener('DOMContentLoaded', async () => {
    const token = localStorage.getItem('jwtToken');
    if (!token) {
        window.location.href = '/login.html';
        return;
    }

    setupTrashUI();
    await loadTrash();
});

function setupTrashUI() {
    const selectAllCheckbox = document.getElementById('selectAllCheckbox');
    if (selectAllCheckbox) {
        selectAllCheckbox.addEventListener('change', toggleSelectAll);
    }

    const emptyTrashBtn = document.getElementById('emptyTrashBtn');
    if (emptyTrashBtn) {
        emptyTrashBtn.addEventListener('click', emptyTrash);
    }

    const restoreSelectedBtn = document.getElementById('restoreSelectedBtn');
    if (restoreSelectedBtn) {
        restoreSelectedBtn.addEventListener('click', restoreSelected);
    }
}

// ============================
// LOAD TRASH
// ============================
async function loadTrash() {
    let container = document.getElementById('trashContainer');
    if (!container) {
        console.warn('Container #trashContainer missing – creating one.');
        const body = document.body || document.documentElement;
        container = document.createElement('div');
        container.id = 'trashContainer';
        body.appendChild(container);
    }

    // Use global showLoading from utils
    showLoading(container, true);

    try {
        const files = await getTrashItems({ skipDedupe: true });

        if (!files || files.length === 0) {
            showEmptyState(container, 'Trash is empty', 'Items deleted from your drive will appear here');
            allFiles = [];
            return;
        }

        allFiles = files;
        renderFiles(files);
        updateBreadcrumb();
    } catch (error) {
        console.error('Error loading trash:', error);
        showError(container, 'Failed to load trash items. Please try again.');
    }
}

// ============================
// RENDER FUNCTIONS
// ============================
function renderFiles(files) {
    const container = document.getElementById('trashContainer');
    if (!container) return;

    if (!files || files.length === 0) {
        showEmptyState(container, 'Trash is empty', 'Items deleted from your drive will appear here');
        return;
    }

    const folders = files.filter(f => f.driveType === 'FOLDER' || f.driveType === 'ROOT');
    const fileItems = files.filter(f => f.driveType === 'FILE');

    let html = '<div class="file-grid">';

    folders.forEach(item => {
        const isSelected = selectedItems.has(item.id);
        const icon = '📁';
        const info = item.hasChildren ? 'Contains items' : 'Empty';
        html += createFileItemHTML(item, isSelected, icon, info, true);
    });

    fileItems.forEach(item => {
        const isSelected = selectedItems.has(item.id);
        const icon = getFileIcon(item.name);
        const info = formatFileSize(item.fileSize);
        html += createFileItemHTML(item, isSelected, icon, info, false);
    });

    html += '</div>';
    container.innerHTML = html;
    updateSelectionToolbar();
}

function createFileItemHTML(item, isSelected, icon, info, isFolder) {
    const safeId = encodeURIComponent(item.id);
    const safeName = encodeURIComponent(item.name).replace(/'/g, '%27');
    const safeType = encodeURIComponent(item.driveType);
    const safeFileType = encodeURIComponent(item.fileType || '');
    const isProtected = item.accessType === 'PROTECTED';
    const lockIcon = isProtected ? '🔒 ' : '';

    const doubleClickAction = `restoreItem(decodeURIComponent('${safeId}'))`;

    let deletedInfo = '';
    if (item.deletedAt) {
        const date = new Date(item.deletedAt);
        deletedInfo = `Deleted: ${date.toLocaleDateString()}`;
    }

    return `
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
            ${deletedInfo ? `<div class="file-deleted-info" style="font-size:11px;color:#999;">${deletedInfo}</div>` : ''}
            <div class="file-menu" onclick="showTrashContextMenu(event, decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'), decodeURIComponent('${safeType}'), decodeURIComponent('${safeFileType}'), ${isProtected})">
                ⋮
            </div>
        </div>
    `;
}

// ============================
// SELECTION
// ============================
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
    if (checked) selectedItems.add(id);
    else selectedItems.delete(id);
    renderFiles(allFiles);
}

function toggleSelectAll() {
    const selectAll = document.getElementById('selectAllCheckbox');
    if (!selectAll) return;
    const checked = selectAll.checked;
    if (checked) allFiles.forEach(f => selectedItems.add(f.id));
    else selectedItems.clear();
    renderFiles(allFiles);
}

function updateSelectionToolbar() {
    const toolbar = document.getElementById('selectionToolbar');
    if (!toolbar) return; // silently skip if missing

    const count = selectedItems.size;
    const selectAllCheckbox = document.getElementById('selectAllCheckbox');
    if (count > 0) {
        toolbar.classList.add('show');
        const countEl = document.getElementById('selectionCount');
        if (countEl) countEl.innerText = `${count} item${count > 1 ? 's' : ''} selected`;
        if (selectAllCheckbox) {
            selectAllCheckbox.checked = count === allFiles.length;
            selectAllCheckbox.indeterminate = count > 0 && count < allFiles.length;
        }
    } else {
        toolbar.classList.remove('show');
    }
}

// ============================
// BREADCRUMB
// ============================
function updateBreadcrumb() {
    const breadcrumb = document.getElementById('breadcrumb');
    if (breadcrumb) {
        breadcrumb.innerHTML = `<span class="breadcrumb-item">🗑️ Trash</span>`;
    }
}

// ============================
// CONTEXT MENU
// ============================
function showTrashContextMenu(event, id, name, type, fileType, isProtected) {
    event.stopPropagation();
    const existingMenu = document.querySelector('.dropdown-menu');
    if (existingMenu) existingMenu.remove();

    const menu = document.createElement('div');
    menu.className = 'dropdown-menu show';
    menu.style.position = 'absolute';
    menu.style.top = `${event.clientY}px`;
    menu.style.left = `${event.clientX}px`;

    const items = [
        { icon: '↩️', label: 'Restore', action: () => restoreItem(id) },
        { icon: '🗑️', label: 'Delete Permanently', action: () => deletePermanently(id) }
    ];
    if (type === 'FILE') {
        items.push({ icon: '⬇️', label: 'Download', action: () => downloadFile(id, name) });
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
    setTimeout(() => document.addEventListener('click', () => menu.remove(), { once: true }), 0);
}

// ============================
// TRASH OPERATIONS
// ============================
async function restoreItem(id) {
    try {
        await restoreFromTrash(id);
        selectedItems.delete(id);
        await loadTrash();
        safeToast('Item restored successfully', 'success');
    } catch (error) {
        safeToast(error.message || 'Restore failed', 'error');
    }
}

async function deletePermanently(id) {
    if (!confirm('Permanently delete this item? This cannot be undone.')) return;
    try {
        await permanentDeleteItem(id);
        selectedItems.delete(id);
        await loadTrash();
        safeToast('Item permanently deleted', 'success');
    } catch (error) {
        safeToast(error.message || 'Delete failed', 'error');
    }
}

async function restoreSelected() {
    if (selectedItems.size === 0) {
        safeToast('No items selected', 'warning');
        return;
    }
    if (!confirm(`Restore ${selectedItems.size} item(s)?`)) return;

    const ids = Array.from(selectedItems);
    let successCount = 0;
    let failCount = 0;

    for (const id of ids) {
        try {
            await restoreFromTrash(id);
            successCount++;
        } catch (error) {
            console.error(`Failed to restore ${id}:`, error);
            failCount++;
        }
    }

    selectedItems.clear();
    await loadTrash();
    safeToast(`Restored ${successCount} item(s)${failCount > 0 ? `, ${failCount} failed` : ''}`, 'success');
}

async function emptyTrash() {
    if (allFiles.length === 0) {
        safeToast('Trash is already empty', 'info');
        return;
    }
    if (!confirm('Permanently delete ALL items in trash? This cannot be undone.')) return;

    try {
        await emptyTrashApi();
        selectedItems.clear();
        await loadTrash();
        safeToast('Trash emptied successfully', 'success');
    } catch (error) {
        safeToast(error.message || 'Empty trash failed', 'error');
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

// ============================
// EXPOSE GLOBAL FUNCTIONS
// ============================
window.restoreItem = restoreItem;
window.deletePermanently = deletePermanently;
window.restoreSelected = restoreSelected;
window.emptyTrash = emptyTrash;
window.showTrashContextMenu = showTrashContextMenu;
window.handleItemClick = handleItemClick;
window.toggleSelectItem = toggleSelectItem;
window.toggleSelectAll = toggleSelectAll;
window.loadTrash = loadTrash;