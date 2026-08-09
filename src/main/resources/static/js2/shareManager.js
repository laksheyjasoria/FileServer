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
        // Single item share
        const item = allFiles.find(f => f.id === ids[0]);
        if (item) showShareModal(ids, item.name);
    } else {
        // Multi-item share
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
        // 1. Determine if we are doing single or multi share
        const isMulti = Array.isArray(currentShareItem.ids) && currentShareItem.ids.length > 1;
        let endpoint = isMulti ? '/share/multi' : '/share';
        let body = {
            publicAccess: shareType === 'PUBLIC', // PUBLIC: true, PROTECTED/USER_ONLY: false
            password: password || null,
            expiry: expiresAt || null
        };

        if (isMulti) {
            // Multi-share payload
            body.fileIds = currentShareItem.ids;
        } else {
            // Single-share payload
            body.fileId = currentShareItem.ids[0];
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