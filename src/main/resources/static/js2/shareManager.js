// Share Operations (static2)
let currentShareItem = null;
let selectedAllowedUsers = []; // Array to store selected { email, name, photoUrl }

// Helper: Use global toast or fallback to alert
function safeToast(message, type = 'info', duration = 3000) {
    if (typeof showToast === 'function') {
        showToast(message, type, duration);
    } else {
        alert(message);
    }
}

function showShareModal(ids, name) {
    currentShareItem = { ids, name };
    selectedAllowedUsers = [];

    document.getElementById('shareType').value = 'PUBLIC';
    document.getElementById('sharePermission').value = 'VIEW_DOWNLOAD';
    document.getElementById('expiresAt').value = '';
    document.getElementById('sharePassword').value = '';
    
    // Reset and clear the user tag input
    const tagsList = document.getElementById('emailTagsList');
    const searchInput = document.getElementById('allowedUsersSearchInput');
    if (tagsList) tagsList.innerHTML = '';
    if (searchInput) searchInput.value = '';
    
    // Ensure dropdown is hidden when opening the modal
    const dropdown = document.getElementById('emailSuggestionsDropdown');
    if (dropdown) dropdown.style.display = 'none';

    toggleShareOptions();
    showModal('shareModal');
}

function shareSelected() {
    const ids = Array.from(selectedItems);
    if (ids.length === 0) {
        safeToast('Please select at least one item to share.', 'warning');
        return;
    }

    if (ids.length === 1) {
        const item = allFiles.find(f => f.id === ids[0]);
        if (item) showShareModal(ids, item.name);
    } else {
        showShareModal(ids, `${ids.length} items`);
    }
}

function toggleShareOptions() {
    const type = document.getElementById('shareType').value;
    document.getElementById('passwordOption').style.display = type === 'PROTECTED' ? 'block' : 'none';
    document.getElementById('usersOption').style.display = type === 'USER_ONLY' ? 'block' : 'none';

    // If switching to USER_ONLY, clear previous selections and focus the search input
    if (type === 'USER_ONLY') {
        const searchInput = document.getElementById('allowedUsersSearchInput');
        if (searchInput) setTimeout(() => searchInput.focus(), 100);
    }
}

// ----- EMAIL TAG & AUTOCOMPLETE LOGIC -----

// Debounce utility to prevent excessive API calls while typing
function debounce(func, timeout = 300) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => func.apply(this, args), timeout);
    };
}

const handleSearchInput = debounce(async (e) => {
    const query = e.target.value.trim();
    const dropdown = document.getElementById('emailSuggestionsDropdown');
    
    if (query.length < 1) {
        dropdown.style.display = 'none';
        return;
    }

    try {
        // Call the backend search API
        const response = await fetch(`${API_URL}/api/users/search?q=${encodeURIComponent(query)}`, {
            headers: { 'Authorization': 'Bearer ' + localStorage.getItem('jwtToken') }
        });

        if (!response.ok) throw new Error('Search failed');

        const users = await response.json();
        renderSuggestions(users);

    } catch (error) {
        console.error('Error searching users:', error);
        dropdown.style.display = 'none';
        safeToast('Failed to search users: ' + error.message, 'error');
    }
}, 300);

function renderSuggestions(users) {
    const dropdown = document.getElementById('emailSuggestionsDropdown');
    dropdown.innerHTML = '';
    
    // 1. Filter out already selected users
    const filteredUsers = users.filter(user => 
        !selectedAllowedUsers.some(sel => sel.email === user.email)
    );

    // 2. Always show dropdown, even for empty results
    dropdown.style.display = 'block';

    if (filteredUsers.length === 0) {
        dropdown.innerHTML = `<div class="no-results">No registered users found</div>`;
        return;
    }

    filteredUsers.forEach(user => {
        const item = document.createElement('div');
        item.className = 'suggestion-item';
        
        const avatarHtml = user.photoUrl 
            ? `<img src="${user.photoUrl}" class="suggestion-avatar" onerror="this.src='/assets/img/default_avatar.jpg'" />`
            : `<div class="suggestion-avatar initials">${(user.name || user.email).charAt(0).toUpperCase()}</div>`;

        item.innerHTML = `
            ${avatarHtml}
            <div class="suggestion-details">
                <div class="suggestion-name">${escapeHtml(user.name || user.email)}</div>
                <div class="suggestion-email">${escapeHtml(user.email)}</div>
            </div>
        `;

        item.onclick = () => addUserTag(user);
        dropdown.appendChild(item);
    });
}

