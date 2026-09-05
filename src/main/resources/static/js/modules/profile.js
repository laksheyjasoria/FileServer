/**
 * Profile Module – User profile, privacy, friends, account management
 * Phase 2 – Simplified privacy (EVERYONE / FRIENDS_ONLY / NOBODY)
 */

// ================================
// STATE
// ================================
let currentUser = null;
let friends = [];
let pendingRequests = [];
let sentRequests = [];

// ================================
// DOM REFS
// ================================
const container = document.getElementById("profileContainer");

// ================================
// MAIN INIT
// ================================
document.addEventListener("DOMContentLoaded", async function () {
  const token = localStorage.getItem("jwtToken");
  if (!token || !isTokenValid()) {
    localStorage.removeItem("jwtToken");
    window.location.href = "/login.html";
    return;
  }

  await loadProfile();

  // ---- Attach password modal events ----
  const cancelPwBtn = document.getElementById("cancelPasswordModalBtn");
  const confirmPwBtn = document.getElementById("confirmPasswordModalBtn");
  const pwModal = document.getElementById("changePasswordModal");

  if (cancelPwBtn)
    cancelPwBtn.addEventListener("click", hideChangePasswordModal);
  if (confirmPwBtn)
    confirmPwBtn.addEventListener("click", submitPasswordChange);
  if (pwModal) {
    pwModal.addEventListener("click", function (e) {
      if (e.target === this) hideChangePasswordModal();
    });
  }

  // ---- Password strength live validation ----
  const newPassInput = document.getElementById("modalNewPassword");
  if (newPassInput) {
    newPassInput.addEventListener("input", function () {
      validatePasswordStrength(this.value);
      validateConfirmPassword();
    });
  }
  const confirmInput = document.getElementById("modalConfirmPassword");
  if (confirmInput) {
    confirmInput.addEventListener("input", validateConfirmPassword);
  }

  // ---- Attach privacy modal events ----
  const cancelPrivBtn = document.getElementById("cancelPrivacyModalBtn");
  const savePrivBtn = document.getElementById("savePrivacyModalBtn");
  const privModal = document.getElementById("privacySettingsModal");
  const incomingSelect = document.getElementById("modalIncomingSharePrivacy");
  const friendSelect = document.getElementById("modalFriendRequestPrivacy");
  const autoCheck = document.getElementById("modalAutoApprove");

  if (cancelPrivBtn)
    cancelPrivBtn.addEventListener("click", hidePrivacySettingsModal);
  if (savePrivBtn) savePrivBtn.addEventListener("click", savePrivacySettings);
  if (incomingSelect)
    incomingSelect.addEventListener("change", updatePrivacyHints);
  if (friendSelect) friendSelect.addEventListener("change", updatePrivacyHints);
  if (autoCheck) autoCheck.addEventListener("change", updatePrivacyHints);
  if (privModal) {
    privModal.addEventListener("click", function (e) {
      if (e.target === this) hidePrivacySettingsModal();
    });
  }
});

// ================================
// LOAD PROFILE DATA
// ================================
async function loadProfile() {
  try {
    container.innerHTML = '<div class="loading">Loading profile...</div>';

    // 1. Get current user – from cache first, then API
    let user = window.getUserData();
    if (!user) {
      const userResponse = await apiCall("/api/users/me", { skipDedupe: true });
      if (!userResponse || !userResponse.ok)
        throw new Error("Failed to load user");
      user = await userResponse.json();
      window.setUserData(user);
    }
    currentUser = user;
    console.log("👤 User data loaded:", currentUser);

    // 2. Fetch friends, pending, sent (always fresh)
    const friendsResponse = await apiCall("/api/friends", { skipDedupe: true });
    if (friendsResponse && friendsResponse.ok) {
      friends = await friendsResponse.json();
    }

    const pendingResponse = await apiCall("/api/friends/pending", {
      skipDedupe: true,
    });
    if (pendingResponse && pendingResponse.ok) {
      pendingRequests = await pendingResponse.json();
    }

    const sentResponse = await apiCall("/api/friends/sent", {
      skipDedupe: true,
    });
    if (sentResponse && sentResponse.ok) {
      sentRequests = await sentResponse.json();
    }

    renderProfile();
  } catch (error) {
    console.error("Error loading profile:", error);
    container.innerHTML = `
      <div style="padding:40px; text-align:center; color:#e53e3e;">
        <div style="font-size:48px; margin-bottom:16px;">❌</div>
        <div style="font-size:18px; font-weight:600;">Failed to load profile</div>
        <div style="font-size:14px; color:#718096; margin-top:8px;">${escapeHtml(error.message)}</div>
      </div>
    `;
  }
}

