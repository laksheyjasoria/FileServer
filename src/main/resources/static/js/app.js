// Main Application Initialization
document.addEventListener('DOMContentLoaded', async () => {
    // Check authentication
    if (!jwtToken) {
        window.location.href = '/login.html';
        return;
    }
    
    // Setup event listeners
    setupEventListeners();
    
    // Load user info
    await loadUserInfo();
    
    // Load files
    await loadFiles();
    
    // Setup sidebar navigation
    setupSidebarNavigation();
    
    // Setup search
    setupSearch();
});

function setupEventListeners() {
    const newFolderBtn = document.getElementById('newFolderBtn');
    const uploadBtn = document.getElementById('uploadBtn');
    const fileInput = document.getElementById('fileInput');
    const closeUploadQueueBtn = document.getElementById('closeUploadQueueBtn');
    const createFolderBtn = document.getElementById('createFolderBtn');
    const shareType = document.getElementById('shareType');
    
    if (newFolderBtn) newFolderBtn.addEventListener('click', () => showCreateFolderModal());
    if (uploadBtn) uploadBtn.addEventListener('click', () => fileInput.click());
    if (closeUploadQueueBtn) closeUploadQueueBtn.addEventListener('click', () => {
        document.getElementById('uploadQueue').style.display = 'none';
    });
    if (createFolderBtn) createFolderBtn.addEventListener('click', createFolder);
    if (fileInput) fileInput.addEventListener('change', (e) => addToUploadQueue(e.target.files));
    if (shareType) shareType.addEventListener('change', toggleShareOptions);
}

function setupSidebarNavigation() {
    document.querySelectorAll('.sidebar-item').forEach(item => {
        item.addEventListener('click', () => {
            document.querySelectorAll('.sidebar-item').forEach(i => i.classList.remove('active'));
            item.classList.add('active');
            currentView = item.dataset.view;
            currentFolderId = null;
            selectedItems.clear();
            loadFiles();
        });
    });
}

function setupSearch() {
    const searchInput = document.getElementById('searchInput');
    if (searchInput) {
        searchInput.addEventListener('input', (e) => {
            const searchTerm = e.target.value.toLowerCase();
            const filtered = allFiles.filter(f => f.name.toLowerCase().includes(searchTerm));
            renderFiles(filtered);
        });
    }
}