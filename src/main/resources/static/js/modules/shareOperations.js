// shareOperations.js – Complete share modal logic (Final comprehensive fix)

let currentShareItem = null;
let selectedUsers = [];
let shareTimer = null;

// ============================
// JWT HELPER TO GET CURRENT USER
// ============================
function decodeJwt(token) {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(atob(base64).split('').map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join(''));
        return JSON.parse(jsonPayload);
    } catch (e) {
        console.error('Invalid JWT token', e);
        return null;
    }
}

// ============================
// INIT & EVENT LISTENERS
// ============================
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) {
                closeShareModal();
                closeShareLinkModal();
            }
        });
    });

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            closeShareModal();
            closeShareLinkModal();
        }
    });

    document.addEventListener('click', function(e) {
        const resultsContainer = document.getElementById('userSearchResults');
        const searchInput = document.getElementById('userSearchInput');

        if (!resultsContainer || !searchInput) return;

        if (!e.target.closest('#userSearchInput') && !e.target.closest('#userSearchResults')) {
            closeAndClearSearchResults();
        }
    });

    const searchInput = document.getElementById('userSearchInput');
    if (searchInput && !document.getElementById('searchClearBtn')) {
        const parent = searchInput.parentNode;
        const wrapper = document.createElement('div');
        wrapper.style.position = 'relative';
        wrapper.style.width = '100%';
        wrapper.style.display = 'block';

        parent.insertBefore(wrapper, searchInput);
        wrapper.appendChild(searchInput);

        const clearBtn = document.createElement('span');
        clearBtn.id = 'searchClearBtn';
        clearBtn.innerHTML = '&times;';
        clearBtn.style.cssText = `
            position: absolute; right: 12px; top: 50%; transform: translateY(-50%); 
            cursor: pointer; color: #888; font-size: 20px; font-weight: bold; 
            display: none; z-index: 10; user-select: none; line-height: 1;
        `;
        wrapper.appendChild(clearBtn);

        searchInput.addEventListener('input', function() {
            clearBtn.style.display = this.value.length > 0 ? 'block' : 'none';
        });

        clearBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            searchInput.value = '';
            clearBtn.style.display = 'none';
            searchInput.focus();
            closeAndClearSearchResults();
        });
    }
});

function closeAndClearSearchResults() {
    const resultsContainer = document.getElementById('userSearchResults');
    if (resultsContainer) {
        resultsContainer.innerHTML = '';
        resultsContainer.classList.remove('show');
    }
    clearTimeout(shareTimer);
}

// ============================
// SHOW / CLOSE MODALS
// ============================
function showShareModal(id, name) {
    // Reset multi‑share state
    window._multiShareFileIds = null;

    currentShareItem = { id, name };
    document.getElementById('shareFileId').value = id;
    document.getElementById('shareFileName').value = name;

    // Update modal title and file info using existing elements
    const titleEl = document.getElementById('shareModalTitle');
    if (titleEl) titleEl.textContent = '📤 Create Share Link';

    const infoEl = document.getElementById('shareFileInfo');
    if (infoEl) {
        infoEl.innerHTML = `
            <span class="share-file-icon">📄</span>
            <span class="share-file-name">${escapeHtml(name)}</span>
        `;
    }

    // Reset form
    document.getElementById('sharePassword').value = '';
    document.getElementById('shareExpiry').value = '';
    document.getElementById('sharePermission').value = 'VIEW_DOWNLOAD';
    document.getElementById('shareType').value = 'PUBLIC';
    toggleShareOptions();
    selectedUsers = [];
    renderSelectedUsers();

    const input = document.getElementById('userSearchInput');
    if (input) {
        input.value = '';
        const clearBtn = document.getElementById('searchClearBtn');
        if (clearBtn) clearBtn.style.display = 'none';
    }
    closeAndClearSearchResults();
    document.getElementById('shareModal').classList.add('active');
    document.body.style.overflow = 'hidden';
}