// ================================
// RENDER PROFILE
// ================================
function renderProfile() {
  if (!currentUser) return;

  const isAdmin = currentUser.role === "ADMIN";
  const incomingSharePrivacy = currentUser.incomingSharePrivacy || "EVERYONE";
  const friendRequestPrivacy = currentUser.friendRequestPrivacy || "EVERYONE";
  const autoApprove = currentUser.autoApproveFriends || false;

  const html = `
    <div class="profile-card">
      <!-- HEADER -->
      <div class="profile-header">
        <div class="profile-avatar-wrapper" id="photoPreview">
          <div id="profilePhotoPlaceholder" class="initials">${(currentUser.name || currentUser.email).charAt(0).toUpperCase()}</div>
        </div>
        <div class="profile-name">${escapeHtml(currentUser.name || currentUser.email)}</div>
        <div class="profile-email">${escapeHtml(currentUser.email)}</div>
        <div class="header-actions">
          <label for="photoFile">📷 Change Photo</label>
          <input id="photoFile" type="file" accept="image/*" style="display:none;" onchange="window.uploadPhoto()">
          ${currentUser.provider === "GOOGLE" ? `<button class="google-sync" onclick="window.syncWithGoogle()">🔄 Sync with Google</button>` : ""}
        </div>
      </div>

      <!-- BODY -->
      <div class="profile-body">

        <!-- PERSONAL INFORMATION -->
        <div class="section">
          <div class="section-title">👤 Personal Information</div>
          <div class="form-group">
            <label for="nameInput">Full Name</label>
            <input id="nameInput" type="text" value="${escapeHtml(currentUser.name || "")}" placeholder="Enter your full name">
          </div>
          <button class="btn-profile btn-primary" onclick="window.saveProfile()">💾 Save Changes</button>
          <button class="btn-profile btn-secondary" onclick="window.showChangePasswordModal()">🔑 Change Password</button>
          <button class="btn-profile btn-secondary" onclick="showPrivacySettingsModal()">⚙️ Edit Settings</button>
        </div>

        <!-- FRIENDS -->
        <div class="section">
          <div class="section-title">👥 Friends <span class="badge">${friends.length}</span></div>
          <div class="friend-list" id="friendList">
            ${
              friends.length === 0
                ? '<span style="color:#a0aec0; font-size:14px;">No friends yet</span>'
                : friends
                    .map(
                      (f) => `
                <span class="friend-item">
                  ${escapeHtml(f.name || f.email)}
                  <span class="remove-friend" onclick="window.removeFriend('${f.id}')" title="Remove friend">✕</span>
                </span>
              `,
                    )
                    .join("")
            }
          </div>
        </div>

        <!-- SEND FRIEND REQUEST -->
        <div class="section">
          <div class="section-title" style="font-size:14px;">📨 Send Friend Request</div>
          <div class="add-user-input">
            <input type="text" id="friendRequestSearch" placeholder="Search by name or email..." />
            <button onclick="window.debouncedFriendSearch()">🔍 Search</button>
          </div>
          <div id="friendSearchResults" style="margin-top:8px; max-height:150px; overflow-y:auto; background:white; border:1px solid #e2e8f0; border-radius:8px; display:none;"></div>
          <div style="font-size:12px; color:#a0aec0; margin-top:4px;">
            💡 Search for a user and click "Send Request" next to their name.
          </div>
        </div>

        <!-- SENT REQUESTS -->
        ${
          sentRequests.length > 0
            ? `
          <div class="section">
            <div class="section-title" style="font-size:14px;">📤 Sent Requests <span class="badge">${sentRequests.length}</span></div>
            <div style="display:flex; flex-wrap:wrap; gap:8px;">
              ${sentRequests
                .map(
                  (req) => `
                <span class="pending-request sent" style="background:#e2e8f0; color:#4a5568;">
                  ${escapeHtml(req.friendName || req.friendEmail || "Unknown")}
                  <button onclick="window.cancelSentRequest(${req.id})" style="padding:2px 10px; border:none; border-radius:12px; font-size:12px; cursor:pointer; background:#fc8181; color:white; font-weight:500;">Cancel</button>
                </span>
              `,
                )
                .join("")}
            </div>
          </div>
        `
            : ""
        }

        <!-- INCOMING REQUESTS -->
        ${
          pendingRequests.length > 0
            ? `
          <div class="section">
            <div class="section-title" style="font-size:14px;">📩 Incoming Requests <span class="badge">${pendingRequests.length}</span></div>
            <div style="display:flex; flex-wrap:wrap; gap:8px;">
              ${pendingRequests
                .map(
                  (req) => `
                <span class="pending-request" 
                      data-request='${encodeURIComponent(JSON.stringify(req))}'
                      onclick="window.openFriendRequestModal(this)">
                  ${escapeHtml(req.userName || req.userEmail || "Unknown")}
                </span>
              `,
                )
                .join("")}
            </div>
          </div>
        `
            : ""
        }

        <!-- ACCOUNT MANAGEMENT -->
        <div class="section">
          <div class="section-title">⚙️ Account Management</div>
          <div class="account-actions">
            <button class="btn-profile btn-danger" onclick="window.deactivateAccount()">🔒 Deactivate Account</button>
            <button class="btn-profile btn-danger" onclick="window.deleteAccount()" style="background:#e53e3e;">🗑️ Delete Account</button>
          </div>
          <div style="margin-top:8px; font-size:12px; color:#a0aec0;">
            ⚠️ Deactivating your account will prevent you from logging in. Deleting your account is permanent and cannot be undone.
          </div>
        </div>

      </div> <!-- end profile-body -->
    </div> <!-- end profile-card -->
  `;

  container.innerHTML = html;

  // Load the profile photo (signed URL)
  loadProfilePhoto();

  // Attach event listeners for friend search
  const searchInput = document.getElementById("friendRequestSearch");
  if (searchInput) {
    searchInput.addEventListener("input", window.debouncedFriendSearch);
    searchInput.addEventListener("keydown", function (e) {
      if (e.key === "Enter") {
        e.preventDefault();
        window.debouncedFriendSearch();
      }
    });
  }
}

