// shareView.js – View shared file/folder (share2.html)

const urlParams = new URLSearchParams(window.location.search);
const token = urlParams.get("token");
let currentShare = null;
let currentPassword = null;
let isExpired = false;
let selectedSharedItems = new Set();
let sharedCurrentFolderId = null;
let navHistory = [];

// ===== ENSURE REQUIRED DOM ELEMENTS EXIST =====
function ensureElement(id, className = "", defaultContent = "") {
  let el = document.getElementById(id);
  if (!el) {
    el = document.createElement("div");
    el.id = id;
    if (className) el.className = className;
    if (defaultContent) el.innerHTML = defaultContent;
    document.body.appendChild(el);
  }
  return el;
}

document.addEventListener("DOMContentLoaded", () => {
  ensureElement(
    "loading",
    "loading",
    '<div class="spinner"></div><div>Loading...</div>',
  );
  ensureElement("contentArea");
  ensureElement("viewerArea", "viewer-area");

  loadUserInfoIntoUI();
  if (!token) {
    showError("Invalid share link. No token provided.");
  } else {
    loadShareInfo();
  }

  // Close context menu on click outside
  document.addEventListener("click", () => {
    const menu = document.querySelector(".dropdown-menu");
    if (menu) menu.remove();
  });
});

// ===== USER INFO =====
function loadUserInfoIntoUI() {
  const jwtToken = localStorage.getItem("jwtToken");
  if (!jwtToken) return;
  const user = getUserFromToken ? getUserFromToken() : null;
  if (user) {
    document.getElementById("userEmail").textContent =
      user.name || user.email || "";
    const avatarEl = document.getElementById("userAvatar");
    if (user.photoUrl) {
      avatarEl.innerHTML = `<img src="${user.photoUrl}" alt="avatar" style="width:100%;height:100%;border-radius:50%;object-fit:cover;">`;
    } else {
      avatarEl.textContent = user.name
        ? user.name.charAt(0).toUpperCase()
        : user.email
          ? user.email.charAt(0).toUpperCase()
          : "U";
    }
  }
}

// ===== ERROR DISPLAYS =====
function showError(message) {
  const loading = document.getElementById("loading");
  if (loading) loading.style.display = "none";
  const contentArea = document.getElementById("contentArea");
  if (contentArea) {
    contentArea.style.display = "block";
    contentArea.innerHTML = `
            <div class="empty-state" style="text-align:center; padding:60px 20px;">
                <div class="empty-state-icon" style="font-size:48px;">🔗</div>
                <h3>Link Not Found</h3>
                <p>${escapeHtml(message)}</p>
            </div>
        `;
  }
}

function showShareNotFound() {
  const loading = document.getElementById("loading");
  if (loading) loading.style.display = "none";
  const contentArea = document.getElementById("contentArea");
  if (contentArea) {
    contentArea.style.display = "block";
    contentArea.innerHTML = `
            <div class="empty-state" style="text-align:center; padding:60px 20px;">
                <div class="empty-state-icon" style="font-size:48px;">❓</div>
                <h3>Share Never Existed</h3>
                <p>The share link you are trying to access was never created or has been permanently removed.</p>
                <p style="margin-top:16px; font-size:13px; color:#5f6368;">Please check the link or contact the owner.</p>
            </div>
        `;
  }
}

function showShareRemoved() {
  const loading = document.getElementById("loading");
  if (loading) loading.style.display = "none";
  const contentArea = document.getElementById("contentArea");
  if (contentArea) {
    contentArea.style.display = "block";
    contentArea.innerHTML = `
            <div class="empty-state" style="text-align:center; padding:60px 20px;">
                <div class="empty-state-icon" style="font-size:48px;">🚫</div>
                <h3>Share Removed by Sharer</h3>
                <p>This share link has been removed by the person who shared it and is no longer available.</p>
                <p style="margin-top:16px; font-size:13px; color:#5f6368;">Please contact the file owner for a new link.</p>
            </div>
        `;
  }
}

