// contextMenu.js – Context menus (if you want to separate from app.js)

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

async function downloadSingleFolder(folderId) {
    safeToast('Preparing folder download...', 'info', 2000);
    try {
        const response = await apiCall('/download/bulk', {
            method: 'POST',
            body: JSON.stringify([folderId])
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
        safeToast('Folder downloaded successfully.', 'success');
    } catch (error) {
        safeToast('Failed to download folder: ' + error.message, 'error');
    }
}

function showSharedContextMenu(event, token, name, type) {
    event.stopPropagation();
    const existingMenu = document.querySelector('.dropdown-menu');
    if (existingMenu) existingMenu.remove();

    const menu = document.createElement('div');
    menu.className = 'dropdown-menu show';
    menu.style.position = 'absolute';
    menu.style.top = `${event.clientY}px`;
    menu.style.left = `${event.clientX}px`;

    let items = [];
    if (type === 'FILE') {
        items = [
            { icon: '👁️', label: 'View', action: () => openSharedItem(token) },
            { icon: '⬇️', label: 'Download', action: () => downloadSharedFile(token) }
        ];
    } else {
        items = [
            { icon: '📂', label: 'Open', action: () => openSharedItem(token) }
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

function downloadSharedFile(token) {
    safeToast('Starting download...', 'info', 1500);
    window.location.href = `/share/download/${token}`;
}

function openSharedItem(token) {
    window.open(`/share2.html?token=${encodeURIComponent(token)}`, '_blank');
}