// ================================
// LOAD PROFILE PHOTO (cached)
// ================================
async function loadProfilePhoto() {
  const previewContainer = document.getElementById("photoPreview");
  if (!previewContainer) return;

  const user = window.getUserData() || window.getUserFromToken();
  if (!user || !user.photoUrl) {
    const initial = (user?.name || user?.email || "U").charAt(0).toUpperCase();
    previewContainer.innerHTML = `<div class="initials">${initial}</div>`;
    return;
  }

  // Check cache first
  const cachedDataUrl = window.getCachedAvatarDataUrl
    ? window.getCachedAvatarDataUrl()
    : null;
  if (cachedDataUrl) {
    previewContainer.innerHTML = `<img src="${cachedDataUrl}" alt="Profile photo" style="width:100%; height:100%; object-fit:cover;" />`;
    return;
  }

  // No cache – fetch
  const token = localStorage.getItem("jwtToken");
  if (!token) return;

  try {
    const response = await fetch(`/api/files/${user.photoUrl}/signed/session`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    const avatarUrl = data.url;

    const imgResponse = await fetch(avatarUrl);
    if (!imgResponse.ok) throw new Error(`Image download failed`);
    const blob = await imgResponse.blob();
    const reader = new FileReader();
    const dataUrl = await new Promise((resolve) => {
      reader.onload = () => resolve(reader.result);
      reader.readAsDataURL(blob);
    });

    if (window.setCachedAvatarDataUrl) {
      window.setCachedAvatarDataUrl(dataUrl, user.photoUrl);
    }
    previewContainer.innerHTML = `<img src="${dataUrl}" alt="Profile photo" style="width:100%; height:100%; object-fit:cover;" />`;
  } catch (err) {
    console.warn("Profile photo load failed:", err);
    const initial = (user?.name || user?.email || "U").charAt(0).toUpperCase();
    previewContainer.innerHTML = `<div class="initials">${initial}</div>`;
  }
}

// ================================
// DEBOUNCED FRIEND SEARCH
// ================================
window.debouncedFriendSearch = debounce(async function () {
  const input = document.getElementById("friendRequestSearch");
  const query = input.value.trim();
  const resultsContainer = document.getElementById("friendSearchResults");

  if (!query) {
    resultsContainer.style.display = "none";
    return;
  }

  try {
    const response = await apiCall(
      `/api/users/search?q=${encodeURIComponent(query)}`,
      { skipDedupe: true },
    );
    if (!response || !response.ok) throw new Error("Search failed");
    const results = await response.json();

    resultsContainer.style.display = "block";
    resultsContainer.innerHTML = "";

    if (results.length === 0) {
      resultsContainer.innerHTML = `<div style="padding:12px; color:#a0aec0; text-align:center;">No users found</div>`;
      return;
    }

    results.forEach((u) => {
      const div = document.createElement("div");
      div.className = "search-result-item";
      div.style.cssText =
        "padding:8px 14px; cursor:pointer; border-bottom:1px solid #f7fafc; display:flex; justify-content:space-between; font-size:13px;";

      let buttonHtml = "";
      if (u.requestStatus === "ACCEPTED") {
        buttonHtml =
          '<span style="color:#48bb78; font-weight:600;">✓ Friends</span>';
      } else if (u.requestStatus === "PENDING_SENT") {
        buttonHtml =
          '<span style="color:#f59e0b; font-weight:600;">⏳ Pending</span>';
      } else if (u.requestStatus === "PENDING_RECEIVED") {
        buttonHtml = `
          <button onclick="window.handleFriendRequest(${u.id}, 'accept')" style="background:#48bb78; color:white; border:none; border-radius:4px; padding:2px 12px; cursor:pointer; font-size:12px; margin-right:4px;">Accept</button>
          <button onclick="window.handleFriendRequest(${u.id}, 'reject')" style="background:#fc8181; color:white; border:none; border-radius:4px; padding:2px 12px; cursor:pointer; font-size:12px;">Reject</button>
        `;
      } else {
        buttonHtml = `
          <button onclick="window.sendFriendRequestDirect('${u.id}', '${escapeJS(u.email)}', '${escapeJS(u.name || u.email)}')" style="background:#48bb78; color:white; border:none; border-radius:4px; padding:2px 12px; cursor:pointer; font-size:12px;">➕ Send Request</button>
        `;
      }

      div.innerHTML = `
        <span><strong>${escapeHtml(u.name || u.email)}</strong></span>
        <span style="color:#718096; font-size:12px;">${escapeHtml(u.email)}</span>
        ${buttonHtml}
      `;
      resultsContainer.appendChild(div);
    });
  } catch (error) {
    showToast("Error searching: " + error.message, "error");
  }
}, 300);

// ================================
// DIRECT SEND FRIEND REQUEST
// ================================
window.sendFriendRequestDirect = async function (id, email, name) {
  try {
    const response = await apiCall(
      `/api/friends/request?email=${encodeURIComponent(email)}`,
      { method: "POST" },
    );
    if (!response || !response.ok) {
      const errorText = await response.text();
      throw new Error(errorText || "Failed to send request");
    }
    showToast(`Friend request sent to ${name || email}`, "success");
    document.getElementById("friendSearchResults").style.display = "none";
    document.getElementById("friendRequestSearch").value = "";
    await loadProfile();
  } catch (error) {
    if (error.message && error.message.includes("already exists")) {
      showToast("Request already sent to this user", "warning");
      document.getElementById("friendSearchResults").style.display = "none";
      document.getElementById("friendRequestSearch").value = "";
    } else {
      showToast("Error: " + error.message, "error");
    }
  }
};

// ================================
// CANCEL SENT REQUEST
// ================================
window.cancelSentRequest = async function (requestId) {
  const confirmed = await showConfirm(
    "Cancel this friend request?",
    "Cancel Request",
    "Yes, cancel",
    "No",
    "danger",
  );
  if (!confirmed) return;

  try {
    const response = await apiCall(`/api/friends/request/${requestId}`, {
      method: "DELETE",
    });
    if (!response || !response.ok) {
      const errorText = await response.text();
      throw new Error(errorText || "Failed to cancel");
    }
    showToast("Request cancelled", "success");
    await loadProfile();
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
};

// ================================
// OPEN FRIEND REQUEST MODAL
// ================================
window.openFriendRequestModal = async function (element) {
  const encodedData = element.dataset.request;
  if (!encodedData) {
    showToast("Invalid request data", "error");
    return;
  }

  let requestData;
  try {
    const decoded = decodeURIComponent(encodedData);
    requestData = JSON.parse(decoded);
  } catch (e) {
    console.error("Failed to parse request data:", e);
    showToast("Error loading request details", "error");
    return;
  }

  const sender = {
    id: requestData.userId,
    name: requestData.userName || requestData.userEmail || "Unknown",
    email: requestData.userEmail,
    photoUrl: requestData.userPhotoUrl,
  };

  let photoSrc = null;
  if (sender.photoUrl) {
    try {
      const token = localStorage.getItem("jwtToken");
      const response = await fetch(
        `/api/files/${sender.photoUrl}/signed/session`,
        {
          headers: { Authorization: `Bearer ${token}` },
        },
      );
      if (response.ok) {
        const data = await response.json();
        photoSrc = data.url;
      }
    } catch (e) {
      console.warn("Could not fetch photo for modal:", e);
    }
  }

  const existing = document.getElementById("friendRequestModal");
  if (existing) existing.remove();

  const modalHTML = `
    <div class="friend-request-modal" id="friendRequestModal">
      <div class="modal-box">
        <button class="close-btn" onclick="window.closeFriendRequestModal()">✕</button>
        ${
          photoSrc
            ? `<img src="${photoSrc}" alt="Profile photo" class="modal-avatar" />`
            : `<div class="modal-avatar-placeholder">${sender.name.charAt(0).toUpperCase()}</div>`
        }
        <div class="modal-name">${escapeHtml(sender.name)}</div>
        <div class="modal-email">${escapeHtml(sender.email)}</div>
        <div class="modal-actions">
          <button class="accept-btn" onclick="window.handleFriendRequestFromModal(${requestData.id}, 'accept')">✓ Accept</button>
          <button class="reject-btn" onclick="window.handleFriendRequestFromModal(${requestData.id}, 'reject')">✕ Reject</button>
        </div>
      </div>
    </div>
  `;
  document.body.insertAdjacentHTML("beforeend", modalHTML);
};

// ================================
// CLOSE FRIEND REQUEST MODAL
// ================================
window.closeFriendRequestModal = function () {
  const modal = document.getElementById("friendRequestModal");
  if (modal) modal.remove();
};

// ================================
// HANDLE FRIEND REQUEST FROM MODAL
// ================================
window.handleFriendRequestFromModal = async function (requestId, action) {
  window.closeFriendRequestModal();
  await window.handleFriendRequest(requestId, action);
};

// ================================
// PERSONAL INFORMATION
// ================================
window.saveProfile = async function () {
  const name = document.getElementById("nameInput").value.trim();
  if (!name) {
    showToast("Name cannot be empty", "warning");
    return;
  }
  try {
    const response = await apiCall("/api/users/me", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name }),
    });
    if (!response || !response.ok) throw new Error("Failed to update profile");
    showToast("Profile updated successfully", "success");
    currentUser.name = name;
    window.setUserData(currentUser);
    await loadProfile();
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
};