function showExpired(message) {
  const loading = document.getElementById("loading");
  if (loading) loading.style.display = "none";
  const contentArea = document.getElementById("contentArea");
  if (contentArea) {
    contentArea.style.display = "block";
    contentArea.innerHTML = `
            <div class="empty-state" style="text-align:center; padding:60px 20px;">
                <div class="empty-state-icon" style="font-size:48px;">⏰</div>
                <h3>Link Expired</h3>
                <p>${escapeHtml(message)}</p>
                <p style="margin-top:16px; font-size:13px; color:#5f6368;">Please contact the file owner for a new link.</p>
            </div>
        `;
  }
}

function showFileRemoved() {
  const loading = document.getElementById("loading");
  if (loading) loading.style.display = "none";
  const contentArea = document.getElementById("contentArea");
  if (contentArea) {
    contentArea.style.display = "block";
    contentArea.innerHTML = `
            <div class="empty-state" style="text-align:center; padding:60px 20px;">
                <div class="empty-state-icon" style="font-size:48px;">🗑️</div>
                <h3>File Removed</h3>
                <p>The file(s) that were shared have been deleted by the owner.</p>
                <p style="margin-top:16px; font-size:13px; color:#5f6368;">Please contact the owner for a new link.</p>
            </div>
        `;
  }
}

// ===== LOAD SHARE INFO =====
async function loadShareInfo() {
  try {
    const headers = {};
    const jwtToken = localStorage.getItem("jwtToken");
    if (jwtToken) headers["Authorization"] = "Bearer " + jwtToken;
    const response = await fetch(`${API_URL}/share/${token}`, { headers });

    if (response.status === 404) {
      let errorData;
      try {
        errorData = await response.json();
      } catch (e) {
        showError("Share not found.");
        return;
      }
      if (errorData.error === "SHARE_NOT_FOUND") {
        showShareNotFound();
      } else {
        showError("Share not found.");
      }
      return;
    }

    if (response.status === 410) {
      let errorData;
      try {
        errorData = await response.json();
      } catch (e) {
        showExpired("This share is no longer available.");
        return;
      }
      if (errorData.error === "SHARE_REMOVED") {
        showShareRemoved();
      } else if (errorData.error === "SHARE_EXPIRED") {
        showExpired("This share link has expired.");
      } else if (errorData.error === "SHARE_FILE_REMOVED") {
        showFileRemoved();
      } else {
        showExpired("This share is no longer available.");
      }
      return;
    }

    if (response.status === 401) {
      showError("You need to be logged in to access this share.");
      return;
    }

    if (!response.ok) throw new Error("Failed to load share information");

    currentShare = await response.json();
    if (
      currentShare.expiresAt &&
      new Date(currentShare.expiresAt) < new Date()
    ) {
      isExpired = true;
      showExpired("This share link has expired.");
      return;
    }
    await displayShareInfo(currentShare);
  } catch (error) {
    console.error("Error loading share:", error);
    showError(error.message);
  }
}

