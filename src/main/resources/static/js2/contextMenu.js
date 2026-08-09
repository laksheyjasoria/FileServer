// Context Menu (copied to static2)
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
			{ icon: '⬇️', label: 'Download', action: () => downloadSingleFolder(id) },
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
    // Use the bulk endpoint with only this folder ID
    showDownloadOverlay('Preparing folder download...'); // Add loading
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
    } catch (error) {
        console.error('Error downloading folder:', error);
        alert('Failed to download folder: ' + error.message);
    } finally {
        hideDownloadOverlay(); // Ensure loading hides even on error
    }
}

