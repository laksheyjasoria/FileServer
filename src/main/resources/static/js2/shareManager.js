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
        const emails = document.getElementById('allowedUsers').value.split(',').map(e => e.trim()).filter(e => e);
        shareData.allowedUserIds = emails;
        if (emails.length === 0) {
            alert('At least one user email is required');
            return;
        }
    }

    try {
        const response = await apiCall('/share', {
            method: 'POST',
            body: JSON.stringify({
                resourceId: shareData.driveId,
                type: shareData.shareType,
                permission: shareData.permission,
                password: shareData.password || null,
                allowedUsers: shareData.allowedUserIds || null,
                expiresAt: shareData.expiresAt
            })
        });

        const result = await response.json();
        const shareUrl = `${window.location.origin}/static2/share.html?token=${result.token || result.shareToken || ''}`;
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