// ===== DISPLAY SHARE INFO =====
async function displayShareInfo(share) {
  const loading = document.getElementById("loading");
  if (loading) loading.style.display = "none";
  const contentArea = document.getElementById("contentArea");
  if (!contentArea) return;
  contentArea.style.display = "block";

  const isExpiredStatus =
    share.expiresAt && new Date(share.expiresAt) < new Date();
  const expiresText = isExpiredStatus
    ? "⚠️ This link has expired"
    : share.expiresAt
      ? `⏰ Expires on ${new Date(share.expiresAt).toLocaleDateString()}`
      : "Never expires";

  let html = `
        <div class="file-info" style="display:flex; align-items:center; gap:16px; padding:12px 0; border-bottom:1px solid #e8eaed; margin-bottom:16px;">
            <div class="file-icon" style="font-size:40px;">${getFileIcon(share.driveName)}</div>
            <div class="file-details">
                <div class="file-name" style="font-size:20px; font-weight:500;">${escapeHtml(share.driveName)}</div>
                <div class="file-meta" style="font-size:14px; color:#5f6368;">
                    Shared via ${share.shareType} link • ${share.viewCount || 0} views
                    <br><span style="font-size:13px;">Created ${new Date(share.createdAt).toLocaleDateString()} • ${expiresText}</span>
                </div>
            </div>
        </div>
    `;

  if (isExpiredStatus) {
    html += `<div class="empty-state" style="text-align:center; padding:20px;"><p style="color:#c5221f;">This link has expired. No further access is available.</p></div>`;
    contentArea.innerHTML = html;
    return;
  }

  if (share.driveType === "FOLDER" || share.driveType === "MULTI") {
    sharedCurrentFolderId = null;
    navHistory = [];
    await loadCurrentFolder();
    return;
  }

  // SINGLE FILE
  const permission = share.permission;
  const canView = permission === "VIEW" || permission === "VIEW_DOWNLOAD";
  const canDownload =
    permission === "DOWNLOAD" || permission === "VIEW_DOWNLOAD";

  if (share.shareType === "PROTECTED") {
    html += `
            <div class="password-section" style="background:#f8f9fa; border-radius:8px; padding:20px; text-align:center;">
                <label style="display:block; font-weight:500; margin-bottom:8px;">This file is password protected</label>
                <input type="password" id="password" class="password-input" placeholder="Enter password" style="width:100%; max-width:300px; padding:10px 16px; border:1px solid #dadce0; border-radius:24px; font-size:14px;">
                <div class="action-buttons" style="margin-top:16px;">
                    <button class="btn btn-primary" onclick="accessWithPassword()">Access File</button>
                </div>
            </div>
        `;
    contentArea.innerHTML = html;
  } else if (share.shareType === "USER_ONLY") {
    const jwtToken = localStorage.getItem("jwtToken");
    if (jwtToken) {
      html += `
                <div style="background:#e8f0fe; padding:12px 16px; border-radius:8px; margin:16px 0;">
                    <p>✅ You are logged in. You can access this file.</p>
                </div>
                <div class="action-buttons">
                    ${canView ? `<button class="btn btn-primary" onclick="loadRootFileContent()">👁️ View File</button>` : ""}
                    ${canDownload ? `<button class="btn btn-secondary" onclick="downloadRootFile()">⬇️ Download</button>` : ""}
                </div>
            `;
      contentArea.innerHTML = html;
    } else {
      html += `
                <div style="background:#fce8e6; padding:12px 16px; border-radius:8px; margin:16px 0;">
                    <p>🔐 This file is shared with registered users only. Please login to access.</p>
                </div>
                <button class="btn btn-primary" onclick="redirectToLogin()">Login to Access</button>
            `;
      contentArea.innerHTML = html;
    }
  } else {
    // PUBLIC
    html += `
            <div class="action-buttons">
                ${canView ? `<button class="btn btn-primary" onclick="loadRootFileContent()">👁️ View File</button>` : ""}
                ${canDownload ? `<button class="btn btn-secondary" onclick="downloadRootFile()">⬇️ Download</button>` : ""}
            </div>
        `;
    contentArea.innerHTML = html;
  }
}

