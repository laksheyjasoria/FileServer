// navigation.js – Dynamic sidebar (fixed trash navigation)

function setupSidebarNavigation() {
    const user = getUserFromToken();
    const isAdmin = user && user.role === 'ADMIN';
    const sidebar = document.querySelector('.sidebar');
    if (!sidebar) return;

    const currentPath = window.location.pathname;

    const items = [
        { view: 'my-drive', icon: '📁', label: 'My Drive', href: '/index.html' },
        { view: 'shared', icon: '👥', label: 'Shared with me', href: '/shared.html' },
        { view: 'shared-by-me', icon: '↗️', label: 'Shared by me', href: '/shared-by-me.html' },
        { view: 'profile', icon: '👤', label: 'My Profile', href: '/profile.html' }
    ];
    if (isAdmin) {
        items.push({ view: 'master', icon: '🏢', label: 'Master Drives', href: '/master.html' });
        items.push({ view: 'logger', icon: '📝', label: 'Loggers', href: '/logger.html' });
		items.push({ view: 'users', icon: '👥', label: 'Users', href: '/admin-users.html' });
    }
    items.push({ view: 'trash', icon: '🗑️', label: 'Trash', href: '/trash.html' });
    items.push({ view: 'signout', icon: '↩', label: 'Sign Out', id: 'signOutBtn' });

    sidebar.innerHTML = '';
    items.forEach(item => {
        const div = document.createElement('div');
        div.className = 'sidebar-item';
        if (item.id) div.id = item.id;
        div.dataset.view = item.view;
        div.dataset.href = item.href || '';
        div.innerHTML = `<span class="icon">${item.icon}</span><span>${item.label}</span>`;
        sidebar.appendChild(div);
    });

    // Highlight active page
    document.querySelectorAll('.sidebar-item').forEach(el => {
        const href = el.dataset.href;
        if (href && (currentPath === href || currentPath === href.replace('.html', ''))) {
            el.classList.add('active');
        }
    });

    // Click handler – avoid reloading the same page
    document.querySelectorAll('.sidebar-item').forEach(item => {
        item.addEventListener('click', () => {
            if (item.id === 'signOutBtn') {
                safeToast('Signing out...', 'info', 1500);
                localStorage.removeItem('jwtToken');
                setTimeout(() => window.location.href = '/login.html', 500);
                return;
            }

            const target = item.dataset.href;
            if (target) {
                // ✅ Prevent reload if we are already on that page
                if (window.location.pathname === target || window.location.pathname === target.replace('.html', '')) {
                    return; // already here, do nothing
                }
                window.location.href = target;
            } else {
                // fallback
                const view = item.dataset.view;
                if (view === 'my-drive') window.location.href = '/index.html';
                else if (view === 'shared') window.location.href = '/shared.html';
                else if (view === 'shared-by-me') window.location.href = '/shared-by-me.html';
                else if (view === 'profile') window.location.href = '/profile.html';
                else if (view === 'master') window.location.href = '/master.html';
                else if (view === 'logger') window.location.href = '/logger.html';
                else if (view === 'trash') window.location.href = '/trash.html';
            }
        });
    });

    loadUserInfoIntoUI();
}

document.addEventListener('DOMContentLoaded', setupSidebarNavigation);