window.uploadPhoto = async function () {
  const fileInput = document.getElementById("photoFile");
  const file = fileInput.files[0];
  if (!file) return;
  const formData = new FormData();
  formData.append("file", file);
  try {
    const response = await fetch("/auth/profile/photo", {
      method: "POST",
      headers: { Authorization: `Bearer ${localStorage.getItem("jwtToken")}` },
      body: formData,
    });
    if (!response.ok) {
      const err = await response.text();
      throw new Error(err || "Upload failed");
    }
    showToast("Photo updated successfully", "success");
    const userResponse = await apiCall("/api/users/me", { skipDedupe: true });
    if (userResponse && userResponse.ok) {
      const user = await userResponse.json();
      window.setUserData(user);
      currentUser = user;
    }
    if (typeof window.clearAvatarCache === "function") {
      window.clearAvatarCache();
    }
    window.renderHeader();
    await loadProfile();
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
};

window.syncWithGoogle = async function () {
  const idToken = localStorage.getItem("googleIdToken");
  if (!idToken) {
    showToast("Please sign in with Google first", "warning");
    return;
  }

  try {
    const response = await apiCall("/api/users/me/sync-google", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ idToken }),
    });
    if (!response || !response.ok) {
      const err = await response.text();
      throw new Error(err || "Sync failed");
    }
    showToast("Profile synced with Google", "success");
    const userResponse = await apiCall("/api/users/me", { skipDedupe: true });
    if (userResponse && userResponse.ok) {
      const user = await userResponse.json();
      window.setUserData(user);
      currentUser = user;
    }
    await loadProfile();
  } catch (error) {
    showToast("Error syncing: " + error.message, "error");
  }
};

