// app.js – Main file manager (final) – with multi-share & adaptive menu

// Globals from config.js
// currentFolderId, allFiles, selectedItems, jwtToken, etc.

// ============================
// INIT
// ============================
document.addEventListener("DOMContentLoaded", async () => {
  console.log("✅ DOMContentLoaded fired");
  if (!jwtToken) {
    window.location.href = "/login.html";
    return;
  }
  setupEventListeners();
  await loadFiles();
  setupSidebarNavigation();
  setupSearch();
  setupModalOverlayListeners();
  patchCloseModal();
});

function setupEventListeners() {
  const newFolderBtn = document.getElementById("newFolderBtn");
  const uploadBtn = document.getElementById("uploadBtn");
  const fileInput = document.getElementById("fileInput");
  const createFolderBtn = document.getElementById("createFolderBtn");
  const shareType = document.getElementById("shareType");

  if (newFolderBtn)
    newFolderBtn.addEventListener("click", () => showCreateFolderModal());
  if (uploadBtn) uploadBtn.addEventListener("click", () => fileInput.click());
  if (fileInput)
    fileInput.addEventListener("change", (e) => {
      if (e.target.files.length > 0) {
        console.log("File input changed, calling addToUploadQueue");
        addToUploadQueue(e.target.files);
        e.target.value = "";
      }
    });
  if (createFolderBtn) createFolderBtn.addEventListener("click", createFolder);
  if (shareType) shareType.addEventListener("change", toggleShareOptions);
}

function setupSearch() {
  const searchInput = document.getElementById("searchInput");
  if (searchInput) {
    searchInput.addEventListener("input", (e) => {
      const term = e.target.value.toLowerCase();
      const filtered = allFiles.filter((f) =>
        f.name.toLowerCase().includes(term),
      );
      renderFiles(filtered);
    });
  }
}

// ============================
// MODAL CLOSE PATCH
// ============================
function patchCloseModal() {
  const originalCloseModal = window.closeModal;
  window.closeModal = function (modalId) {
    if (
      modalId === "renameModal" ||
      modalId === "moveModal" ||
      modalId === "folderModal" ||
      modalId === "shareModal" ||
      modalId === "shareLinkModal"
    ) {
      closeModalById(modalId);
    } else {
      if (typeof originalCloseModal === "function") {
        originalCloseModal(modalId);
      }
    }
  };
}

function setupModalOverlayListeners() {
  document.querySelectorAll(".modal-overlay").forEach((overlay) => {
    overlay.addEventListener("click", function (e) {
      if (e.target === this) {
        const id = this.id;
        if (
          id === "renameModal" ||
          id === "moveModal" ||
          id === "folderModal" ||
          id === "shareModal" ||
          id === "shareLinkModal" ||
          id === "uploadModal"
        ) {
          closeModalById(id);
        }
      }
    });
  });
  document.addEventListener("keydown", function (e) {
    if (e.key === "Escape") {
      const activeModals = document.querySelectorAll(".modal-overlay.active");
      activeModals.forEach((modal) => closeModalById(modal.id));
    }
  });
}

// ============================
// LOAD FILES
// ============================
async function loadFiles() {
  console.log("🔍 loadFiles called, currentFolderId:", currentFolderId);
  const container = document.getElementById("fileContainer");
  if (!container) {
    console.error("fileContainer not found");
    return;
  }
  showLoading(container, true);
  try {
    const files = await getFilesForFolder(currentFolderId);
    console.log("📦 loadFiles response:", files);
    allFiles = files;
    renderFiles(files);
    updateBreadcrumb();
  } catch (error) {
    console.error("❌ Error loading files:", error);
    showError(container);
    safeToast("Failed to load files: " + error.message, "error");
  }
}

