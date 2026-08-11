// navigation.js - Updated sidebar navigation logic
function setupSidebarNavigation() {
    document.querySelectorAll('.sidebar-item').forEach(item => {
        item.addEventListener('click', () => {
            if (item.id === 'signOutBtn') {
                localStorage.removeItem('jwtToken');
                window.location.href = '/login.html';
                return;
            }

            document.querySelectorAll('.sidebar-item').forEach(i => i.classList.remove('active'));
            item.classList.add('active');
            const view = item.dataset.view;

            if (view === 'my-drive') {
                window.location.href = '/index.html';
                return;
            }
            if (view === 'profile') {
                window.location.href = '/profile.html';
                return;
            }
            if (view === 'shared') {
                window.location.href = '/shared.html';
                return;
            }
            if (view === 'shared-by-me') {
                window.location.href = '/shared-by-me.html';
                return;
            }
            // Fallback
            window.location.href = '/index.html';
        });
    });
}