// ================================
// CHANGE PASSWORD MODAL
// ================================
window.showChangePasswordModal = function () {
  const modal = document.getElementById("changePasswordModal");
  if (!modal) {
    console.warn("Password modal not found in DOM");
    return;
  }
  modal.classList.add("active");
  document.getElementById("modalCurrentPassword").value = "";
  document.getElementById("modalNewPassword").value = "";
  document.getElementById("modalConfirmPassword").value = "";
  // Reset requirement indicators
  updateRequirement("reqLength", "reqLengthIcon", false);
  updateRequirement("reqCase", "reqCaseIcon", false);
  updateRequirement("reqDigit", "reqDigitIcon", false);
  updateRequirement("reqSpecial", "reqSpecialIcon", false);
  updateRequirement("reqCommon", "reqCommonIcon", false);
};

function hideChangePasswordModal() {
  const modal = document.getElementById("changePasswordModal");
  if (modal) modal.classList.remove("active");
}

async function submitPasswordChange() {
  const current = document.getElementById("modalCurrentPassword").value;
  const newPass = document.getElementById("modalNewPassword").value;
  const confirm = document.getElementById("modalConfirmPassword").value;

  if (!current || !newPass || !confirm) {
    showToast("Please fill in all fields", "warning");
    return;
  }
  if (newPass.length < 8) {
    showToast("New password must be at least 8 characters", "warning");
    return;
  }
  if (newPass !== confirm) {
    showToast("Passwords do not match", "warning");
    return;
  }

  try {
    const response = await apiCall("/api/users/me/password", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ oldPassword: current, newPassword: newPass }),
    });
    if (!response || !response.ok) {
      const err = await response.text();
      throw new Error(err || "Failed to update password");
    }
    showToast("Password updated successfully", "success");
    hideChangePasswordModal();
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
}