// ============================
// RENDER FILES (updated layout)
// ============================
function renderFiles(files) {
  const container = document.getElementById("fileContainer");
  if (!container) return;
  if (!files || files.length === 0) {
    showEmptyState(container);
    return;
  }
  const folders = files.filter(
    (f) => f.driveType === "FOLDER" || f.driveType === "ROOT",
  );
  const fileItems = files.filter((f) => f.driveType === "FILE");

  let html = '<div class="file-grid">';
  folders.forEach((item) => {
    const isSelected = selectedItems.has(item.id);
    const icon = "📁";
    const info = item.hasChildren ? "Contains items" : "Empty";
    const isProtected = item.accessType === "PROTECTED";
    const lockIcon = isProtected ? "🔒 " : "";
    const safeId = encodeURIComponent(item.id);
    const safeName = encodeURIComponent(item.name).replace(/'/g, "%27");
    const safeType = encodeURIComponent(item.driveType);
    const safeFileType = encodeURIComponent(item.fileType || "");
    const safeParentId = item.parentId ? encodeURIComponent(item.parentId) : "";

    html += `
            <div class="file-item ${isSelected ? "selected" : ""}" 
                 data-id="${item.id}" 
                 data-parent-id="${item.parentId || ""}"
                 data-type="${item.driveType}" 
                 data-name="${escapeHtml(item.name)}"
                 ondblclick="openFolder(decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'), ${isProtected})"
                 onclick="handleItemClick(event, decodeURIComponent('${safeId}'), true)">
                <input type="checkbox" class="checkbox" ${isSelected ? "checked" : ""} onchange="toggleSelectItem(event, decodeURIComponent('${safeId}'), this.checked)">
                <div class="file-icon">${icon}</div>
                <div class="file-name">${lockIcon}${escapeHtml(item.name)}</div>
                <div class="file-info">${info}</div>
                <div class="file-menu" onclick="showContextMenu(event, decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'), decodeURIComponent('${safeType}'), decodeURIComponent('${safeFileType}'), ${isProtected}, '${safeParentId}' ? decodeURIComponent('${safeParentId}') : null)">
                    ⋮
                </div>
            </div>
        `;
  });

  fileItems.forEach((item) => {
    const isSelected = selectedItems.has(item.id);
    const icon = getFileIcon(item.name);
    const info = formatFileSize(item.fileSize);
    const isProtected = item.accessType === "PROTECTED";
    const lockIcon = isProtected ? "🔒 " : "";
    const safeId = encodeURIComponent(item.id);
    const safeName = encodeURIComponent(item.name).replace(/'/g, "%27");
    const safeType = encodeURIComponent(item.driveType);
    const safeFileType = encodeURIComponent(item.fileType || "");
    const safeParentId = item.parentId ? encodeURIComponent(item.parentId) : "";
    const isViewable =
      item.fileType &&
      (item.fileType.startsWith("image/") ||
        item.fileType === "application/pdf" ||
        item.fileType.startsWith("video/"));

    let doubleClickAction = isViewable
      ? `viewFile(decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'))`
      : `downloadFile(decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'))`;

    html += `
            <div class="file-item ${isSelected ? "selected" : ""}" 
                 data-id="${item.id}" 
                 data-parent-id="${item.parentId || ""}"
                 data-type="${item.driveType}" 
                 data-name="${escapeHtml(item.name)}"
                 ondblclick="${doubleClickAction}"
                 onclick="handleItemClick(event, decodeURIComponent('${safeId}'), false)">
                <input type="checkbox" class="checkbox" ${isSelected ? "checked" : ""} onchange="toggleSelectItem(event, decodeURIComponent('${safeId}'), this.checked)">
                <div class="file-icon">${icon}</div>
                <div class="file-name">${lockIcon}${escapeHtml(item.name)}</div>
                <div class="file-info">${info}</div>
                <div class="file-menu" onclick="showContextMenu(event, decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'), decodeURIComponent('${safeType}'), decodeURIComponent('${safeFileType}'), ${isProtected}, '${safeParentId}' ? decodeURIComponent('${safeParentId}') : null)">
                    ⋮
                </div>
            </div>
        `;
  });

  html += "</div>";
  container.innerHTML = html;
  updateSelectionToolbar();
  container.style.height = "auto";
  container.style.minHeight = "400px";
  container.style.overflow = "visible";
}

// ============================
// EMPTY / ERROR / LOADING
// ============================
function showLoading(container, show) {
  if (show) container.innerHTML = '<div class="loading">Loading...</div>';
}
function showEmptyState(container, message = "This folder is empty") {
  container.innerHTML = `
        <div class="empty-state">
            <div class="empty-state-icon">📁</div>
            <div>${message}</div>
            <div style="font-size:12px; margin-top:8px;">Click "Upload File" or "New Folder" to add items</div>
        </div>
    `;
}
function showError(container, message = "Error loading files") {
  container.innerHTML = `
        <div class="empty-state">
            <div class="empty-state-icon">❌</div>
            <div>${message}</div>
            <div style="font-size:12px; margin-top:8px;">Please try refreshing the page</div>
        </div>
    `;
}

// ============================
// NAVIGATION
// ============================
function openFolder(folderId, folderName, isProtected) {
  if (isProtected) {
    const password = prompt(
      `Folder "${folderName}" is password protected. Enter password:`,
    );
    if (!password) return;
  }
  navigateToFolder(folderId);
}
function navigateToFolder(folderId) {
  currentFolderId = folderId;
  selectedItems.clear();
  loadFiles();
}
function navigateToParent() {
  currentFolderId = null;
  selectedItems.clear();
  loadFiles();
}
async function updateBreadcrumb() {
  const breadcrumb = document.getElementById("breadcrumb");
  if (!currentFolderId) {
    breadcrumb.innerHTML =
      '<span class="breadcrumb-item" onclick="navigateToParent()">My Drive</span>';
    return;
  }
  try {
    const path = await getBreadcrumbPath(currentFolderId);
    let html =
      '<span class="breadcrumb-item" onclick="navigateToParent()">My Drive</span>';
    path.forEach((item) => {
      html += '<span class="breadcrumb-separator">/</span>';
      html += `<span class="breadcrumb-item" onclick="navigateToFolder('${item.id}')">${escapeHtml(item.name)}</span>`;
    });
    breadcrumb.innerHTML = html;
  } catch (error) {
    breadcrumb.innerHTML =
      '<span class="breadcrumb-item" onclick="navigateToParent()">My Drive</span>';
  }
}

// ============================
// SELECTION
// ============================
function handleItemClick(event, id, isFolder) {
  if (
    event.target.type === "checkbox" ||
    event.target.classList.contains("file-menu")
  )
    return;
  if (!event.ctrlKey && !event.metaKey) {
    if (!selectedItems.has(id)) {
      selectedItems.clear();
      selectedItems.add(id);
      renderFiles(allFiles);
    }
  } else {
    toggleSelectItem(event, id, !selectedItems.has(id));
  }
}
function toggleSelectItem(event, id, checked) {
  event.stopPropagation();
  if (checked) selectedItems.add(id);
  else selectedItems.delete(id);
  renderFiles(allFiles);
}
function toggleSelectAll() {
  const selectAll = document.getElementById("selectAllCheckbox").checked;
  if (selectAll) allFiles.forEach((f) => selectedItems.add(f.id));
  else selectedItems.clear();
  renderFiles(allFiles);
}
function updateSelectionToolbar() {
  const toolbar = document.getElementById("selectionToolbar");
  const count = selectedItems.size;
  const selectAll = document.getElementById("selectAllCheckbox");
  if (count > 0) {
    toolbar.classList.add("show");
    document.getElementById("selectionCount").innerText =
      `${count} item${count > 1 ? "s" : ""} selected`;
    if (selectAll) {
      selectAll.checked = count === allFiles.length;
      selectAll.indeterminate = count > 0 && count < allFiles.length;
    }
  } else {
    toolbar.classList.remove("show");
  }
}

// ============================
// SHARE SELECTED (MULTI)
// ============================
async function shareSelected() {
  const selectedIds = Array.from(selectedItems);
  if (selectedIds.length === 0) {
    safeToast("No items selected.", "warning");
    return;
  }

  window._multiShareFileIds = selectedIds;

  const selectedNames = selectedIds.map((id) => {
    const item = allFiles.find((f) => f.id === id);
    return item ? item.name : id;
  });

  openShareModalForMulti(selectedIds, selectedNames);
}

// ============================
// FOLDER OPERATIONS
// ============================
function showCreateFolderModal() {
  resetFolderModal();
  openModalById("folderModal");
}
function togglePasswordField() {
  const isPrivate = document.getElementById("isPrivate").checked;
  const passwordField = document.getElementById("passwordField");
  const passwordInput = document.getElementById("folderPassword");
  if (isPrivate) {
    passwordField.style.display = "block";
    passwordInput.required = true;
  } else {
    passwordField.style.display = "none";
    passwordInput.required = false;
    passwordInput.value = "";
  }
}
function resetFolderModal() {
  document.getElementById("folderName").value = "";
  document.getElementById("isPrivate").checked = false;
  document.getElementById("passwordField").style.display = "none";
  document.getElementById("folderPassword").value = "";
}
async function createFolder() {
  const name = document.getElementById("folderName").value.trim();
  if (!name) {
    safeToast("Please enter a folder name", "warning");
    return;
  }
  const isPrivate = document.getElementById("isPrivate").checked;
  const password = document.getElementById("folderPassword").value;

  if (isPrivate && (!password || password.length < 4)) {
    safeToast(
      "Please enter a password (minimum 4 characters) for private folder",
      "warning",
    );
    return;
  }

  try {
    await checkDuplicateName(name, currentFolderId);
  } catch (error) {
    safeToast(error.message, "warning");
    return;
  }

  const createBtn = document.querySelector("#folderModal .btn-primary");
  const originalText = createBtn.textContent;
  createBtn.textContent = "Creating...";
  createBtn.disabled = true;

  try {
    const payload = {
      action: "CREATE_FOLDER",
      ids: [],
      destination: currentFolderId || null,
      name: name,
    };
    const response = await apiCall("/resources/action", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || "Failed to create folder");
    }
    safeToast(`Folder "${name}" created successfully.`, "success");
    closeModalById("folderModal");
    resetFolderModal();
    await loadFiles();
  } catch (error) {
    console.error("Error creating folder:", error);
    safeToast("Failed to create folder: " + error.message, "error");
  } finally {
    createBtn.textContent = originalText;
    createBtn.disabled = false;
  }
}

// ============================
// CONTEXT MENU (with adaptive positioning)
// ============================
function showContextMenu(
  event,
  id,
  name,
  type,
  fileType,
  isProtected,
  parentId,
) {
  event.stopPropagation();
  contextMenuItem = { id, name, type, fileType, parentId, isProtected };

  const existingMenu = document.querySelector(".dropdown-menu");
  if (existingMenu) existingMenu.remove();

  const menu = document.createElement("div");
  menu.className = "dropdown-menu show";
  menu.style.position = "fixed";
  menu.style.zIndex = "9999";

  let items = [];

  if (type === "FILE") {
    const isViewable =
      fileType &&
      (fileType.startsWith("image/") ||
        fileType === "application/pdf" ||
        fileType.startsWith("video/"));
    items = [
      ...(isViewable
        ? [{ icon: "👁️", label: "View", action: () => viewFile(id, name) }]
        : []),
      { icon: "⬇️", label: "Download", action: () => downloadFile(id, name) },
      {
        icon: "✏️",
        label: "Rename",
        action: () => showRenameModal(id, name, type),
      },
      { icon: "📋", label: "Copy", action: () => copyItem(id) },
      { icon: "📁", label: "Move", action: () => moveItem(id) },
      { icon: "🔗", label: "Share", action: () => showShareModal(id, name) },
      { icon: "🗑️", label: "Delete", action: () => deleteItem(id) },
    ];
  } else {
    items = [
      {
        icon: "📂",
        label: "Open",
        action: () => openFolder(id, name, isProtected),
      },
      {
        icon: "✏️",
        label: "Rename",
        action: () => showRenameModal(id, name, type),
      },
      { icon: "📁", label: "Move", action: () => moveItem(id) },
      { icon: "🔗", label: "Share", action: () => showShareModal(id, name) },
      { icon: "🗑️", label: "Delete", action: () => deleteItem(id) },
    ];
  }

  items.forEach((item) => {
    const div = document.createElement("div");
    div.className = "dropdown-item";
    div.innerHTML = `${item.icon} ${item.label}`;
    div.onclick = () => {
      item.action();
      menu.remove();
    };
    menu.appendChild(div);
  });

  document.body.appendChild(menu);

  // --- Adaptive positioning (Windows style) ---
  const menuRect = menu.getBoundingClientRect();
  const menuWidth = menuRect.width || 180;
  const menuHeight = menuRect.height || 200;

  let left = event.clientX;
  let top = event.clientY;

  // If menu would overflow right, align to left of cursor
  if (left + menuWidth > window.innerWidth) {
    left = event.clientX - menuWidth;
  }
  if (left < 0) {
    left = 10;
  }

  if (top + menuHeight > window.innerHeight) {
    top = event.clientY - menuHeight;
  }
  if (top < 0) {
    top = 10;
  }

  menu.style.left = left + "px";
  menu.style.top = top + "px";

  // Final clamp
  const finalRect = menu.getBoundingClientRect();
  if (finalRect.right > window.innerWidth) {
    menu.style.left = window.innerWidth - finalRect.width - 10 + "px";
  }
  if (finalRect.bottom > window.innerHeight) {
    menu.style.top = window.innerHeight - finalRect.height - 10 + "px";
  }

  // Auto-close on click outside
  setTimeout(() => {
    document.addEventListener("click", () => menu.remove(), { once: true });
  }, 10);
}

// ============================
// MODAL HELPERS
// ============================
function openModalById(modalId) {
  const modal = document.getElementById(modalId);
  if (!modal) {
    console.error(`❌ Modal with id "${modalId}" not found!`);
    return;
  }
  modal.style.display = "flex";
  modal.classList.add("active");
  console.log(`✅ Modal "${modalId}" opened.`);
}

function closeModalById(modalId) {
  const modal = document.getElementById(modalId);
  if (modal) {
    modal.style.display = "none";
    modal.classList.remove("active");
    console.log(`✅ Modal "${modalId}" closed.`);
  }
}

// ============================
// RENAME
// ============================
function showRenameModal(id, currentName, type) {
  console.log(
    "🔍 showRenameModal called with id:",
    id,
    "name:",
    currentName,
    "type:",
    type,
  );
  contextMenuItem = {
    id,
    name: currentName,
    type: type,
    parentId: contextMenuItem ? contextMenuItem.parentId : currentFolderId,
  };

  let displayName = currentName;
  let extension = "";
  if (type === "FILE" && currentName.includes(".")) {
    const lastDotIndex = currentName.lastIndexOf(".");
    displayName = currentName.substring(0, lastDotIndex);
    extension = currentName.substring(lastDotIndex);
    contextMenuItem.extension = extension;
  } else {
    contextMenuItem.extension = "";
  }

  const input = document.getElementById("newName");
  if (input) {
    input.value = displayName;
  }
  openModalById("renameModal");
}

async function executeRename() {
  let newName = document.getElementById("newName").value.trim();
  if (!newName) {
    safeToast("Please enter a new name", "warning");
    return;
  }

  let fullNewName = newName;
  if (contextMenuItem.type === "FILE" && contextMenuItem.extension) {
    if (!newName.endsWith(contextMenuItem.extension)) {
      fullNewName = newName + contextMenuItem.extension;
    }
  }

  if (fullNewName === contextMenuItem.name) {
    closeModalById("renameModal");
    safeToast("No changes made", "info");
    return;
  }

  const parentId =
    contextMenuItem.parentId !== undefined
      ? contextMenuItem.parentId
      : currentFolderId;

  try {
    await checkDuplicateName(fullNewName, parentId, contextMenuItem.id);
  } catch (error) {
    safeToast(error.message, "warning");
    return;
  }

  try {
    const response = await apiCall("/resources/action", {
      method: "POST",
      body: JSON.stringify({
        ids: [contextMenuItem.id],
        action: "RENAME",
        name: fullNewName,
      }),
    });
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || "Rename failed");
    }
    closeModalById("renameModal");
    await new Promise((resolve) => setTimeout(resolve, 300));
    await loadFiles();
    safeToast("Renamed successfully", "success");
  } catch (error) {
    console.error("❌ Rename error:", error);
    safeToast("Failed to rename: " + error.message, "error");
  }
}

