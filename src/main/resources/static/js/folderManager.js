// Folder Operations
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
        let url;
        let body = {
            name: name,
            accessType: isPrivate ? 'PROTECTED' : 'PUBLIC'
        };

        if (isPrivate && password) {
            body.password = password;
        }

        if (currentFolderId) {
            url = `/drive/${currentFolderId}/folders`;
        } else {
            url = `/drive/root`;
        }

        const response = await fetch(`${API_URL}${url}`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${jwtToken}`,
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(body)
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`Server responded with ${response.status}: ${errorText}`);
        }

        const result = await response.json();

        if (isPrivate) {
            alert(`Folder "${name}" created successfully with password protection.\n\nPassword: ${password}\n\nKeep this password safe to access the folder later.`);
        } else {
            alert(`Folder "${name}" created successfully.`);
        }

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
        await apiCall(`/drive/${contextMenuItem.id}`, {
            method: 'PUT',
            body: JSON.stringify({ name: newName, accessType: 'PUBLIC' })
        });
        closeModal('renameModal');
        await loadFiles();
    } catch (error) {
        console.error('Error renaming:', error);
        alert('Failed to rename: ' + error.message);
    }
}

async function copyItem(id) {
    try {
        const response = await apiCall(`/drive/${id}`);
        const item = await response.json();

        let password = null;
        if (item.accessType === 'PROTECTED') {
            password = prompt(`"${item.name}" is password protected. Enter password to copy it:`);
            if (password === null) return;
            if (!password) {
                alert('Password is required to copy this protected item');
                return;
            }
        }

        pendingAction = 'copy';
        pendingItems = [{ id, password }];
        await selectDestination();
    } catch (error) {
        console.error('Error checking item:', error);
        alert('Failed to check item status');
    }
}

async function moveItem(id) {
    try {
        const response = await apiCall(`/drive/${id}`);
        const item = await response.json();

        let password = null;
        if (item.accessType === 'PROTECTED') {
            password = prompt(`"${item.name}" is password protected. Enter password to move it:`);
            if (password === null) return;
            if (!password) {
                alert('Password is required to move this protected item');
                return;
            }
        }

        pendingAction = 'move';
        pendingItems = [{ id, password }];
        await selectDestination();
    } catch (error) {
        console.error('Error checking item:', error);
        alert('Failed to check item status');
    }
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
            let url = '';
            // Use generic resources action endpoint
            if (pendingAction === 'copy') {
                await apiCall('/resources/action', {
                    method: 'POST',
                    body: JSON.stringify({ action: 'COPY', ids: [item.id], destination: destinationId })
                });
            } else {
                await apiCall('/resources/action', {
                    method: 'POST',
                    body: JSON.stringify({ action: 'MOVE', ids: [item.id], destination: destinationId })
                });
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
        alert(`Failed to ${pendingAction} ${failCount} item(s). Please check passwords and try again.`);
    }
}