// ---- Password strength helpers ----
function validatePasswordStrength(password) {
  const hasMinLength = password.length >= 8;
  const hasUpperCase = /[A-Z]/.test(password);
  const hasLowerCase = /[a-z]/.test(password);
  const hasDigit = /\d/.test(password);
  const hasSpecial = /[@$!%*?&]/.test(password);
  const commonPasswords = [
    "password123",
    "password1",
    "12345678",
    "123456789",
    "qwerty123",
    "admin123",
    "letmein",
    "welcome1",
    "passw0rd",
    "hello123",
  ];
  const isCommon = commonPasswords.includes(password.toLowerCase());

  updateRequirement("reqLength", "reqLengthIcon", hasMinLength);
  updateRequirement("reqCase", "reqCaseIcon", hasUpperCase && hasLowerCase);
  updateRequirement("reqDigit", "reqDigitIcon", hasDigit);
  updateRequirement("reqSpecial", "reqSpecialIcon", hasSpecial);
  updateRequirement(
    "reqCommon",
    "reqCommonIcon",
    !isCommon && password.length > 0,
  );
}

function updateRequirement(containerId, iconId, isMet) {
  const container = document.getElementById(containerId);
  const icon = document.getElementById(iconId);
  if (container) {
    container.style.color = isMet ? "#48bb78" : "#a0aec0";
  }
  if (icon) {
    icon.textContent = isMet ? "✓" : "✗";
  }
}