// ===== FOLDER NAVIGATION =====
async function loadCurrentFolder() {
  const contentArea = document.getElementById("contentArea");
  if (!contentArea) return;
  const folderId = sharedCurrentFolderId;
  const endpoint = folderId
    ? `/share/${token}/folder/${folderId}/contents`
    : `/share/${token}/contents`;
  const permission = currentShare ? currentShare.permission : null;
  const canDownload =
    permission === "DOWNLOAD" || permission === "VIEW_DOWNLOAD";
  const canView = permission === "VIEW" || permission === "VIEW_DOWNLOAD";

  // Missing files note (only for MULTI)
  let missingNote = "";
  if (
    currentShare &&
    currentShare.driveType === "MULTI" &&
    currentShare.totalFileCount !== undefined &&
    currentShare.activeFileCount !== undefined &&
    currentShare.activeFileCount < currentShare.totalFileCount
  ) {
    missingNote = `
            <div style="background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 10px 16px; margin-bottom: 16px; border-radius: 4px; display: flex; align-items: center; gap: 10px;">
                <span style="font-size: 20px;">⚠️</span>
                <span style="color: #856404;">
                    <strong>Note:</strong> ${currentShare.totalFileCount - currentShare.activeFileCount} file(s) in this share have been removed by the owner.
                    Only the remaining ${currentShare.activeFileCount} file(s) are shown below.
                </span>
            </div>
        `;
  }

  contentArea.innerHTML = `
        <div class="toolbar">
            <div class="breadcrumb" id="folderBreadcrumb">
                <span class="breadcrumb-item" onclick="navigateToFolder(null)">${escapeHtml(currentShare.driveName)}</span>
            </div>
            <div class="action-buttons">
                ${
                  canDownload
                    ? `
                    <input type="checkbox" id="selectAllCheckbox" title="Select All" onchange="toggleSelectAllShared()" />
                    <button class="btn btn-secondary" onclick="downloadSelectedShared()">⬇️ Download Selected</button>
                `
                    : ""
                }
            </div>
        </div>
        ${missingNote}
        <div id="selectionToolbar" class="selection-toolbar" style="display:none;">
            <span id="selectionCount">0 items selected</span>
        </div>
        <div id="folderGrid" class="file-grid"></div>
    `;

  renderBreadcrumb();

  try {
    const headers = {};
    const jwtToken = localStorage.getItem("jwtToken");
    if (jwtToken) headers["Authorization"] = "Bearer " + jwtToken;
    const response = await fetch(`${API_URL}${endpoint}`, { headers });
    if (!response.ok)
      throw new Error(
        `Failed to load folder contents (Status: ${response.status})`,
      );
    const items = await response.json();
    renderFolderGrid(items);
  } catch (error) {
    const grid = document.getElementById("folderGrid");
    if (grid)
      grid.innerHTML = `<div class="empty-state">❌ ${escapeHtml(error.message)}</div>`;
    safeToast("Failed to load folder contents: " + error.message, "error");
  }
}

function renderBreadcrumb() {
  const container = document.getElementById("folderBreadcrumb");
  if (!container) return;
  if (!sharedCurrentFolderId) {
    container.innerHTML = `<span class="breadcrumb-item" onclick="navigateToFolder(null)">${escapeHtml(currentShare.driveName)}</span>`;
    return;
  }
  let html = `<span class="breadcrumb-item" onclick="navigateToFolder(null)">${escapeHtml(currentShare.driveName)}</span>`;
  for (let i = 0; i < navHistory.length; i++) {
    const item = navHistory[i];
    html += `<span class="breadcrumb-separator">/</span>`;
    html += `<span class="breadcrumb-item" onclick="navigateToFolder('${escapeJSString(item.id)}')">${escapeHtml(item.name)}</span>`;
  }
  container.innerHTML = html;
}

async function navigateToFolder(folderId, folderName) {
  // Check view permission before navigating
  const canView =
    currentShare.permission === "VIEW" ||
    currentShare.permission === "VIEW_DOWNLOAD";
  if (!canView) {
    safeToast("You do not have permission to view this folder.", "warning");
    return;
  }

  if (folderId === null) {
    sharedCurrentFolderId = null;
    navHistory = [];
  } else {
    if (
      navHistory.length === 0 ||
      navHistory[navHistory.length - 1].id !== folderId
    ) {
      navHistory.push({ id: folderId, name: folderName });
    }
    sharedCurrentFolderId = folderId;
  }
  await loadCurrentFolder();
}

