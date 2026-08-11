// js2/navigation.js - Shared Sidebar Navigation Logic

function setupSidebarNavigation() {
    document.querySelectorAll('.sidebar-item').forEach(item => {
        item.addEventListener('click', () => {
            // Handle Sign Out
            if (item.id === 'signOutBtn') {
                localStorage.removeItem('jwtToken');
                window.location.href = '/login.html';
                return;
            }

            // Remove active from all, set active to clicked
            document.querySelectorAll('.sidebar-item').forEach(i => i.classList.remove('active'));
            item.classList.add('active');
            const currentView = item.dataset.view;

            // 🔹 Redirect My Drive to Index
            if (currentView === 'my-drive') {
                window.location.href = '/index.html';
                return;
            }

            // 🔹 Redirect Profile to Profile
            if (currentView === 'profile') {
                window.location.href = '/profile.html';
                return;
            }

            // For Shared / Recent, fallback to Index
            window.location.href = '/index.html';
        });
    });
}