function validateConfirmPassword() {
  const newPass = document.getElementById("modalNewPassword").value;
  const confirm = document.getElementById("modalConfirmPassword").value;
  const icon = document.getElementById("confirmMatchIcon");
  const text = document.getElementById("confirmMatchText");
  const container = document.getElementById("confirmMatch");

  if (!confirm) {
    icon.textContent = "✗";
    text.textContent = "Please confirm your password";
    container.style.color = "#a0aec0";
    return;
  }
  if (newPass === confirm) {
    icon.textContent = "✓";
    text.textContent = "Passwords match";
    container.style.color = "#48bb78";
  } else {
    icon.textContent = "✗";
    text.textContent = "Passwords do not match";
    container.style.color = "#e53e3e";
  }
}

// ================================
// PRIVACY SETTINGS MODAL
// ================================
function showPrivacySettingsModal() {
  const modal = document.getElementById("privacySettingsModal");
  if (!modal) return;
  document.getElementById("modalIncomingSharePrivacy").value =
    currentUser.incomingSharePrivacy || "EVERYONE";
  document.getElementById("modalFriendRequestPrivacy").value =
    currentUser.friendRequestPrivacy || "EVERYONE";
  document.getElementById("modalAutoApprove").checked =
    currentUser.autoApproveFriends || false;
  updatePrivacyHints();
  modal.classList.add("active");
}

function hidePrivacySettingsModal() {
  const modal = document.getElementById("privacySettingsModal");
  if (modal) modal.classList.remove("active");
}

function updatePrivacyHints() {
  const incoming = document.getElementById("modalIncomingSharePrivacy").value;
  const friendReq = document.getElementById("modalFriendRequestPrivacy").value;
  const autoApprove = document.getElementById("modalAutoApprove").checked;
  document.getElementById("modalIncomingSharePrivacyHint").textContent =
    incoming === "EVERYONE"
      ? "Anyone can share files with you."
      : incoming === "FRIENDS_ONLY"
        ? "Only your friends can share files with you."
        : "No one can share files with you.";
  document.getElementById("modalFriendRequestPrivacyHint").textContent =
    friendReq === "EVERYONE"
      ? "Anyone can send you a friend request."
      : "No one can send you friend requests.";
  document.getElementById("modalAutoApproveHint").textContent = autoApprove
    ? "Friend requests are automatically accepted."
    : "You must manually accept friend requests.";
}

async function savePrivacySettings() {
  const incoming = document.getElementById("modalIncomingSharePrivacy").value;
  const friendReq = document.getElementById("modalFriendRequestPrivacy").value;
  const autoApprove = document.getElementById("modalAutoApprove").checked;

  try {
    await window.updateIncomingSharePrivacy(incoming);
    await window.updateFriendRequestPrivacy(friendReq);
    await window.updateAutoApprove(autoApprove);
    showToast("Privacy settings updated", "success");
    hidePrivacySettingsModal();
    await loadProfile();
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
}

// ================================
// PRIVACY API UPDATES (existing)
// ================================
window.updateIncomingSharePrivacy = async function (value) {
  try {
    const response = await apiCall("/api/users/me/privacy/incoming-share", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ privacy: value }),
    });
    if (!response || !response.ok) throw new Error("Failed to update privacy");
    const userResponse = await apiCall("/api/users/me", { skipDedupe: true });
    if (userResponse && userResponse.ok) {
      const user = await userResponse.json();
      window.setUserData(user);
      currentUser = user;
    }
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
};

