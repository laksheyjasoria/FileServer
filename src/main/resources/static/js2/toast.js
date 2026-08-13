// toast.js – Global Toast Notification System
// Automatically creates the container if it doesn't exist.

(function() {
    // 1. Create container if missing
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        document.body.appendChild(container);
    }

    // 2. Icon mapping
    const ICONS = {
        success: '✅',
        warning: '⚠️',
        error: '❌',
        info: 'ℹ️'
    };

    /**
     * Show a toast notification.
     * @param {string} message   - The message to display.
     * @param {string} type      - 'success', 'warning', 'error', or 'info' (default: 'info').
     * @param {number} duration  - Auto‑hide delay in ms (default: 3500). Set 0 to keep until closed.
     * @returns {HTMLElement} The toast element.
     */
    window.showToast = function(message, type = 'info', duration = 3500) {
        const icon = ICONS[type] || 'ℹ️';
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.innerHTML = `
            <span class="toast-icon">${icon}</span>
            <span class="toast-message">${escapeHtml(message)}</span>
            <button class="toast-close" aria-label="Close">&times;</button>
        `;

        container.appendChild(toast);

        // Show with animation
        requestAnimationFrame(() => {
            toast.classList.add('show');
        });

        // Close button
        const closeBtn = toast.querySelector('.toast-close');
        closeBtn.addEventListener('click', () => removeToast(toast));

        // Auto‑remove
        if (duration > 0) {
            const timer = setTimeout(() => removeToast(toast), duration);
            toast._timer = timer;
        }

        return toast;
    };

    // Remove toast with fade-out animation
    function removeToast(toast) {
        if (!toast || !toast.parentNode) return;
        toast.classList.remove('show');
        toast.addEventListener('transitionend', () => {
            if (toast.parentNode) toast.remove();
        });
        if (toast._timer) {
            clearTimeout(toast._timer);
            toast._timer = null;
        }
    }

    // Simple HTML escape to prevent XSS
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
})();