// ============================
// MULTI‑SHARE MODAL OPENER (NEW)
// ============================
function openShareModalForMulti(fileIds, names) {
    // Store the IDs for multi‑share
    window._multiShareFileIds = fileIds;

    // Reset single‑file state
    currentShareItem = null;
    document.getElementById('shareFileId').value = '';

    // Update modal title and file info
    const titleEl = document.getElementById('shareModalTitle');
    if (titleEl) titleEl.textContent = `📤 Share ${fileIds.length} Items`;

    const infoEl = document.getElementById('shareFileInfo');
    if (infoEl) {
        infoEl.innerHTML = `
            <span class="share-file-icon">📂</span>
            <span class="share-file-name">Sharing ${fileIds.length} items: ${names.join(', ')}</span>
        `;
    }

    // Reset form
    document.getElementById('sharePassword').value = '';
    document.getElementById('shareExpiry').value = '';
    document.getElementById('sharePermission').value = 'VIEW_DOWNLOAD';
    document.getElementById('shareType').value = 'PUBLIC';
    toggleShareOptions();
    selectedUsers = [];
    renderSelectedUsers();

    const input = document.getElementById('userSearchInput');
    if (input) {
        input.value = '';
        const clearBtn = document.getElementById('searchClearBtn');
        if (clearBtn) clearBtn.style.display = 'none';
    }
    closeAndClearSearchResults();
    document.getElementById('shareModal').classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closeShareModal() {
    document.getElementById('shareModal').classList.remove('active');
    document.body.style.overflow = '';
    currentShareItem = null;
    selectedUsers = [];
    const input = document.getElementById('userSearchInput');
    if (input) input.value = '';
    closeAndClearSearchResults();
}

function closeShareLinkModal() {
    document.getElementById('shareLinkModal').classList.remove('active');
    document.body.style.overflow = '';
}

function toggleShareOptions() {
    const type = document.getElementById('shareType').value;
    document.getElementById('sharePasswordGroup').style.display = type === 'PROTECTED' ? 'block' : 'none';
    document.getElementById('userSelectionGroup').style.display = type === 'USER_ONLY' ? 'block' : 'none';
}

// ============================
// SEARCH USERS
// ============================
async function searchUsers(query) {
    const resultsContainer = document.getElementById('userSearchResults');
    clearTimeout(shareTimer);

    if (!query || query.trim().length < 1) {
        closeAndClearSearchResults();
        return;
    }

    shareTimer = setTimeout(async () => {
        try {
            const response = await apiCall(`/api/users/search?q=${encodeURIComponent(query)}`, { skipDedupe: true });
            if (!response.ok) {
                const fallbackResponse = await apiCall('/api/users', { skipDedupe: true });
                if (fallbackResponse.ok) {
                    const users = await fallbackResponse.json();
                    const filtered = users.filter(u =>
                        u.email.toLowerCase().includes(query.toLowerCase()) ||
                        (u.name && u.name.toLowerCase().includes(query.toLowerCase()))
                    );
                    renderSearchResults(filtered);
                    return;
                }
                closeAndClearSearchResults();
                return;
            }
            const users = await response.json();
            renderSearchResults(users);
        } catch (error) {
            console.error('User search failed:', error);
            closeAndClearSearchResults();
        }
    }, 300);
}

function renderSearchResults(users) {
    const resultsContainer = document.getElementById('userSearchResults');

    if (!users || users.length === 0) {
        resultsContainer.innerHTML = `
            <div class="user-search-result" style="cursor: default; justify-content: center; align-items: center; pointer-events: none; background-color: #f9f9f9;">
                <div style="color: #888; font-weight: 500;">No results found</div>
            </div>
        `;
        resultsContainer.classList.add('show');
        return;
    }

    const currentUser = getUserFromToken();
    const currentUserEmail = currentUser ? currentUser.email : null;

    let html = '';
    users.forEach(user => {
        const isAlreadySelected = selectedUsers.some(u => u.email.toLowerCase().trim() === user.email.toLowerCase().trim());
        if (isAlreadySelected) return;

        if (currentUserEmail && currentUserEmail.toLowerCase().trim() === user.email.toLowerCase().trim()) {
            return;
        }

        const displayName = user.name ? `${user.name} (${user.email})` : user.email;
        html += `
            <div class="user-search-result" onclick="selectUser('${user.email}', '${escapeHtml(user.email)}', '${escapeHtml(user.name || '')}')">
                <div class="user-result-avatar">${(user.name || user.email).charAt(0).toUpperCase()}</div>
                <div class="user-result-info">
                    <div class="user-result-name">${escapeHtml(user.name || '')}</div>
                    <div class="user-result-email">${escapeHtml(user.email)}</div>
                </div>
            </div>
        `;
    });
    resultsContainer.innerHTML = html;
    resultsContainer.classList.add('show');
}

// ============================
// SELECT / REMOVE USER
// ============================
function selectUser(id, email, name) {
    clearTimeout(shareTimer);

    if (selectedUsers.some(u => u.email.toLowerCase().trim() === email.toLowerCase().trim())) return;

    selectedUsers.push({ id, email, name });
    renderSelectedUsers();

    const input = document.getElementById('userSearchInput');
    const clearBtn = document.getElementById('searchClearBtn');

    if (input) {
        input.value = '';
        if (clearBtn) clearBtn.style.display = 'none';
        input.focus();
    }

    closeAndClearSearchResults();
}

function removeUser(email) {
    selectedUsers = selectedUsers.filter(u => u.email.toLowerCase().trim() !== email.toLowerCase().trim());
    renderSelectedUsers();

    const input = document.getElementById('userSearchInput');
    if (input && input.value.trim().length > 0) {
        searchUsers(input.value);
    } else {
        closeAndClearSearchResults();
    }
}

function renderSelectedUsers() {
    const container = document.getElementById('selectedUsersContainer');
    if (!selectedUsers.length) {
        container.innerHTML = '<span class="empty-chip-text">No users selected</span>';
        return;
    }
    let html = '';
    selectedUsers.forEach(user => {
        const displayName = user.name || user.email;
        html += `
            <span class="user-chip">
                <span class="chip-avatar">${displayName.charAt(0).toUpperCase()}</span>
                <span class="chip-label">${escapeHtml(displayName)}</span>
                <span class="remove-chip" onclick="removeUser('${user.email}')">&times;</span>
            </span>
        `;
    });
    container.innerHTML = html;
}

// ============================
// CREATE SHARE LINK (SINGLE / MULTI)
// ============================
async function createShareLink() {
    const shareType = document.getElementById('shareType').value;
    const password = document.getElementById('sharePassword').value;
    const expiry = document.getElementById('shareExpiry').value || null;
    const permission = document.getElementById('sharePermission').value;

    // Detect if multi‑share
    const isMulti = window._multiShareFileIds && window._multiShareFileIds.length > 0;

    // Build payload based on share type
    let payload;
    if (shareType === 'USER_ONLY') {
        if (selectedUsers.length === 0) {
            safeToast('Please select at least one user', 'warning');
            return;
        }
        payload = {
            ...(isMulti ? { fileIds: window._multiShareFileIds } : { fileId: document.getElementById('shareFileId').value }),
            publicAccess: false,
            permission: permission,
            expiry: expiry,
            password: null,
            allowedUsers: selectedUsers.map(u => u.email)
        };
    } else if (shareType === 'PROTECTED') {
        if (!password || password.length < 4) {
            safeToast('Password must be at least 4 characters', 'warning');
            return;
        }
        payload = {
            ...(isMulti ? { fileIds: window._multiShareFileIds } : { fileId: document.getElementById('shareFileId').value }),
            publicAccess: false,
            permission: permission,
            expiry: expiry,
            password: password,
            allowedUsers: []
        };
    } else {
        // PUBLIC
        payload = {
            ...(isMulti ? { fileIds: window._multiShareFileIds } : { fileId: document.getElementById('shareFileId').value }),
            publicAccess: true,
            permission: permission,
            expiry: expiry,
            password: null,
            allowedUsers: []
        };
    }

    const endpoint = isMulti ? '/share/multi' : '/share';

    const createBtn = document.querySelector('#shareModal .btn-primary');
    const originalText = createBtn.textContent;
    createBtn.textContent = 'Creating...';
    createBtn.disabled = true;

    try {
        const response = await apiCall(endpoint, {
            method: 'POST',
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            const err = await response.text();
            throw new Error(err || 'Share creation failed');
        }
        const result = await response.json();

        const shareUrl = result.url || `${window.location.origin}/share2.html?token=/${result.token}`;
        document.getElementById('shareLinkInput').value = shareUrl;
        document.getElementById('shareLinkExpiry').textContent = expiry ? new Date(expiry).toLocaleDateString() : 'Never';
        document.getElementById('shareLinkType').textContent = shareType === 'PUBLIC' ? 'Public' :
            shareType === 'PROTECTED' ? 'Password Protected' :
                'User Only';

        // Clear multi‑share state
        window._multiShareFileIds = null;

        closeShareModal();
        document.getElementById('shareLinkModal').classList.add('active');
        document.body.style.overflow = 'hidden';
        safeToast('Share link created successfully!', 'success');
    } catch (error) {
        safeToast(error.message || 'Failed to create share link', 'error');
    } finally {
        createBtn.textContent = originalText;
        createBtn.disabled = false;
    }
}

function copyShareLink() {
    const input = document.getElementById('shareLinkInput');
    const text = input.value;

    function clearSelection() {
        input.blur();
        input.setSelectionRange(0, 0);
        document.getSelection().removeAllRanges();
    }

    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text)
            .then(() => {
                safeToast('Share link copied to clipboard!', 'success');
                clearSelection();
            })
            .catch(() => fallbackCopy(text));
    } else {
        fallbackCopy(text);
    }

    function fallbackCopy(text) {
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.left = '-9999px';
        textarea.style.top = '-9999px';
        document.body.appendChild(textarea);
        textarea.focus();
        textarea.select();

        try {
            const success = document.execCommand('copy');
            if (success) {
                safeToast('Share link copied to clipboard!', 'success');
            } else {
                safeToast('Copy failed. Please select and copy manually.', 'error');
            }
        } catch (err) {
            safeToast('Copy failed. Please select and copy manually.', 'error');
        }

        document.body.removeChild(textarea);
        clearSelection();
    }
}

// ============================
// EXPOSE GLOBALLY
// ============================
window.showShareModal = showShareModal;
window.openShareModalForMulti = openShareModalForMulti;
window.closeShareModal = closeShareModal;
window.closeShareLinkModal = closeShareLinkModal;
window.createShareLink = createShareLink;
window.copyShareLink = copyShareLink;
window.toggleShareOptions = toggleShareOptions;
window.searchUsers = searchUsers;
window.selectUser = selectUser;
window.removeUser = removeUser;

console.log('✅ shareOperations.js loaded (Completed fixes)');