// shared.js - Logic for the Shared with me page
console.log("✅ shared.js loaded successfully!");

function escapeJSString(str) {
    return (str || '').replace(/'/g, "\\'");
}

document.addEventListener('DOMContentLoaded', () => {
    console.log("✅ DOM fully loaded. Starting loadSharedItems()...");
    loadSharedItems();
});

async function loadSharedItems() {
    console.log("🔄 Entered loadSharedItems() function...");
    
    const container = document.getElementById('fileContainer');
    if (!container) {
        console.error("❌ CRITICAL: 'fileContainer' element not found in the DOM!");
        return;
    }

    showLoading(container, true);
    document.getElementById('breadcrumb').innerHTML = '<span class="breadcrumb-item">Shared with me</span>';

    try {
        console.log("📡 Checking if apiCall exists...");
        if (typeof apiCall !== 'function') {
            throw new Error("apiCall is not defined. Make sure api.js loads before shared.js!");
        }
        
        console.log("📡 Calling /share/shared-with-me...");
        const response = await apiCall('/share/shared-with-me');
        console.log("📡 API Response status:", response.status);

        if (!response.ok) {
            console.error("❌ API Error status:", response.status);
            throw new Error('API Error');
        }

        const shares = await response.json();
        console.log("📦 Shares received from API:", shares);

        if (shares && shares.length > 0) {
            renderSharedFiles(shares);
        } else {
            console.log("ℹ️ API returned empty list.");
            showEmptyState(container, 'No items shared with you', '');
        }
    } catch (error) {
        console.error("❌ Error caught in loadSharedItems():", error);
        showError(container);
    }
}

function renderSharedFiles(shares) {
    console.log("🖼️ Rendering shared files...");
    const container = document.getElementById('fileContainer');
    let html = '<div class="file-grid">';

    shares.forEach(item => {
        const isFolder = item.driveType === 'FOLDER' || item.driveType === 'MULTI';
        const icon = isFolder ? '📁' : getFileIcon(item.driveName);
        
        let infoText = `Shared by ${escapeHtml(item.createdBy)}`;
        if (!isFolder && item.fileSize > 0) {
            infoText = `${formatFileSize(item.fileSize)} • ${infoText}`;
        } else if (isFolder) {
            infoText = `Folder • ${infoText}`;
        }

        html += `
            <div class="file-item" onclick="handleSharedItemClick(event, '${escapeJSString(item.token)}')" ondblclick="openSharedItem('${escapeJSString(item.token)}')">
                <div class="file-icon">${icon}</div>
                <div class="file-name">${escapeHtml(item.driveName)}</div>
                <div class="file-info">${infoText}</div>
                <div class="file-menu" onclick="showSharedContextMenu(event, '${escapeJSString(item.token)}', '${escapeJSString(item.driveName)}', '${item.driveType}')">
                    ⋮
                </div>
            </div>
        `;
    });

    html += '</div>';
    container.innerHTML = html;
    console.log("✅ Rendering complete.");
}

function handleSharedItemClick(event, token) {
    if (event.target.classList.contains('file-menu')) return;
}

function openSharedItem(token) {
   window.open(`/share2.html?token=${encodeURIComponent(token)}`, '_blank');
}

// Rest of context menu and helper functions remain the same...
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
    window.location.href = `/share/download/${token}`;
}