// ============================
// MOVE / COPY
// ============================
async function copyItem(id) {
  pendingAction = "copy";
  pendingItems = [{ id, password: null }];
  await selectDestination();
}
async function moveItem(id) {
  pendingAction = "move";
  pendingItems = [{ id, password: null }];
  await selectDestination();
}
async function copySelected() {
  if (selectedItems.size === 0) {
    safeToast("No items selected", "warning");
    return;
  }
  pendingAction = "copy";
  pendingItems = Array.from(selectedItems).map((id) => ({
    id,
    password: null,
  }));
  await selectDestination();
}
async function moveSelected() {
  if (selectedItems.size === 0) {
    safeToast("No items selected", "warning");
    return;
  }
  pendingAction = "move";
  pendingItems = Array.from(selectedItems).map((id) => ({
    id,
    password: null,
  }));
  await selectDestination();
}

async function selectDestination() {
  console.log("🔍 selectDestination called, pendingAction:", pendingAction);
  const folders = allFiles.filter(
    (f) =>
      (f.driveType === "FOLDER" || f.driveType === "ROOT") &&
      !pendingItems.map((p) => p.id).includes(f.id),
  );
  const select = document.getElementById("destinationFolder");
  if (!select) {
    console.error("❌ destinationFolder select not found");
    return;
  }
  select.innerHTML = '<option value="">Root</option>';
  folders.forEach((f) => {
    select.innerHTML += `<option value="${f.id}">${escapeHtml(f.name)}</option>`;
  });
  const titleEl = document.getElementById("moveModalTitle");
  if (titleEl) {
    titleEl.innerText = pendingAction === "copy" ? "Copy to" : "Move to";
  }
  openModalById("moveModal");
}

