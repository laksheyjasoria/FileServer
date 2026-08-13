// sharedByMe.js - Logic for "Shared by me" page

// 🛡️ Embedded helpers to guarantee no "undefined function" crashes
function escapeJSString(str) {
    return (str || '').replace(/'/g, "\\'");
}
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
function formatFileSize(bytes) {
    if (!bytes) return '0 B';
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${sizes[i]}`;
}
function getFileIcon(filename) {
    const ext = filename.split('.').pop().toLowerCase();
    const icons = {
        'pdf': '📄', 'doc': '📝', 'docx': '📝', 'xls': '📊', 'xlsx': '📊',
        'ppt': '📽️', 'pptx': '📽️', 'jpg': '🖼️', 'jpeg': '🖼️', 'png': '🖼️',
        'mp4': '🎥', 'mp3': '🎵', 'zip': '📦', 'txt': '📃', 'html': '🌐',
        'css': '🎨', 'js': '📜', 'json': '📋', 'xml': '📋'
    };
    return icons[ext] || '📎';
}

// Use global showToast if available, else fallback to alert
function safeToast(message, type = 'info', duration = 3000) {
    if (typeof showToast === 'function') {
        showToast(message, type, duration);
    } else {
        alert(message);
    }
}

document.addEventListener('DOMContentLoaded', () => {
    console.log("✅ sharedByMe.js loaded. Starting loadSharedByMe()...");
    loadSharedByMe();
});

async function loadSharedByMe() {
    const container = document.getElementById('fileContainer');
    if (!container) {
        console.error("❌ CRITICAL: 'fileContainer' not found in DOM!");
        safeToast('Page error: file container not found.', 'error');
        return;
    }

    showLoading(container, true);
    document.getElementById('breadcrumb').innerHTML = '<span class="breadcrumb-item">Shared by me</span>';

    try {
        console.log("📡 Calling /share/shared-by-me...");
        const response = await apiCall('/share/shared-by-me');
        
        if (!response.ok) {
            throw new Error(`Server returned ${response.status}`);
        }

        const shares = await response.json();
        console.log("📦 Shares received:", shares);

        if (shares && shares.length > 0) {
            renderSharedByMe(shares);
            safeToast(`Loaded ${shares.length} shared item(s).`, 'info', 2000);
        } else {
            showEmptyState(container, 'You have not created any shares yet', '');
            safeToast('No shares created yet.', 'info', 2000);
        }
    } catch (error) {
        console.error("❌ Error in loadSharedByMe():", error);
        showError(container);
        safeToast('Failed to load shared items: ' + error.message, 'error');
    }
}

function renderSharedByMe(shares) {
    const container = document.getElementById('fileContainer');
    let html = '<div class="file-grid">';

    shares.forEach(item => {
        const isFolder = item.driveType === 'FOLDER' || item.driveType === 'MULTI';
        const icon = isFolder ? '📁' : getFileIcon(item.driveName);
        const expiry = item.expiresAt ? new Date(item.expiresAt).toLocaleDateString() : 'Never';

        html += `
            <div class="file-item">
                <div class="file-icon">${icon}</div>
                <div class="file-name">${escapeHtml(item.driveName)}</div>
                <div class="file-info">
                    ${item.shareType} • Expires: ${expiry}
                </div>
                <div class="file-menu" onclick="showSharedByMeContextMenu(event, '${escapeJSString(item.token)}', '${escapeJSString(item.driveName)}')">
                    ⋮
                </div>
            </div>
        `;
    });

    html += '</div>';
    container.innerHTML = html;
}

// Context menu for "Shared by me"
function showSharedByMeContextMenu(event, token, name) {
    event.stopPropagation();
    const existingMenu = document.querySelector('.dropdown-menu');
    if (existingMenu) existingMenu.remove();

    const menu = document.createElement('div');
    menu.className = 'dropdown-menu show';
    menu.style.position = 'absolute';
    menu.style.top = `${event.clientY}px`;
    menu.style.left = `${event.clientX}px`;

    const items = [
        { icon: '🔗', label: 'Copy Link', action: () => copyShareLink(token) },
        { icon: '🗑️', label: 'Delete Share', action: () => deleteShare(token) }
    ];

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

async function copyShareLink(token) {
    try {
        await navigator.clipboard.writeText(`${window.location.origin}/share2.html?token=${token}`);
        safeToast('Link copied to clipboard!', 'success');
    } catch (err) {
        safeToast('Failed to copy link.', 'error');
    }
}

async function deleteShare(token) {
    // Native confirm (since no modal system)
    if (!confirm('Are you sure you want to delete this share?')) return;
    try {
        const response = await apiCall(`/share/${token}`, { method: 'DELETE' });
        if (!response.ok) throw new Error('Delete failed');
        safeToast('Share deleted successfully.', 'success');
        await loadSharedByMe();
    } catch (error) {
        safeToast('Failed to delete share: ' + error.message, 'error');
    }
}