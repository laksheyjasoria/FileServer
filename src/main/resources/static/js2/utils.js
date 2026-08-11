// Utility Functions (static2)
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

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showModal(modalId) {
    document.getElementById(modalId).classList.add('active');
}

function closeModal(modalId) {
    document.getElementById(modalId).classList.remove('active');
}

function showLoading(container, show) {
    if (show) {
        container.innerHTML = '<div class="loading">Loading...</div>';
    }
}

function showEmptyState(container, message = 'This folder is empty', subtitle = 'Click "Upload File" or "New Folder" to add items') {
    const subtitleHtml = subtitle ? `<div style="font-size: 12px; margin-top: 8px;">${escapeHtml(subtitle)}</div>` : '';
    container.innerHTML = `
        <div class="empty-state">
            <div class="empty-state-icon">📁</div>
            <div>${escapeHtml(message)}</div>
            ${subtitleHtml}
        </div>
    `;
}

function showError(container, message = 'Error loading files') {
    container.innerHTML = `<div class="empty-state"><div class="empty-state-icon">❌</div><div>${message}</div></div>`;
}

function showDownloadOverlay(message = 'Preparing your download...') {
    let overlay = document.getElementById('downloadOverlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'downloadOverlay';
        overlay.style.cssText = 'display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0, 0, 0, 0.6); z-index: 9999; align-items: center; justify-content: center; flex-direction: column;';
        overlay.innerHTML = `
            <div class="spinner" style="width: 50px; height: 50px; border: 4px solid #f3f3f3; border-top: 4px solid #1a73e8; border-radius: 50%; animation: spin 1s linear infinite;"></div>
            <p style="color: white; margin-top: 20px; font-size: 18px; font-weight: 500;">${message}</p>
        `;
        document.body.appendChild(overlay);
    }
    overlay.style.display = 'flex';
}

function hideDownloadOverlay() {
    const overlay = document.getElementById('downloadOverlay');
    if (overlay) {
        overlay.style.display = 'none';
    }
}