async function executeMove() {
  const destId = document.getElementById("destinationFolder").value;
  const destinationId = destId || null;
  let success = 0,
    fail = 0;
  for (const item of pendingItems) {
    try {
      const response = await apiCall("/resources/action", {
        method: "POST",
        body: JSON.stringify({
          ids: [item.id],
          action: pendingAction === "copy" ? "COPY" : "MOVE",
          destination: destinationId,
        }),
      });
      if (!response.ok) throw new Error(`${pendingAction} failed`);
      success++;
    } catch (error) {
      fail++;
    }
  }
  const action = pendingAction;
  closeModalById("moveModal");
  if (action === "move") selectedItems.clear();
  await new Promise((resolve) => setTimeout(resolve, 300));
  await loadFiles();
  pendingItems = [];
  pendingAction = null;
  if (success > 0) {
    safeToast(
      `${action === "copy" ? "Copied" : "Moved"} ${success} item(s)${fail > 0 ? `, ${fail} failed` : ""}`,
      "success",
    );
  } else {
    safeToast(`Failed to ${action} ${fail} item(s)`, "error");
  }
}

// ============================
// DOWNLOAD
// ============================
async function downloadFile(fileId, filename) {
  safeToast("Preparing download...", "info", 1500);
  try {
    const response = await apiCall(`/download/${fileId}`);
    if (!response.ok) throw new Error("Download failed");
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
    safeToast("Download started", "success", 2000);
  } catch (error) {
    safeToast("Download failed: " + error.message, "error");
  }
}

