// profile.js - logic for the profile page

function requireAuth() {
    if (!localStorage.getItem('jwtToken')) {
        window.location.href = '/login.html';
        return false;
    }
    return true;
}

function showToast(message, type = 'success') {
    const toast = document.getElementById('toast');
    if (toast) {
        toast.textContent = message;
        toast.className = type + ' show';
        clearTimeout(toast._timeout);
        toast._timeout = setTimeout(() => { toast.className = ''; }, 3000);
    }
}

async function loadProfile() {
    if (!requireAuth()) return;
    const previewContainer = document.getElementById('photoPreview');
    const emailDisplay = document.getElementById('userEmailDisplay');
    const userAvatar = document.getElementById('userAvatar');
    const headerEmail = document.getElementById('userEmail');
    const defaultAvatar = '/assets/img/default_avatar.jpg';

    function fallbackToDefaultAvatar() {
        previewContainer.innerHTML = `<img src="${defaultAvatar}" alt="Default Avatar" class="profile-avatar" />`;
        userAvatar.style.backgroundImage = `url(${defaultAvatar})`;
        userAvatar.style.backgroundSize = 'cover';
        userAvatar.textContent = '';
        emailDisplay.textContent = 'User';
    }

    try {
        const res = await fetch(`${API_URL}/auth/me`, { headers: { 'Authorization': 'Bearer ' + localStorage.getItem('jwtToken') } });
        
        if (res.status === 401) {
            localStorage.removeItem('jwtToken');
            showToast('Session expired. Redirecting to login...', 'error');
            setTimeout(() => window.location.href = '/login.html', 1000);
            return;
        }

        const json = await res.json();
        
        if (res.ok && json.success) {
            const user = json.data;
            document.getElementById('name').value = user.name || '';
            headerEmail.textContent = user.email || '';
            emailDisplay.textContent = user.email || '';

            if (user.photoUrl) {
                try {
                    const imgRes = await fetch(`${API_URL}/auth/profile/photo`, { 
                        headers: { 'Authorization': 'Bearer ' + localStorage.getItem('jwtToken') } 
                    });

                    if (imgRes.ok) {
                        const blob = await imgRes.blob();
                        const imageUrl = URL.createObjectURL(blob);
                        previewContainer.innerHTML = `<img src="${imageUrl}" alt="profile" class="profile-avatar" onerror="this.src='${defaultAvatar}'" />`;
                        userAvatar.style.backgroundImage = `url(${imageUrl})`;
                        userAvatar.style.backgroundSize = 'cover';
                        userAvatar.textContent = '';
                    } else {
                        fallbackToDefaultAvatar();
                    }
                } catch (error) {
                    fallbackToDefaultAvatar();
                }
            } else {
                fallbackToDefaultAvatar();
            }
        } else {
            fallbackToDefaultAvatar();
            showToast('Unable to load profile details', 'error');
        }
    } catch (e) {
        fallbackToDefaultAvatar();
        showToast('Network error loading profile', 'error');
    }
}

async function saveProfile() {
    if (!requireAuth()) return;
    const name = document.getElementById('name').value;
    try {
        const res = await fetch(`${API_URL}/auth/profile`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + localStorage.getItem('jwtToken') },
            body: JSON.stringify({ name: name })
        });
        const json = await res.json();
        if (res.ok && json.success) {
            showToast('Profile updated successfully!');
            loadProfile();
        } else {
            showToast(json.message || 'Update failed', 'error');
        }
    } catch (e) {
        showToast('Network error saving profile', 'error');
    }
}

async function uploadPhoto() {
    if (!requireAuth()) return;
    const fileInput = document.getElementById('photoFile');
    const file = fileInput.files[0];
    if (!file) { 
        showToast('Please choose an image to upload', 'error'); 
        return; 
    }
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
            showToast('Photo uploaded successfully!');
            loadProfile();
        } else {
            showToast(json.message || 'Photo upload failed', 'error');
        }
    } catch (e) {
        showToast('Network error uploading photo', 'error');
    }
}

async function changePassword() {
    const oldP = document.getElementById('oldPassword').value;
    const newP = document.getElementById('newPassword').value;
    if(!oldP || !newP) {
        showToast('Please fill in both password fields', 'error');
        return;
    }
    try {
        const res = await fetch(`${API_URL}/auth/change-password`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + localStorage.getItem('jwtToken') },
            body: JSON.stringify({ oldPassword: oldP, newPassword: newP })
        });
        const json = await res.json();
        if (res.ok && json.success) {
            showToast('Password changed successfully!');
            document.getElementById('oldPassword').value = '';
            document.getElementById('newPassword').value = '';
        } else {
            showToast(json.message || 'Failed to change password', 'error');
        }
    } catch (e) {
        showToast('Network error changing password', 'error');
    }
}

// Initialize profile on load
document.addEventListener('DOMContentLoaded', () => {
    // 1. Load Profile Data
    loadProfile();
    
    // 2. 👇 Updated: Calls the shared navigation function
    setupSidebarNavigation();
});

// 🛡️ Explicitly expose functions to the global window object for HTML onclick attributes
window.saveProfile = saveProfile;
window.uploadPhoto = uploadPhoto;
window.changePassword = changePassword;
window.showToast = showToast;