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

  // ❌ REMOVED: loadUserInfoIntoUI(); – header is handled by header.js

  await loadProfile();
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
            <!-- HEADER: gradient background with photo, name, email -->
            <div class="profile-header">
                <div class="profile-avatar-wrapper" id="photoPreview">
                    <div id="profilePhotoPlaceholder" class="initials">${(currentUser.name || currentUser.email).charAt(0).toUpperCase()}</div>
                </div>
                <div class="profile-name">${escapeHtml(currentUser.name || currentUser.email)}</div>
                <div class="profile-email">${escapeHtml(currentUser.email)}</div>
                <div class="header-actions">
                    <label for="photoFile">📷 Change Photo</label>
                    <input id="photoFile" type="file" accept="image/*" style="display:none;" onchange="window.uploadPhoto()">
                    ${
                      currentUser.provider === "GOOGLE"
                        ? `<button class="google-sync" onclick="window.syncWithGoogle()">🔄 Sync with Google</button>`
                        : ""
                    }
                </div>
            </div>

            <!-- BODY -->
            <div class="profile-body">

                <!-- ====== PERSONAL INFORMATION ====== -->
                <div class="section">
                    <div class="section-title">👤 Personal Information</div>
                    <div class="form-group">
                        <label for="nameInput">Full Name</label>
                        <input id="nameInput" type="text" value="${escapeHtml(currentUser.name || "")}" placeholder="Enter your full name">
                    </div>
                    <button class="btn-profile btn-primary" onclick="window.saveProfile()">💾 Save Changes</button>
                </div>

                <!-- ====== SECURITY ====== -->
                <div class="section">
                    <div class="section-title">🔒 Security</div>
                    <div class="form-row">
                        <div class="form-group">
                            <label for="oldPassword">Current Password</label>
                            <input id="oldPassword" type="password" placeholder="Enter current password">
                        </div>
                        <div class="form-group">
                            <label for="newPassword">New Password</label>
                            <input id="newPassword" type="password" placeholder="Enter new password">
                        </div>
                    </div>
                    <button class="btn-profile btn-secondary" onclick="window.changePassword()">🔑 Update Password</button>
                </div>

                <!-- ====== PRIVACY SETTINGS ====== -->
                <div class="section">
                    <div class="section-title">🔐 Privacy Settings</div>
                    <div class="privacy-section">
                        <!-- Who can share with me -->
                        <div class="privacy-row">
                            <label>Who can share with me?</label>
                            <select id="incomingSharePrivacySelect" onchange="window.updateIncomingSharePrivacy(this.value)">
                                <option value="EVERYONE" ${incomingSharePrivacy === "EVERYONE" ? "selected" : ""}>Everyone</option>
                                <option value="FRIENDS_ONLY" ${incomingSharePrivacy === "FRIENDS_ONLY" ? "selected" : ""}>Friends Only</option>
                                <option value="NOBODY" ${incomingSharePrivacy === "NOBODY" ? "selected" : ""}>Nobody</option>
                            </select>
                            <span class="privacy-hint">
                                ${
                                  incomingSharePrivacy === "EVERYONE"
                                    ? "Anyone can share files with you."
                                    : incomingSharePrivacy === "FRIENDS_ONLY"
                                      ? "Only your friends can share files with you."
                                      : "No one can share files with you."
                                }
                            </span>
                        </div>

                        <!-- Friend request privacy -->
                        <div class="privacy-row">
                            <label>Who can send friend requests?</label>
                            <select id="friendRequestPrivacySelect" onchange="window.updateFriendRequestPrivacy(this.value)">
                                <option value="EVERYONE" ${friendRequestPrivacy === "EVERYONE" ? "selected" : ""}>Everyone</option>
                                <option value="NOBODY" ${friendRequestPrivacy === "NOBODY" ? "selected" : ""}>Nobody</option>
                            </select>
                            <span class="privacy-hint">
                                ${
                                  friendRequestPrivacy === "EVERYONE"
                                    ? "Anyone can send you a friend request."
                                    : "No one can send you friend requests."
                                }
                            </span>
                        </div>

                        <!-- Auto-approve friends -->
                        <div class="privacy-row">
                            <label>Auto-approve friend requests</label>
                            <label class="toggle-switch">
                                <input type="checkbox" id="autoApproveToggle" ${autoApprove ? "checked" : ""} onchange="window.updateAutoApprove(this.checked)">
                                <span class="slider"></span>
                            </label>
                            <span class="privacy-hint">
                                ${autoApprove ? "Friend requests are automatically accepted." : "You must manually accept friend requests."}
                            </span>
                        </div>
                    </div>
                </div>

                <!-- ====== FRIENDS ====== -->
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

                <!-- ====== SEND FRIEND REQUEST ====== -->
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

                <!-- ====== SENT REQUESTS ====== -->
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

                <!-- ====== INCOMING REQUESTS ====== -->
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

                <!-- ====== ACCOUNT MANAGEMENT ====== -->
                <div class="section">
                    <div class="section-title">⚙️ Account Management</div>
                    <div class="account-actions">
                        <button class="btn-profile btn-danger" onclick="window.deactivateAccount()">🔒 Deactivate Account</button>
                        <button class="btn-profile btn-danger" onclick="window.deleteAccount()" style="background:#e53e3e;">🗑️ Delete Account</button>
                        ${
                          isAdmin
                            ? `
                            <button class="btn-profile btn-secondary" onclick="window.location.href='/admin-users.html'" style="background:#667eea; color:white;">👥 Manage Users</button>
                        `
                            : ""
                        }
                    </div>
                    <div style="margin-top:8px; font-size:12px; color:#a0aec0;">
                        ⚠️ Deactivating your account will prevent you from logging in. Deleting your account is permanent and cannot be undone.
                    </div>
                </div>

            </div> <!-- end profile-body -->
        </div> <!-- end profile-card -->
    `;

  container.innerHTML = html;
  // ❌ REMOVED: loadUserInfoIntoUI(); – header is self‑contained

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
// LOAD PROFILE PHOTO (signed URL)
// ================================
// ================================
// LOAD PROFILE PHOTO (signed URL with cache)
// ================================
async function loadProfilePhoto() {
  const previewContainer = document.getElementById("photoPreview");
  if (!previewContainer) return;

  const user = window.getUserData() || window.getUserFromToken();
  if (!user || !user.photoUrl) {
    // Show initials
    const initial = (user?.name || user?.email || "U").charAt(0).toUpperCase();
    previewContainer.innerHTML = `<div class="initials">${initial}</div>`;
    return;
  }

  // ---- CHECK CACHE FIRST ----
  const cachedDataUrl = window.getCachedAvatarDataUrl ? window.getCachedAvatarDataUrl() : null;
  if (cachedDataUrl) {
    console.log("🖼️ Profile: using cached avatar");
    previewContainer.innerHTML = `<img src="${cachedDataUrl}" alt="Profile photo" style="width:100%; height:100%; object-fit:cover;" />`;
    return;
  }

  // ---- NO CACHE – FETCH ----
  const token = localStorage.getItem("jwtToken");
  if (!token) {
    console.warn("No token – cannot load profile photo");
    return;
  }

  console.log("📡 Fetching signed URL for profile photo...");
  try {
    const response = await fetch(`/api/files/${user.photoUrl}/signed/session`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    const data = await response.json();
    const avatarUrl = data.url;

    // Download the image and convert to Data URL (to cache)
    const imgResponse = await fetch(avatarUrl);
    if (!imgResponse.ok) throw new Error(`Image download failed`);
    const blob = await imgResponse.blob();
    const reader = new FileReader();
    const dataUrl = await new Promise((resolve) => {
      reader.onload = () => resolve(reader.result);
      reader.readAsDataURL(blob);
    });

    // Store in cache
    if (window.setCachedAvatarDataUrl) {
      window.setCachedAvatarDataUrl(dataUrl, user.photoUrl);
      console.log("✅ Profile: avatar cached");
    }

    // Display
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
                    <button onclick="window.handleFriendRequest(${u.id}, 'accept')" 
                            style="background:#48bb78; color:white; border:none; border-radius:4px; padding:2px 12px; cursor:pointer; font-size:12px; margin-right:4px;">
                        Accept
                    </button>
                    <button onclick="window.handleFriendRequest(${u.id}, 'reject')" 
                            style="background:#fc8181; color:white; border:none; border-radius:4px; padding:2px 12px; cursor:pointer; font-size:12px;">
                        Reject
                    </button>
                `;
      } else {
        buttonHtml = `
                    <button onclick="window.sendFriendRequestDirect('${u.id}', '${escapeJS(u.email)}', '${escapeJS(u.name || u.email)}')" 
                            style="background:#48bb78; color:white; border:none; border-radius:4px; padding:2px 12px; cursor:pointer; font-size:12px;">
                        ➕ Send Request
                    </button>
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

  // Remove any existing modal
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
    // ❌ REMOVED: loadUserInfoIntoUI(); – header will pick up new data on next page load
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
    // Refresh cached user data
    const userResponse = await apiCall("/api/users/me", { skipDedupe: true });
    if (userResponse && userResponse.ok) {
      const user = await userResponse.json();
      window.setUserData(user);
      currentUser = user;
    }
    // Clear avatar cache so it will be reloaded
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
    // Refresh cached user data
    const userResponse = await apiCall("/api/users/me", { skipDedupe: true });
    if (userResponse && userResponse.ok) {
      const user = await userResponse.json();
      window.setUserData(user);
      currentUser = user;
    }
    // Clear avatar cache so it will be reloaded
    if (typeof window.clearCachedAvatarBlobUrl === "function") {
      window.clearCachedAvatarBlobUrl();
    }
    await loadProfile();
  } catch (error) {
    showToast("Error syncing: " + error.message, "error");
  }
};

// ================================
// SECURITY
// ================================
window.changePassword = async function () {
  const oldPassword = document.getElementById("oldPassword").value;
  const newPassword = document.getElementById("newPassword").value;
  if (!oldPassword || !newPassword) {
    showToast("Please fill in both password fields", "warning");
    return;
  }
  if (newPassword.length < 6) {
    showToast("New password must be at least 6 characters", "warning");
    return;
  }
  try {
    const response = await apiCall("/api/users/me/password", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ oldPassword, newPassword }),
    });
    if (!response || !response.ok) throw new Error("Failed to update password");
    showToast("Password updated successfully", "success");
    document.getElementById("oldPassword").value = "";
    document.getElementById("newPassword").value = "";
  } catch (error) {
    showToast("Error: " + error.message, "error");
  }
};

// ================================
// PRIVACY SETTINGS
// ================================
window.updateIncomingSharePrivacy = async function (value) {
  try {
    const response = await apiCall("/api/users/me/privacy/incoming-share", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ privacy: value }),
    });
    if (!response || !response.ok) throw new Error("Failed to update privacy");
    showToast("Privacy updated", "success");
    const userResponse = await apiCall("/api/users/me", { skipDedupe: true });
    if (userResponse && userResponse.ok) {
      const user = await userResponse.json();
      window.setUserData(user);
      currentUser = user;
    }
    await loadProfile();
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
    showToast("Friend request privacy updated", "success");
    const userResponse = await apiCall("/api/users/me", { skipDedupe: true });
    if (userResponse && userResponse.ok) {
      const user = await userResponse.json();
      window.setUserData(user);
      currentUser = user;
    }
    await loadProfile();
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
    showToast(`Auto-approve ${enabled ? "enabled" : "disabled"}`, "success");
    const userResponse = await apiCall("/api/users/me", { skipDedupe: true });
    if (userResponse && userResponse.ok) {
      const user = await userResponse.json();
      window.setUserData(user);
      currentUser = user;
    }
    await loadProfile();
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