// ===== RENDER FOLDER GRID with context menu =====
function renderFolderGrid(items) {
  const grid = document.getElementById("folderGrid");
  if (!grid) return;
  if (!items || items.length === 0) {
    grid.innerHTML = '<div class="empty-state">📁 This folder is empty.</div>';
    return;
  }

  const permission = currentShare.permission;
  const canDownload =
    permission === "DOWNLOAD" || permission === "VIEW_DOWNLOAD";
  const canView = permission === "VIEW" || permission === "VIEW_DOWNLOAD";

  let html = "";
  items.forEach((item) => {
    const isSelected = selectedSharedItems.has(item.id);
    const icon = item.driveType === "FILE" ? getFileIcon(item.name) : "📁";
    const info =
      item.driveType === "FILE" ? formatFileSize(item.size) : "Folder";
    const isFolder = item.driveType === "FOLDER";
    const eId = escapeJSString(item.id);
    const eName = escapeJSString(item.name);

    // Double‑click: open folder (if view) or view file (if view) else download (if download)
    let doubleClickAction = "";
    if (isFolder) {
      if (canView) {
        doubleClickAction = `ondblclick="navigateToFolder('${eId}', '${eName}')"`;
      } else {
        doubleClickAction = `ondblclick="safeToast('You do not have permission to open this folder.', 'warning')"`;
      }
    } else {
      if (canView) {
        doubleClickAction = `ondblclick="viewSharedFile('${eId}', '${eName}')"`;
      } else if (canDownload) {
        doubleClickAction = `ondblclick="downloadSharedFile('${eId}', '${eName}')"`;
      }
    }

    html += `
            <div class="file-item ${isSelected ? "selected" : ""}" 
                 data-id="${item.id}" 
                 data-type="${item.driveType}" 
                 data-name="${escapeHtml(item.name)}"
                 ${doubleClickAction}
                 onclick="handleSharedItemClick(event, '${eId}')"
                 oncontextmenu="showSharedContextMenu(event, '${eId}', '${eName}', '${item.driveType}')">
                ${canDownload ? `<input type="checkbox" class="checkbox" ${isSelected ? "checked" : ""} onchange="toggleSharedSelection('${eId}', this.checked)">` : ""}
                <div class="file-icon">${icon}</div>
                <div class="file-name">${escapeHtml(item.name)}</div>
                <div class="file-info">${info}</div>
                <div class="file-menu" onclick="event.stopPropagation(); showSharedContextMenu(event, '${eId}', '${eName}', '${item.driveType}')">⋮</div>
            </div>
        `;
  });

  grid.innerHTML = html;
  updateSelectionToolbar();
}

// ===== SHARED CONTEXT MENU =====
function showSharedContextMenu(event, id, name, type) {
  event.preventDefault();
  event.stopPropagation();

  // Remove any existing menu
  const existing = document.querySelector(".dropdown-menu");
  if (existing) existing.remove();

  const permission = currentShare.permission;
  const canView = permission === "VIEW" || permission === "VIEW_DOWNLOAD";
  const canDownload =
    permission === "DOWNLOAD" || permission === "VIEW_DOWNLOAD";

  const menu = document.createElement("div");
  menu.className = "dropdown-menu show";
  menu.style.position = "fixed";
  menu.style.top = `${Math.min(event.clientY, window.innerHeight - 200)}px`;
  menu.style.left = `${Math.min(event.clientX, window.innerWidth - 180)}px`;
  menu.style.zIndex = "9999";
  menu.style.background = "white";
  menu.style.borderRadius = "8px";
  menu.style.boxShadow = "0 4px 16px rgba(0,0,0,0.15)";
  menu.style.minWidth = "160px";
  menu.style.padding = "6px 0";

  const isFolder = type === "FOLDER";

  const items = [];

  if (isFolder) {
    if (canView) {
      items.push({
        icon: "📂",
        label: "Open",
        action: () => navigateToFolder(id, name),
      });
    } else {
      items.push({
        icon: "🚫",
        label: "Open (no permission)",
        action: () =>
          safeToast(
            "You do not have permission to view this folder.",
            "warning",
          ),
      });
    }
    if (canDownload) {
      items.push({
        icon: "⬇️",
        label: "Download Folder as ZIP",
        action: () => downloadSharedFolder(id, name),
      });
    }
  } else {
    // File
    if (canView) {
      items.push({
        icon: "👁️",
        label: "View",
        action: () => viewSharedFile(id, name),
      });
      items.push({
        icon: "🔄",
        label: "View in new tab",
        action: () => viewSharedFileInNewTab(id, name),
      });
    }
    if (canDownload) {
      items.push({
        icon: "⬇️",
        label: "Download",
        action: () => downloadSharedFile(id, name),
      });
    }
  }

  if (items.length === 0) {
    items.push({ icon: "ℹ️", label: "No actions available", action: () => {} });
  }

  items.forEach((item) => {
    const div = document.createElement("div");
    div.className = "dropdown-item";
    div.style.padding = "8px 16px";
    div.style.cursor = "pointer";
    div.style.display = "flex";
    div.style.alignItems = "center";
    div.style.gap = "10px";
    div.style.fontSize = "14px";
    div.innerHTML = `${item.icon} ${item.label}`;
    div.onclick = (e) => {
      e.stopPropagation();
      item.action();
      menu.remove();
    };
    div.onmouseenter = () => {
      div.style.background = "#f1f3f4";
    };
    div.onmouseleave = () => {
      div.style.background = "transparent";
    };
    menu.appendChild(div);
  });

  document.body.appendChild(menu);

  // Auto-close on click outside
  setTimeout(() => {
    document.addEventListener("click", () => menu.remove(), { once: true });
  }, 10);
}

