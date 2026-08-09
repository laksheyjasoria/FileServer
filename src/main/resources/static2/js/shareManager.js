// Share Operations (static2)
let currentShareItem = null;

function showShareModal(ids, name) {
    // ids can be a single string OR an array of strings
    currentShareItem = { ids, name };
    
    document.getElementById('shareType').value = 'PUBLIC';
    document.getElementById('sharePermission').value = 'VIEW';
    document.getElementById('expiresAt').value = '';
    document.getElementById('sharePassword').value = '';
    document.getElementById('allowedUsers').value = '';
    toggleShareOptions();
    showModal('shareModal');
}

function shareSelected() {
    const ids = Array.from(selectedItems);
    if (ids.length === 0) {
        alert('Please select at least one item to share.');
        return;
    }

    if (ids.length === 1) {
        // Single item share (existing logic)
        const item = allFiles.find(f => f.id === ids[0]);
        if (item) showShareModal(item.id, item.name);
    } else {
        // Multi-item share (NEW logic)
        showShareModal(ids, `${ids.length} items`);
    }
}

function toggleShareOptions() {
    const type = document.getElementById('shareType').value;
    document.getElementById('passwordOption').style.display = type === 'PROTECTED' ? 'block' : 'none';
    document.getElementById('usersOption').style.display = type === 'USER_ONLY' ? 'block' : 'none';
}

async function createShareLink() {
    const shareType = document.getElementById('shareType').value;
    const permission = document.getElementById('sharePermission').value;
    const expiresAt = document.getElementById('expiresAt').value || null;
    const password = document.getElementById('sharePassword').value;

    // Validation for Protected shares
    if (shareType === 'PROTECTED') {
        if (!password) {
            alert('Password is required for protected share');
            return;
        }
    }

    // Validation for User Only shares
    if (shareType === 'USER_ONLY') {
        alert('User-specific sharing is not available on this server yet. Use a public or password-protected link.');
        return;
    }

    try {
        let endpoint = '/share';
        let body = {};

        // Determine if it is a single share or multi share
        const isMulti = Array.isArray(currentShareItem.ids);
        
        if (!isMulti) {
            // Single share endpoint
            endpoint = '/share';
            body = {
                fileId: currentShareItem.ids,
                publicAccess: shareType === 'PUBLIC',
                password: password || null,
                expiry: expiresAt || null
            };
        } else {
            // Multi share endpoint (NEW)
            endpoint = '/share/multi';
            body = {
                fileIds: currentShareItem.ids,
                publicAccess: shareType === 'PUBLIC',
                password: password || null,
                expiry: expiresAt || null
            };
        }

        const response = await apiCall(endpoint, {
            method: 'POST',
            body: JSON.stringify(body)
        });

        const result = await response.json();
        const shareUrl = `${window.location.origin}/share2.html?token=${result.token || result.shareToken || ''}`;
        
        document.getElementById('shareLink').value = shareUrl;
        closeModal('shareModal');
        showModal('shareLinkModal');

    } catch (error) {
        console.error('Error creating share:', error);
        alert('Failed to create share link: ' + error.message);
    }
}

function copyShareLink() {
    const link = document.getElementById('shareLink');
    link.select();
    document.execCommand('copy');
    alert('Link copied to clipboard!');
}