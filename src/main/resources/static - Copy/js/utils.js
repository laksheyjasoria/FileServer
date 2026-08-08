// Utility Functions
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

function showEmptyState(container, message = 'This folder is empty') {
    container.innerHTML = `
        <div class="empty-state">
            <div class="empty-state-icon">📁</div>
            <div>${message}</div>
            <div style="font-size: 12px; margin-top: 8px;">Click "Upload File" or "New Folder" to add items</div>
        </div>
    `;
}

function showError(container, message = 'Error loading files') {
    container.innerHTML = `<div class="empty-state"><div class="empty-state-icon">❌</div><div>${message}</div></div>`;
}