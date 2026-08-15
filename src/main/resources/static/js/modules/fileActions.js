// fileActions.js – File operations (delete, download, copy, move)

function viewFile(fileId, filename) {
    window.open(`${API_URL}/viewer.html?id=${fileId}&token=${jwtToken}`, '_blank');
}

async function downloadFile(fileId, filename) {
    safeToast('Preparing download...', 'info', 2000);
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
        safeToast('File downloaded successfully.', 'success');
    } catch (error) {
        safeToast('Failed to download file: ' + error.message, 'error');
    }
}

async function deleteItem(id) {
    const item = allFiles.find(f => f.id === id);
    if (!item) { safeToast('Item not found', 'error'); return; }
    const confirmed = await showConfirm(
        `Are you sure you want to delete "${item.name}"? This cannot be undone.`,
        'Delete Item', 'Delete', 'Cancel', 'danger'
    );
    if (!confirmed) return;
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
    if (selectedItems.size === 0) { safeToast('No items selected.', 'warning'); return; }
    const confirmed = await showConfirm(
        `Delete ${selectedItems.size} item(s)? This cannot be undone.`,
        'Delete Items', 'Delete', 'Cancel', 'danger'
    );
    if (!confirmed) return;
    try {
        const response = await apiCall('/resources/action', {
            method: 'POST',
            body: JSON.stringify({ ids: Array.from(selectedItems), action: 'DELETE' })
        });
        if (!response.ok) throw new Error('Delete failed');
        selectedItems.clear();
        await loadFiles();
        safeToast('Deleted items successfully.', 'success');
    } catch (error) {
        safeToast('Failed to delete items: ' + error.message, 'error');
    }
}

async function downloadSelected() {
    const ids = Array.from(selectedItems);
    if (ids.length === 0) { safeToast('Select at least one item.', 'warning'); return; }
    safeToast('Preparing ZIP download...', 'info', 2000);
    try {
        const response = await apiCall('/download/bulk', {
            method: 'POST',
            body: JSON.stringify(ids)
        });
        if (!response.ok) throw new Error('Download failed');
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'download.zip';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
        selectedItems.clear();
        renderFiles(allFiles);
        safeToast('Download completed.', 'success');
    } catch (error) {
        safeToast('Failed to download items: ' + error.message, 'error');
    }
}

async function copyItem(id) { pendingAction = 'copy'; pendingItems = [{ id, password: null }]; await selectDestination(); }
async function moveItem(id) { pendingAction = 'move'; pendingItems = [{ id, password: null }]; await selectDestination(); }
async function copySelected() { pendingAction = 'copy'; pendingItems = Array.from(selectedItems).map(id => ({ id, password: null })); await selectDestination(); }
async function moveSelected() { pendingAction = 'move'; pendingItems = Array.from(selectedItems).map(id => ({ id, password: null })); await selectDestination(); }

async function selectDestination() {
    const folders = allFiles.filter(f => (f.driveType === 'ROOT' || f.driveType === 'FOLDER') && !pendingItems.map(p => p.id).includes(f.id));
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

    let successCount = 0, failCount = 0;
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
            successCount++;
        } catch (error) {
            failCount++;
        }
    }
    const completedAction = pendingAction;
    closeModal('moveModal');
    if (pendingAction === 'move') selectedItems.clear();
    await loadFiles();
    pendingItems = [];
    pendingAction = null;

    if (successCount > 0) {
        const msg = `${completedAction === 'copy' ? 'Copied' : 'Moved'} ${successCount} item(s) successfully. ${failCount > 0 ? `Failed to ${completedAction} ${failCount} item(s).` : ''}`;
        safeToast(msg, failCount > 0 ? 'warning' : 'success');
    } else if (failCount > 0) {
        safeToast(`Failed to ${completedAction} ${failCount} item(s). Please try again.`, 'error');
    }
}

window.viewFile = viewFile;
window.downloadFile = downloadFile;
window.deleteItem = deleteItem;
window.deleteSelected = deleteSelected;
window.downloadSelected = downloadSelected;
window.copyItem = copyItem;
window.moveItem = moveItem;
window.copySelected = copySelected;
window.moveSelected = moveSelected;
window.executeMove = executeMove;
window.selectDestination = selectDestination;