// ===== DOWNLOAD SHARED FOLDER (as ZIP) =====
async function downloadSharedFolder(folderId, folderName) {
  safeToast("Preparing folder download...", "info", 2000);
  try {
    const response = await fetch(
      `${API_URL}/share/download/${token}?fileId=${encodeURIComponent(folderId)}&type=folder`,
      {
        method: "GET",
        headers: {
          Authorization: "Bearer " + (localStorage.getItem("jwtToken") || ""),
        },
      },
    );
    if (!response.ok) throw new Error("Download failed");
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${folderName}.zip`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    safeToast("Download completed.", "success");
  } catch (error) {
    safeToast("Failed to download folder: " + error.message, "error");
  }
}

// ===== VIEW SHARED FILE IN NEW TAB =====
function viewSharedFileInNewTab(fileId, fileName) {
  let url = `${API_URL}/share/stream/${token}?fileId=${encodeURIComponent(fileId)}`;
  if (currentPassword)
    url += `&password=${encodeURIComponent(currentPassword)}`;
  window.open(url, "_blank");
}

// ===== SELECTION HANDLING =====
function handleSharedItemClick(event, id) {
  if (
    event.target.type === "checkbox" ||
    event.target.classList.contains("file-menu")
  )
    return;
  if (!event.ctrlKey && !event.metaKey) {
    if (!selectedSharedItems.has(id)) {
      selectedSharedItems.clear();
      selectedSharedItems.add(id);
      loadCurrentFolder();
    }
  } else {
    const checked = !selectedSharedItems.has(id);
    toggleSharedSelection(id, checked);
  }
}

function toggleSharedSelection(id, checked) {
  const itemDiv = document.querySelector(`.file-item[data-id="${id}"]`);
  if (checked) {
    selectedSharedItems.add(id);
    if (itemDiv) itemDiv.classList.add("selected");
  } else {
    selectedSharedItems.delete(id);
    if (itemDiv) itemDiv.classList.remove("selected");
  }
  updateSelectionToolbar();
}

function toggleSelectAllShared() {
  const allItems = document.querySelectorAll(".file-item");
  if (allItems.length === 0) return;
  const allChecked = Array.from(allItems).every((item) => {
    const cb = item.querySelector(".checkbox");
    return cb && cb.checked;
  });
  const newState = !allChecked;
  selectedSharedItems.clear();
  allItems.forEach((item) => {
    const cb = item.querySelector(".checkbox");
    if (cb) {
      cb.checked = newState;
      const id = item.dataset.id;
      if (newState) {
        selectedSharedItems.add(id);
        item.classList.add("selected");
      } else {
        item.classList.remove("selected");
      }
    }
  });
  updateSelectionToolbar();
}

function updateSelectionToolbar() {
  const toolbar = document.getElementById("selectionToolbar");
  const count = selectedSharedItems.size;
  if (count > 0) {
    toolbar.style.display = "flex";
    document.getElementById("selectionCount").innerText =
      `${count} item${count > 1 ? "s" : ""} selected`;
  } else {
    toolbar.style.display = "none";
  }
}

async function downloadSelectedShared() {
  const ids = Array.from(selectedSharedItems);
  if (ids.length === 0) {
    safeToast("Select at least one item.", "warning");
    return;
  }
  safeToast("Preparing ZIP...", "info", 2000);
  try {
    const response = await fetch(
      `${API_URL}/download/bulk/shared?token=${token}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(ids),
      },
    );
    if (!response.ok) throw new Error("Download failed");
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "download.zip";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    safeToast("Download completed.", "success");
  } catch (error) {
    safeToast("Download failed: " + error.message, "error");
  }
}