function addUserTag(user) {
    // Prevent adding duplicate emails
    if (selectedAllowedUsers.some(u => u.email === user.email)) return;

    selectedAllowedUsers.push(user);

    const tagsList = document.getElementById('emailTagsList');
    const tag = document.createElement('div');
    tag.className = 'email-tag';
    tag.dataset.email = user.email; // Safe removal tracking

    const avatarHtml = user.photoUrl 
        ? `<img src="${user.photoUrl}" class="tag-avatar" onerror="this.src='/assets/img/default_avatar.jpg'" />`
        : `<div class="tag-avatar initials">${(user.name || user.email).charAt(0).toUpperCase()}</div>`;

    tag.innerHTML = `
        ${avatarHtml}
        <span class="tag-text">${escapeHtml(user.name || user.email)}</span>
        <span class="tag-remove" onclick="removeUserTag('${escapeHtml(user.email)}')">×</span>
    `;
    tagsList.appendChild(tag);

    // Clear search input and hide dropdown
    document.getElementById('allowedUsersSearchInput').value = '';
    document.getElementById('emailSuggestionsDropdown').style.display = 'none';
}

function removeUserTag(email) {
    selectedAllowedUsers = selectedAllowedUsers.filter(u => u.email !== email);
    const tagToRemove = document.querySelector(`.email-tag[data-email="${escapeHtml(email)}"]`);
    if (tagToRemove) tagToRemove.remove();
}

// ----- CREATE SHARE LINK (Final Submission) -----
async function createShareLink() {
    const shareType = document.getElementById('shareType').value;
    const permission = document.getElementById('sharePermission').value;
    const expiresAt = document.getElementById('expiresAt').value || null;
    const password = document.getElementById('sharePassword').value;

    let allowedUsers = [];
    if (shareType === 'USER_ONLY') {
        // Extract emails from the selected tags
        allowedUsers = selectedAllowedUsers.map(u => u.email);
        if (allowedUsers.length === 0) {
            safeToast('Please select at least one registered user to share with.', 'warning');
            return;
        }
    }

    if (shareType === 'PROTECTED' && !password) {
        safeToast('Password is required for protected share', 'warning');
        return;
    }

    try {
        const isMulti = Array.isArray(currentShareItem.ids) && currentShareItem.ids.length > 1;
        let endpoint = isMulti ? '/share/multi' : '/share';
        let body = {
            publicAccess: shareType === 'PUBLIC',
            password: password || null,
            expiry: expiresAt || null,
            permission: permission,
            allowedUsers: allowedUsers // Send the filtered list of emails
        };

        if (isMulti) body.fileIds = currentShareItem.ids;
        else body.fileId = currentShareItem.ids[0];

        const response = await apiCall(endpoint, { method: 'POST', body: JSON.stringify(body) });
        const result = await response.json();
        const shareUrl = `${window.location.origin}/share2.html?token=${result.token || result.shareToken || ''}`;
        
        document.getElementById('shareLink').value = shareUrl;
        closeModal('shareModal');
        showModal('shareLinkModal');

        safeToast('Share link created successfully!', 'success');

    } catch (error) {
        console.error('Error creating share:', error);
        safeToast('Failed to create share link: ' + error.message, 'error');
    }
}

function copyShareLink() {
    const link = document.getElementById('shareLink');
    link.select();
    document.execCommand('copy');
    safeToast('Link copied to clipboard!', 'success');
}

// ✅ GLOBAL CLICK LISTENER: Closes the dropdown only when clicking outside the container
document.addEventListener('click', function(event) {
    const container = document.querySelector('.email-tag-input-container');
    const dropdown = document.getElementById('emailSuggestionsDropdown');
    
    if (dropdown && container) {
        // If the click is NOT inside the container, hide the dropdown
        if (!container.contains(event.target)) {
            dropdown.style.display = 'none';
        }
    }
});

// Ensure we expose the functions globally for HTML onclick attributes
window.removeUserTag = removeUserTag;
window.handleSearchInput = handleSearchInput;