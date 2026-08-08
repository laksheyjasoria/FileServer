// Folder Operations (static2) - backend compatible
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

function showCreateFolderModal() {
    resetFolderModal();
    showModal('folderModal');
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
        // Call backend resources action to create a folder
        const payload = {
            action: 'CREATE_FOLDER',
            ids: [],
            destination: currentFolderId || null,
            name: name
        };

        const response = await apiCall('/resources/action', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || 'Failed to create folder');
        }

        alert(`Folder "${name}" created successfully.`);

    } catch (error) {
        console.error('Error creating folder:', error);
        alert('Failed to create folder: ' + error.message);
    } finally {
        createBtn.textContent = originalText;
        createBtn.disabled = false;
    }
}

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
        // Use /resources/action endpoint for rename
        const response = await apiCall('/resources/action', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                ids: [contextMenuItem.id],
                action: 'RENAME',
                name: newName
            })
        });

        if (!response.ok) {
            throw new Error('Rename failed');
        }

        closeModal('renameModal');
        await loadFiles();
    } catch (error) {
        console.error('Error renaming:', error);
        alert('Failed to rename: ' + error.message);
    }
}

async function copyItem(id) {
    // Backend would need this endpoint to be available
    // For now, just select for copy operation
    pendingAction = 'copy';
    pendingItems = [{ id, password: null }];
    await selectDestination();
}

async function moveItem(id) {
    // Backend would need this endpoint to be available  
    // For now, just select for move operation
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
    const destinationId = destId ? parseInt(destId) : null;

    let successCount = 0;
    let failCount = 0;

    for (const item of pendingItems) {
        try {
            // Use /resources/action endpoint for move/copy operations
            const response = await apiCall('/resources/action', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    ids: [item.id],
                    action: pendingAction === 'copy' ? 'COPY' : 'MOVE',
                    destination: destinationId
                })
            });

            if (!response.ok) {
                throw new Error(`${pendingAction} failed`);
            }

            successCount++;
        } catch (error) {
            console.error(`Error ${pendingAction}ing item:`, error);
            failCount++;
        }
    }

    closeModal('moveModal');
    if (pendingAction === 'move') selectedItems.clear();
    await loadFiles();
    pendingItems = [];
    pendingAction = null;

    if (successCount > 0) {
        alert(`${pendingAction === 'copy' ? 'Copied' : 'Moved'} ${successCount} item(s) successfully. ${failCount > 0 ? `Failed to ${pendingAction} ${failCount} item(s).` : ''}`);
    } else if (failCount > 0) {
        alert(`Failed to ${pendingAction} ${failCount} item(s). Please try again.`);
    }
}
