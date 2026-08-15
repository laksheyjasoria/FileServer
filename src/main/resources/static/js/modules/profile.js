// profile.js – Profile page

function requireAuth() {
    if (!localStorage.getItem('jwtToken')) { window.location.href = '/login.html'; return false; }
    return true;
}

async function loadProfile() {
    if (!requireAuth()) return;
    const preview = document.getElementById('photoPreview');
    const emailDisplay = document.getElementById('userEmailDisplay');
    const userAvatar = document.getElementById('userAvatar');
    const headerEmail = document.getElementById('userEmail');
    const syncBtn = document.getElementById('syncGoogleBtn');
    const defaultAvatar = '/assets/img/default_avatar.jpg';

    function setDefaultAvatar() {
        preview.innerHTML = `<img src="${defaultAvatar}" class="profile-avatar" alt="Default Avatar" />`;
        userAvatar.style.backgroundImage = `url(${defaultAvatar})`;
        userAvatar.style.backgroundSize = 'cover';
        userAvatar.textContent = '';
    }

    try {
        const token = localStorage.getItem('jwtToken');
        const res = await fetch(`${API_URL}/auth/me`, {
            headers: { 'Authorization': 'Bearer ' + token }
        });
        if (res.status === 401) {
            localStorage.removeItem('jwtToken');
            safeToast('Session expired. Redirecting...', 'error');
            setTimeout(() => window.location.href = '/login.html', 1000);
            return;
        }
        const json = await res.json();
        if (res.ok && json.success) {
            const user = json.data;
            document.getElementById('name').value = user.name || '';
            headerEmail.textContent = user.email || '';
            emailDisplay.textContent = user.email || '';

            if (syncBtn) {
                const isGoogleUser = (user.provider === 'GOOGLE');
                syncBtn.style.display = isGoogleUser ? 'inline-block' : 'none';
            }

            const hasValidPhoto = user.photoUrl && user.photoUrl !== 'null' && user.photoUrl !== 'undefined';
            if (hasValidPhoto) {
                try {
                    const imgRes = await fetch(`${API_URL}/auth/profile/photo`, {
                        headers: { 'Authorization': 'Bearer ' + token }
                    });
                    if (imgRes.ok) {
                        const blob = await imgRes.blob();
                        if (blob.size > 0) {
                            const url = URL.createObjectURL(blob);
                            preview.innerHTML = `<img src="${url}" class="profile-avatar" alt="Profile Photo" />`;
                            userAvatar.style.backgroundImage = `url(${url})`;
                            userAvatar.style.backgroundSize = 'cover';
                            userAvatar.textContent = '';
                            setTimeout(() => URL.revokeObjectURL(url), 10000);
                        } else {
                            setDefaultAvatar();
                        }
                    } else {
                        setDefaultAvatar();
                    }
                } catch (photoError) {
                    setDefaultAvatar();
                }
            } else {
                setDefaultAvatar();
            }
        } else {
            setDefaultAvatar();
            safeToast('Unable to load profile details', 'error');
        }
    } catch (e) {
        console.error('Profile load error:', e);
        setDefaultAvatar();
        safeToast('Network error loading profile', 'error');
    }
}

async function saveProfile() {
    if (!requireAuth()) return;
    const name = document.getElementById('name').value;
    try {
        const res = await fetch(`${API_URL}/auth/profile`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + localStorage.getItem('jwtToken') },
            body: JSON.stringify({ name })
        });
        const json = await res.json();
        if (res.ok && json.success) {
            safeToast('Profile updated!', 'success');
            loadProfile();
        } else {
            safeToast(json.message || 'Update failed', 'error');
        }
    } catch (e) {
        safeToast('Network error saving profile', 'error');
    }
}

async function uploadPhoto() {
    if (!requireAuth()) return;
    const file = document.getElementById('photoFile').files[0];
    if (!file) { safeToast('Please choose an image', 'warning'); return; }
    const formData = new FormData();
    formData.append('file', file);
    try {
        const res = await fetch(`${API_URL}/auth/profile/photo`, {
            method: 'POST',
            headers: { 'Authorization': 'Bearer ' + localStorage.getItem('jwtToken') },
            body: formData
        });
        const json = await res.json();
        if (res.ok && json.success) {
            safeToast('Photo uploaded!', 'success');
            loadProfile();
        } else {
            safeToast(json.message || 'Photo upload failed', 'error');
        }
    } catch (e) {
        safeToast('Network error uploading photo', 'error');
    }
}

async function changePassword() {
    const oldP = document.getElementById('oldPassword').value;
    const newP = document.getElementById('newPassword').value;
    if (!oldP || !newP) { safeToast('Please fill both fields', 'warning'); return; }
    try {
        const res = await fetch(`${API_URL}/auth/change-password`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + localStorage.getItem('jwtToken') },
            body: JSON.stringify({ oldPassword: oldP, newPassword: newP })
        });
        const json = await res.json();
        if (res.ok && json.success) {
            safeToast('Password changed!', 'success');
            document.getElementById('oldPassword').value = '';
            document.getElementById('newPassword').value = '';
        } else {
            safeToast(json.message || 'Change failed', 'error');
        }
    } catch (e) {
        safeToast('Network error changing password', 'error');
    }
}

async function syncWithGoogle() {
    console.log('🔄 syncWithGoogle called');
    try {
        if (typeof showConfirm !== 'function') {
            console.warn('⚠️ showConfirm not defined, using native confirm');
            const confirmed = confirm(
                'To sync your profile with Google, you will be redirected to the login page. Please sign in with Google again.\n\nYour current session will remain active, and you will be redirected back to your profile after syncing.'
            );
            if (!confirmed) return;
            sessionStorage.setItem('googleSyncMode', 'true');
            window.location.href = '/login.html?sync=true';
            return;
        }
        const confirmed = await showConfirm(
            'To sync your profile with Google, you will be redirected to the login page. Please sign in with Google again.\n\nYour current session will remain active, and you will be redirected back to your profile after syncing.',
            'Sync with Google',
            'Continue',
            'Cancel',
            'primary'
        );
        if (!confirmed) return;
        if (typeof hideModal === 'function') hideModal();
        await new Promise(resolve => setTimeout(resolve, 200));
        sessionStorage.setItem('googleSyncMode', 'true');
        window.location.href = '/login.html?sync=true';
    } catch (error) {
        console.error('❌ Error in syncWithGoogle:', error);
        safeToast('An error occurred. Please try again.', 'error');
    }
}

document.addEventListener('DOMContentLoaded', () => {
    loadProfile();
    setupSidebarNavigation();
    loadUserInfoIntoUI();
    const avatar = document.getElementById('userAvatar');
    if (avatar) {
        avatar.addEventListener('click', () => {
            window.location.href = '/profile.html';
        });
        avatar.style.cursor = 'pointer';
    }
});

window.saveProfile = saveProfile;
window.uploadPhoto = uploadPhoto;
window.changePassword = changePassword;
window.syncWithGoogle = syncWithGoogle;
window.loadProfile = loadProfile;