function viewFile(fileId, filename) {
  window.open(`/viewer.html?id=${fileId}&token=${jwtToken}`, "_blank");
}

// ============================
// DELETE
// ============================
async function deleteItem(id) {
  const item = allFiles.find((f) => f.id === id);
  if (!item) {
    safeToast("Item not found", "error");
    return;
  }
  const confirmed = await showConfirm(
    `Are you sure you want to delete "${item.name}"?`,
    "Delete Item",
    "Delete",
    "Cancel",
    "danger",
  );
  if (!confirmed) return;
  try {
    const response = await apiCall("/resources/action", {
      method: "POST",
      body: JSON.stringify({ ids: [id], action: "DELETE" }),
    });
    if (!response.ok) throw new Error("Delete failed");
    selectedItems.delete(id);
    await new Promise((resolve) => setTimeout(resolve, 300));
    await loadFiles();
    safeToast(`"${item.name}" deleted successfully`, "success");
  } catch (error) {
    safeToast("Failed to delete: " + error.message, "error");
  }
}
async function deleteSelected() {
  if (selectedItems.size === 0) return;
  const itemsToDelete = Array.from(selectedItems)
    .map((id) => allFiles.find((f) => f.id === id))
    .filter((item) => item != null);
  const confirmed = await showConfirm(
    `Delete ${itemsToDelete.length} item(s)?`,
    "Delete Items",
    "Delete",
    "Cancel",
    "danger",
  );
  if (!confirmed) return;
  try {
    const response = await apiCall("/resources/action", {
      method: "POST",
      body: JSON.stringify({
        ids: Array.from(selectedItems),
        action: "DELETE",
      }),
    });
    if (!response.ok) throw new Error("Bulk delete failed");
    selectedItems.clear();
    await new Promise((resolve) => setTimeout(resolve, 300));
    await loadFiles();
    safeToast(`Deleted ${itemsToDelete.length} item(s)`, "success");
  } catch (error) {
    safeToast("Failed to delete: " + error.message, "error");
  }
}

