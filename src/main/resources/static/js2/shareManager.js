// Share Operations (static2)
let currentShareItem = null;

function showShareModal(id, name) {
    currentShareItem = { id, name };
    document.getElementById('shareType').value = 'PUBLIC';
    document.getElementById('sharePermission').value = 'VIEW';
    document.getElementById('expiresAt').value = '';
    document.getElementById('sharePassword').value = '';
    document.getElementById('allowedUsers').value = '';
    toggleShareOptions();
    showModal('shareModal');
}

function shareSelected() {
    if (selectedItems.size !== 1) {
        alert('Select exactly one file or folder to create a share link.');
        return;
    }
    const id = Array.from(selectedItems)[0];
    const item = allFiles.find(file => file.id === id);
    if (!item) {
        alert('The selected item is no longer available. Refresh and try again.');
        return;
    }
    showShareModal(item.id, item.name);
}

function toggleShareOptions() {
    const type = document.getElementById('shareType').value;
    document.getElementById('passwordOption').style.display = type === 'PROTECTED' ? 'block' : 'none';
    document.getElementById('usersOption').style.display = type === 'USER_ONLY' ? 'block' : 'none';
}

async function createShareLink() {
    const shareData = {
        driveId: currentShareItem.id,
        shareType: document.getElementById('shareType').value,
        permission: document.getElementById('sharePermission').value,
        expiresAt: document.getElementById('expiresAt').value || null
    };

    if (shareData.shareType === 'PROTECTED') {
        shareData.password = document.getElementById('sharePassword').value;
        if (!shareData.password) {
            alert('Password is required for protected share');
            return;
        }
    }

    if (shareData.shareType === 'USER_ONLY') {
        alert('User-specific sharing is not available on this server yet. Use a public or password-protected link.');
        return;
    }

    try {
        const response = await apiCall('/share', {
            method: 'POST',
            body: JSON.stringify({
                fileId: shareData.driveId,
                publicAccess: true,
                password: shareData.password || null,
                expiry: shareData.expiresAt || null
            })
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
