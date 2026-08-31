// utils.js – Centralized utilities (globally available)

// ===== HTML/JS ESCAPE =====
window.escapeHtml = function(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
};

window.escapeJSString = function(str) {
    return (str || '').replace(/'/g, "\\'");
};

// ===== FILE HELPERS =====
window.formatFileSize = function(bytes) {
    if (!bytes) return '0 B';
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${sizes[i]}`;
};

window.getFileIcon = function(filename) {
    const ext = filename.split('.').pop().toLowerCase();
    const icons = {
        'pdf': '📄', 'doc': '📝', 'docx': '📝', 'xls': '📊', 'xlsx': '📊',
        'ppt': '📽️', 'pptx': '📽️', 'jpg': '🖼️', 'jpeg': '🖼️', 'png': '🖼️',
        'mp4': '🎥', 'mp3': '🎵', 'zip': '📦', 'txt': '📃', 'html': '🌐',
        'css': '🎨', 'js': '📜', 'json': '📋', 'xml': '📋'
    };
    return icons[ext] || '📎';
};

// ===== UI STATE HELPERS =====
window.showLoading = function(container, show) {
    if (show) container.innerHTML = '<div class="loading">Loading...</div>';
};

window.showEmptyState = function(container, message = 'This folder is empty', subtitle = '') {
    const subtitleHtml = subtitle ? `<div style="font-size:12px;margin-top:8px;">${escapeHtml(subtitle)}</div>` : '';
    container.innerHTML = `
        <div class="empty-state">
            <div class="empty-state-icon">📁</div>
            <div>${escapeHtml(message)}</div>
            ${subtitleHtml}
        </div>
    `;
};

window.showError = function(container, message = 'Error loading files') {
    container.innerHTML = `<div class="empty-state"><div class="empty-state-icon">❌</div><div>${message}</div></div>`;
};

// ===== MODAL HELPERS =====
window.showModal = function(id) {
    document.getElementById(id).classList.add('active');
};

window.closeModal = function(id) {
    document.getElementById(id).classList.remove('active');
};

// ===== TOAST FALLBACK =====
window.safeToast = function(message, type = 'info', duration = 3000) {
    if (typeof showToast === 'function') showToast(message, type, duration);
    else alert(message);
};

// ===== DOWNLOAD OVERLAY =====
window.showDownloadOverlay = function(message = 'Preparing your download...') {
    let overlay = document.getElementById('downloadOverlay');
    if (!overlay) {
        overlay = document.createElement('div');
        overlay.id = 'downloadOverlay';
        overlay.innerHTML = `
            <div class="spinner"></div>
            <p>${message}</p>
        `;
        document.body.appendChild(overlay);
    } else {
        overlay.querySelector('p').textContent = message;
    }
    overlay.style.display = 'flex';
};

window.hideDownloadOverlay = function() {
    const overlay = document.getElementById('downloadOverlay');
    if (overlay) overlay.style.display = 'none';
};

// ===== USER INFO INTO TOP BAR =====
window.loadUserInfoIntoUI = function() {
    const user = getUserFromToken();
    const emailEl = document.getElementById('userEmail');
    const avatarEl = document.getElementById('userAvatar');
    const defaultAvatar = '/assets/img/default_avatar.jpg';

    if (user && user.email) {
        if (emailEl) emailEl.textContent = user.email;

        if (avatarEl) {
            const hasValidPhoto = user.photoUrl && user.photoUrl !== 'null' && user.photoUrl !== 'undefined';
            if (hasValidPhoto) {
                avatarEl.style.backgroundImage = `url(${API_URL}/auth/profile/photo?${new Date().getTime()})`;
                avatarEl.style.backgroundSize = 'cover';
                avatarEl.textContent = '';
            } else {
                avatarEl.style.backgroundImage = `url(${defaultAvatar})`;
                avatarEl.style.backgroundSize = 'cover';
                avatarEl.textContent = '';
            }
        }
    } else {
        if (emailEl) emailEl.textContent = '';
        if (avatarEl) {
            avatarEl.style.backgroundImage = `url(${defaultAvatar})`;
            avatarEl.style.backgroundSize = 'cover';
            avatarEl.textContent = '';
        }
    }
};

// ================================
// DEBOUNCE HELPER
// ================================
window.debounce = function(func, wait) {
    let timeout;
    return function(...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), wait);
    };
};

// ================================
// ESCAPE FOR JAVASCRIPT STRINGS
// ================================
window.escapeJS = function(str) {
    if (!str) return '';
    return str.replace(/\\/g, '\\\\')
              .replace(/'/g, "\\'")
              .replace(/"/g, '\\"')
              .replace(/\n/g, '\\n')
              .replace(/\r/g, '\\r');
};