// ============================
// EXPOSE GLOBALLY
// ============================
window.loadFiles = loadFiles;
window.renderFiles = renderFiles;
window.openFolder = openFolder;
window.navigateToFolder = navigateToFolder;
window.navigateToParent = navigateToParent;
window.handleItemClick = handleItemClick;
window.toggleSelectItem = toggleSelectItem;
window.toggleSelectAll = toggleSelectAll;
window.showCreateFolderModal = showCreateFolderModal;
window.togglePasswordField = togglePasswordField;
window.resetFolderModal = resetFolderModal;
window.createFolder = createFolder;
window.showContextMenu = showContextMenu;
window.showRenameModal = showRenameModal;
window.executeRename = executeRename;
window.deleteItem = deleteItem;
window.deleteSelected = deleteSelected;
window.downloadFile = downloadFile;
window.viewFile = viewFile;
window.copyItem = copyItem;
window.moveItem = moveItem;
window.copySelected = copySelected;
window.moveSelected = moveSelected;
window.selectDestination = selectDestination;
window.executeMove = executeMove;
window.updateSelectionToolbar = updateSelectionToolbar;
window.openModalById = openModalById;
window.closeModalById = closeModalById;
window.shareSelected = shareSelected;

console.log("✅ app.js loaded (final)");