// ===== VIEW / DOWNLOAD SINGLE FILE =====
async function viewSharedFile(fileId, fileName) {
  const viewerArea = document.getElementById("viewerArea");
  if (!viewerArea) return;
  viewerArea.style.display = "block";
  viewerArea.innerHTML =
    '<div class="loading"><div class="spinner"></div><div>Loading file...</div></div>';
  try {
    let url = `${API_URL}/share/stream/${token}?fileId=${encodeURIComponent(fileId)}`;
    if (currentPassword)
      url += `&password=${encodeURIComponent(currentPassword)}`;
    const headers = {};
    const jwtToken = localStorage.getItem("jwtToken");
    if (jwtToken) headers["Authorization"] = "Bearer " + jwtToken;
    const response = await fetch(url, { headers });
    if (response.status === 403) throw new Error("Access denied.");
    if (response.status === 410) throw new Error("Link expired");
    if (response.status === 404)
      throw new Error(
        "The shared file has been deleted or is no longer available.",
      );
    if (!response.ok) throw new Error("Failed to load file");
    const blob = await response.blob();
    const fileUrl = URL.createObjectURL(blob);
    const contentType = blob.type;
    const ext = fileName.split(".").pop().toLowerCase();
    displayFileInViewer(fileUrl, contentType, fileName, ext);
  } catch (error) {
    viewerArea.innerHTML = `<div class="empty-state">❌ ${escapeHtml(error.message)}</div>`;
    safeToast(error.message, "error");
  }
}

async function downloadSharedFile(fileId, fileName) {
  let url = `${API_URL}/share/download/${token}?fileId=${encodeURIComponent(fileId)}`;
  if (currentPassword)
    url += `&password=${encodeURIComponent(currentPassword)}`;
  safeToast("Preparing download...", "info", 2000);
  try {
    const response = await fetch(url);
    if (!response.ok) throw new Error("Download failed");
    const blob = await response.blob();
    const fileUrl = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = fileUrl;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(fileUrl);
    safeToast("Download completed.", "success");
  } catch (error) {
    safeToast("Download failed: " + error.message, "error");
  }
}

// ===== FILE VIEWER (inline) =====
function displayFileInViewer(fileUrl, contentType, filename, ext) {
  const viewerArea = document.getElementById("viewerArea");
  if (!viewerArea) return;
  const canDownload =
    currentShare.permission === "DOWNLOAD" ||
    currentShare.permission === "VIEW_DOWNLOAD";

  if (contentType.startsWith("image/")) {
    viewerArea.innerHTML = `<div style="text-align:center;"><img src="${fileUrl}" alt="${filename}" style="max-width:100%; max-height:80vh;"></div>`;
  } else if (contentType === "application/pdf") {
    viewerArea.innerHTML = `<iframe src="${fileUrl}" style="width:100%; height:600px; border:none;"></iframe>`;
  } else if (contentType.startsWith("video/")) {
    viewerArea.innerHTML = `<video controls autoplay style="max-width:100%; max-height:80vh;"><source src="${fileUrl}" type="${contentType}">Your browser does not support video.</video>`;
  } else if (contentType.startsWith("audio/")) {
    viewerArea.innerHTML = `<audio controls autoplay><source src="${fileUrl}" type="${contentType}"></audio>`;
  } else if (
    contentType.startsWith("text/") ||
    ["txt", "log", "md", "csv", "json", "xml", "html", "css", "js"].includes(
      ext,
    )
  ) {
    fetch(fileUrl)
      .then((res) => res.text())
      .then((text) => {
        viewerArea.innerHTML = `<pre style="background:#f1f3f4; padding:16px; border-radius:4px; white-space:pre-wrap; max-height:600px; overflow:auto;">${escapeHtml(text)}</pre>`;
      })
      .catch(() => {
        viewerArea.innerHTML = `<div class="empty-state">Failed to load text content.</div>`;
      });
  } else {
    viewerArea.innerHTML = `
            <div style="text-align:center; padding:40px;">
                <div style="font-size:64px;">📄</div>
                <p>Preview not available for this file type.</p>
                ${canDownload ? `<button class="btn btn-primary" onclick="downloadRootFile()">⬇️ Download File</button>` : ""}
            </div>
        `;
  }
  URL.revokeObjectURL(fileUrl);
}