window.updateFriendRequestPrivacy = async function (value) {
  try {
    const response = await apiCall("/api/users/me/privacy/friend-requests", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ friendRequestPrivacy: value }),
    });
    if (!response || !response.ok)
      throw new Error("Failed to update friend request privacy");
    const userResponse = await apiCall("/api/users/me", { skipDedupe: true });
    if (userResponse && userResponse.ok) {
      const user = await userResponse.json();
      window.setUserData(user);
      currentUser = user;
    }
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
};

window.updateAutoApprove = async function (enabled) {
  try {
    const response = await apiCall(
      "/api/users/me/privacy/auto-approve-friends?enabled=" + enabled,
      { method: "PUT" },
    );
    if (!response || !response.ok)
      throw new Error("Failed to update auto-approve");
    const userResponse = await apiCall("/api/users/me", { skipDedupe: true });
    if (userResponse && userResponse.ok) {
      const user = await userResponse.json();
      window.setUserData(user);
      currentUser = user;
    }
  } catch (error) {
    showToast("Error: " + error.message, "error");
    document.getElementById("autoApproveToggle").checked = !enabled;
  }
};

// ================================
// FRIENDS
// ================================
window.handleFriendRequest = async function (requestId, action) {
  try {
    const response = await apiCall(`/api/friends/${action}/${requestId}`, {
      method: "PUT",
    });
    if (!response || !response.ok)
      throw new Error(`Failed to ${action} request`);
    showToast(`Request ${action}ed`, "success");
    await loadProfile();
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
};

window.removeFriend = async function (friendId) {
  const confirmed = await showConfirm(
    "Remove this friend? You will need to send a new request to add them again.",
    "Remove Friend",
    "Remove",
    "Cancel",
    "danger",
  );
  if (!confirmed) return;
  try {
    const response = await apiCall(`/api/friends/${friendId}`, {
      method: "DELETE",
    });
    if (!response || !response.ok) throw new Error("Failed to remove friend");
    showToast("Friend removed", "success");
    await loadProfile();
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
};

// ================================
// ACCOUNT MANAGEMENT
// ================================
window.deactivateAccount = async function () {
  const confirmed = await showConfirm(
    "Deactivating your account will prevent you from logging in. You can reactivate later by contacting support.",
    "Deactivate Account",
    "Deactivate",
    "Cancel",
    "danger",
  );
  if (!confirmed) return;
  try {
    const response = await apiCall("/api/users/me/deactivate", {
      method: "PUT",
    });
    if (!response || !response.ok)
      throw new Error("Failed to deactivate account");
    showToast("Account deactivated. You will be logged out.", "success");
    localStorage.removeItem("jwtToken");
    window.clearUserData();
    setTimeout(() => (window.location.href = "/login.html"), 1500);
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
};

window.deleteAccount = async function () {
  const confirmed = await showConfirm(
    "⚠️ This action is PERMANENT. All your files, shares, and data will be lost. This cannot be undone.",
    "Delete Account",
    "Delete Permanently",
    "Cancel",
    "danger",
  );
  if (!confirmed) return;
  const secondConfirm = await showConfirm(
    'Are you absolutely sure? Type "DELETE" to confirm.',
    "Final Confirmation",
    "Confirm",
    "Cancel",
    "danger",
  );
  if (!secondConfirm) return;
  try {
    const response = await apiCall("/api/users/me", { method: "DELETE" });
    if (!response || !response.ok) throw new Error("Failed to delete account");
    showToast("Account deleted successfully", "success");
    localStorage.removeItem("jwtToken");
    window.clearUserData();
    setTimeout(() => (window.location.href = "/login.html"), 1500);
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
};