// ===== DOWNLOAD ROOT FILE (single file share) =====
async function downloadRootFile() {
  downloadFile();
}

async function downloadFile() {
  let url = `${API_URL}/share/download/${token}`;
  if (currentPassword)
    url += `?password=${encodeURIComponent(currentPassword)}`;
  safeToast("Preparing download...", "info", 2000);
  try {
    const response = await fetch(url);
    if (!response.ok) throw new Error("Download failed");
    const blob = await response.blob();
    const fileUrl = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = fileUrl;
    a.download = currentShare ? currentShare.driveName : "download";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(fileUrl);
    safeToast("Download completed.", "success");
  } catch (error) {
    safeToast("Download failed: " + error.message, "error");
  }
}

// ===== VIEW ROOT FILE (single file share) =====
async function loadRootFileContent() {
  const viewerArea = document.getElementById("viewerArea");
  if (!viewerArea) return;
  viewerArea.style.display = "block";
  viewerArea.innerHTML =
    '<div class="loading"><div class="spinner"></div><div>Loading file...</div></div>';
  try {
    let url = `${API_URL}/share/stream/${token}`;
    if (currentPassword)
      url += `?password=${encodeURIComponent(currentPassword)}`;
    const headers = {};
    const jwtToken = localStorage.getItem("jwtToken");
    if (jwtToken) headers["Authorization"] = "Bearer " + jwtToken;
    const response = await fetch(url, { headers });
    if (response.status === 403) throw new Error("Access denied.");
    if (response.status === 410) throw new Error("Link expired");
    if (response.status === 404)
      throw new Error(
        "The shared file has been deleted or is no longer available.",
      );
    if (!response.ok) throw new Error("Failed to load file");
    const blob = await response.blob();
    const fileUrl = URL.createObjectURL(blob);
    const contentType = blob.type;
    const ext = currentShare.driveName.split(".").pop().toLowerCase();
    displayFileInViewer(fileUrl, contentType, currentShare.driveName, ext);
  } catch (error) {
    viewerArea.innerHTML = `<div class="empty-state">❌ ${escapeHtml(error.message)}</div>`;
    safeToast(error.message, "error");
  }
}

// ===== PASSWORD ACCESS =====
async function accessWithPassword() {
  const password = document.getElementById("password").value;
  if (!password) {
    safeToast("Please enter password", "warning");
    return;
  }
  const button = document.querySelector("#contentArea .btn-primary");
  if (button) {
    button.disabled = true;
    button.textContent = "Verifying...";
  }
  currentPassword = password;
  try {
    const response = await fetch(
      `${API_URL}/share/${token}?password=${encodeURIComponent(password)}`,
    );
    if (response.status === 403) throw new Error("Invalid password");
    if (!response.ok) throw new Error("Access denied");
    await loadShareInfo();
    safeToast("Access granted.", "success");
  } catch (error) {
    safeToast("Invalid password. Please try again.", "error");
    if (button) {
      button.disabled = false;
      button.textContent = "Access File";
    }
  }
}

// ===== REDIRECT TO LOGIN =====
function redirectToLogin() {
  localStorage.setItem("redirectAfterLogin", window.location.href);
  window.location.href = "/login.html";
}

// ===== UTILITY =====
function escapeJSString(str) {
  if (!str) return "";
  return str.replace(/\\/g, "\\\\").replace(/'/g, "\\'").replace(/"/g, '\\"');
}

// Expose globals
window.navigateToFolder = navigateToFolder;
window.toggleSharedSelection = toggleSharedSelection;
window.toggleSelectAllShared = toggleSelectAllShared;
window.downloadSelectedShared = downloadSelectedShared;
window.viewSharedFile = viewSharedFile;
window.downloadSharedFile = downloadSharedFile;
window.loadRootFileContent = loadRootFileContent;
window.downloadRootFile = downloadRootFile;
window.accessWithPassword = accessWithPassword;
window.redirectToLogin = redirectToLogin;
window.handleSharedItemClick = handleSharedItemClick;
window.showSharedContextMenu = showSharedContextMenu;
window.viewSharedFileInNewTab = viewSharedFileInNewTab;
window.downloadSharedFolder = downloadSharedFolder;
