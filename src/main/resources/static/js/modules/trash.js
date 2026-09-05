// trash.js – Complete with search filter (preserves all existing functionality)

// ============================
// GLOBALS
// ============================
let allTrashItems = [];
let currentTrashFolderId = null; // null = root of trash
let searchQuery = ''; // NEW: for filtering

// ============================
// INIT
// ============================
document.addEventListener("DOMContentLoaded", async () => {
  const token = localStorage.getItem("jwtToken");
  if (!token) {
    window.location.href = "/login.html";
    return;
  }

  setupTrashUI();
  await loadTrash();
  attachSearchListener(); // NEW
});

function setupTrashUI() {
  const selectAllCheckbox = document.getElementById("selectAllCheckbox");
  if (selectAllCheckbox) {
    selectAllCheckbox.addEventListener("change", toggleSelectAll);
  }

  const emptyTrashBtn = document.getElementById("emptyTrashBtn");
  if (emptyTrashBtn) {
    emptyTrashBtn.addEventListener("click", emptyTrash);
  }

  const restoreSelectedBtn = document.getElementById("restoreSelectedBtn");
  if (restoreSelectedBtn) {
    restoreSelectedBtn.addEventListener("click", restoreSelected);
  }
}

// ============================
// SEARCH FILTER
// ============================
function attachSearchListener() {
  const searchInput = document.getElementById('searchInput');
  if (!searchInput) {
    // Header may not be ready yet – retry
    setTimeout(attachSearchListener, 200);
    return;
  }
  // Remove any existing listener to avoid duplicates
  searchInput.removeEventListener('input', handleSearchInput);
  searchInput.addEventListener('input', handleSearchInput);
}

function handleSearchInput(event) {
  searchQuery = event.target.value.trim().toLowerCase();
  renderTrashItems(); // re‑render with filter
}

// ============================
// LOAD TRASH
// ============================
async function loadTrash() {
  const container = document.getElementById("trashContainer");
  if (!container) {
    console.warn("Container #trashContainer missing – creating one.");
    const body = document.body || document.documentElement;
    container = document.createElement("div");
    container.id = "trashContainer";
    body.appendChild(container);
  }

  showLoading(container, true);

  try {
    const files = await getTrashItems({ skipDedupe: true });
    allTrashItems = files;
    // Reset search when reloading (optional)
    searchQuery = '';
    const searchInput = document.getElementById('searchInput');
    if (searchInput) searchInput.value = '';
    renderTrashItems();
    updateBreadcrumb();
  } catch (error) {
    console.error("Error loading trash:", error);
    showError(container, "Failed to load trash items. Please try again.");
  }
}

// ============================
// RENDER FUNCTIONS
// ============================
function renderTrashItems() {
  const container = document.getElementById("trashContainer");
  if (!container) return;

  // 1. Filter by current folder
  let items = allTrashItems.filter((item) => {
    const parentId = item.parentId || null;
    if (currentTrashFolderId === null) return true;
    return parentId === currentTrashFolderId;
  });

  // 2. Apply search filter (NEW)
  if (searchQuery) {
    items = items.filter(item => 
      item.name.toLowerCase().includes(searchQuery)
    );
  }

  if (!items || items.length === 0) {
    const message = searchQuery ? `No items match "${searchQuery}"` : "This folder is empty";
    showEmptyState(container, message, "Items deleted from your drive will appear here");
    return;
  }

  // Compute hasChildren for each item (checks if any other trashed item has this as parent)
  const hasChildrenMap = new Map();
  allTrashItems.forEach((item) => {
    const children = allTrashItems.filter(
      (other) => other.parentId === item.id,
    );
    hasChildrenMap.set(item.id, children.length > 0);
  });

  const folders = items.filter(
    (f) => f.driveType === "FOLDER" || f.driveType === "ROOT",
  );
  const fileItems = items.filter((f) => f.driveType === "FILE");

  let html = '<div class="file-grid">';

  folders.forEach((item) => {
    const isSelected = selectedItems.has(item.id);
    const icon = "📁";
    const info = hasChildrenMap.get(item.id) ? "Contains items" : "Empty";
    html += createFileItemHTML(item, isSelected, icon, info, true);
  });

  fileItems.forEach((item) => {
    const isSelected = selectedItems.has(item.id);
    const icon = getFileIcon(item.name);
    const info = formatFileSize(item.fileSize);
    html += createFileItemHTML(item, isSelected, icon, info, false);
  });

  html += "</div>";
  container.innerHTML = html;
  updateSelectionToolbar();
}

function createFileItemHTML(item, isSelected, icon, info, isFolder) {
  const safeId = encodeURIComponent(item.id);
  const safeName = encodeURIComponent(item.name).replace(/'/g, "%27");
  const safeType = encodeURIComponent(item.driveType);
  const safeFileType = encodeURIComponent(item.fileType || "");
  const isProtected = item.accessType === "PROTECTED";
  const lockIcon = isProtected ? "🔒 " : "";

  const doubleClickAction = isFolder
    ? `openTrashFolder(decodeURIComponent('${safeId}'))`
    : `restoreItem(decodeURIComponent('${safeId}'))`;

  let deletedInfo = "";
  if (item.deletedAt) {
    const date = new Date(item.deletedAt);
    deletedInfo = `Deleted: ${date.toLocaleDateString()}`;
  }

  return `
        <div class="file-item ${isSelected ? "selected" : ""}" 
             data-id="${item.id}" 
             data-parent-id="${item.parentId || ""}"
             data-type="${item.driveType}" 
             data-name="${escapeHtml(item.name)}"
             ondblclick="${doubleClickAction}"
             onclick="handleItemClick(event, decodeURIComponent('${safeId}'), ${isFolder})">
            <input type="checkbox" class="checkbox" ${isSelected ? "checked" : ""} onchange="toggleSelectItem(event, decodeURIComponent('${safeId}'), this.checked)">
            <div class="file-icon">${icon}</div>
            <div class="file-name">${lockIcon}${escapeHtml(item.name)}</div>
            <div class="file-info">${info}</div>
            ${deletedInfo ? `<div class="file-deleted-info" style="font-size:11px;color:#999;">${deletedInfo}</div>` : ""}
            <div class="file-menu" onclick="showTrashContextMenu(event, decodeURIComponent('${safeId}'), decodeURIComponent('${safeName}'), decodeURIComponent('${safeType}'), decodeURIComponent('${safeFileType}'), ${isProtected})">
                ⋮
            </div>
        </div>
    `;
}

// ============================
// TRASH NAVIGATION (folders openable)
// ============================
function openTrashFolder(folderId) {
  const folder = allTrashItems.find((f) => f.id === folderId);
  if (!folder) {
    safeToast("Folder not found", "error");
    return;
  }
  currentTrashFolderId = folderId;
  // Clear search when navigating into a folder
  searchQuery = '';
  const searchInput = document.getElementById('searchInput');
  if (searchInput) searchInput.value = '';
  renderTrashItems();
  updateBreadcrumb();
}

function navigateToTrashRoot() {
  currentTrashFolderId = null;
  searchQuery = '';
  const searchInput = document.getElementById('searchInput');
  if (searchInput) searchInput.value = '';
  renderTrashItems();
  updateBreadcrumb();
}

function updateBreadcrumb() {
  const breadcrumb = document.getElementById("breadcrumb");
  if (!breadcrumb) return;

  if (currentTrashFolderId === null) {
    breadcrumb.innerHTML =
      '<span class="breadcrumb-item" onclick="navigateToTrashRoot()">🗑️ Trash</span>';
    return;
  }

  // Build breadcrumb path
  const path = [];
  let currentId = currentTrashFolderId;
  const visited = new Set();
  while (currentId && !visited.has(currentId)) {
    visited.add(currentId);
    const item = allTrashItems.find((f) => f.id === currentId);
    if (!item) break;
    path.unshift({ id: item.id, name: item.name });
    currentId = item.parentId || null;
  }

  let html =
    '<span class="breadcrumb-item" onclick="navigateToTrashRoot()">🗑️ Trash</span>';
  for (const p of path) {
    html += '<span class="breadcrumb-separator">/</span>';
    html += `<span class="breadcrumb-item" onclick="openTrashFolder('${p.id}')">${escapeHtml(p.name)}</span>`;
  }
  breadcrumb.innerHTML = html;
}

// ============================
// SELECTION (uses global selectedItems)
// ============================
function handleItemClick(event, id, isFolder) {
  if (
    event.target.type === "checkbox" ||
    event.target.classList.contains("file-menu")
  ) {
    return;
  }
  if (!event.ctrlKey && !event.metaKey) {
    if (!selectedItems.has(id)) {
      selectedItems.clear();
      selectedItems.add(id);
      renderTrashItems();
    }
  } else {
    toggleSelectItem(event, id, !selectedItems.has(id));
  }
}

function toggleSelectItem(event, id, checked) {
  event.stopPropagation();
  if (checked) selectedItems.add(id);
  else selectedItems.delete(id);
  renderTrashItems();
}

function toggleSelectAll() {
  const selectAll = document.getElementById("selectAllCheckbox");
  if (!selectAll) return;
  const checked = selectAll.checked;
  // Get current visible items (based on current folder + search)
  let items = allTrashItems.filter((item) => {
    const parentId = item.parentId || null;
    if (currentTrashFolderId === null) return true;
    return parentId === currentTrashFolderId;
  });
  if (searchQuery) {
    items = items.filter(item => item.name.toLowerCase().includes(searchQuery));
  }
  if (checked) {
    items.forEach((f) => selectedItems.add(f.id));
  } else {
    items.forEach((f) => selectedItems.delete(f.id));
  }
  renderTrashItems();
}

function updateSelectionToolbar() {
  const toolbar = document.getElementById("selectionToolbar");
  if (!toolbar) return;
  const count = selectedItems.size;
  const selectAllCheckbox = document.getElementById("selectAllCheckbox");
  if (count > 0) {
    toolbar.classList.add("show");
    const countEl = document.getElementById("selectionCount");
    if (countEl)
      countEl.innerText = `${count} item${count > 1 ? "s" : ""} selected`;
    if (selectAllCheckbox) {
      // Get current visible items
      let items = allTrashItems.filter((item) => {
        const parentId = item.parentId || null;
        if (currentTrashFolderId === null) return true;
        return parentId === currentTrashFolderId;
      });
      if (searchQuery) {
        items = items.filter(item => item.name.toLowerCase().includes(searchQuery));
      }
      selectAllCheckbox.checked = count === items.length;
      selectAllCheckbox.indeterminate =
        count > 0 && count < items.length;
    }
  } else {
    toolbar.classList.remove("show");
  }
}

// ============================
// TRASH OPERATIONS
// ============================
async function restoreItem(id) {
  const item = allTrashItems.find((f) => f.id === id);
  if (!item) {
    safeToast("Item not found", "error");
    return;
  }

  // Check if parent is in trash
  const parentInTrash = item.parentId
    ? allTrashItems.find((f) => f.id === item.parentId)
    : null;
  if (parentInTrash) {
    const confirmed = await showConfirm(
      `This item is inside a deleted folder "${parentInTrash.name}". To restore it, the parent folder must be restored first. Would you like to restore the parent folder as well?`,
      "Restore with Parent",
      "Restore Both",
      "Cancel",
      "primary",
    );
    if (!confirmed) return;
    try {
      await restoreFromTrash(parentInTrash.id);
      await loadTrash();
      safeToast(
        `Folder "${parentInTrash.name}" and its contents restored successfully`,
        "success",
      );
    } catch (error) {
      safeToast(error.message || "Restore failed", "error");
    }
    return;
  }

  try {
    await restoreFromTrash(id);
    selectedItems.delete(id);
    await loadTrash();
    safeToast("Item restored successfully", "success");
  } catch (error) {
    safeToast(error.message || "Restore failed", "error");
  }
}

async function deletePermanently(id) {
  const confirmed = await showConfirm(
    "Permanently delete this item? This cannot be undone.",
    "Delete Permanently",
    "Delete",
    "Cancel",
    "danger",
  );
  if (!confirmed) return;
  try {
    await permanentDeleteItem(id);
    selectedItems.delete(id);
    await loadTrash();
    safeToast("Item permanently deleted", "success");
  } catch (error) {
    safeToast(error.message || "Delete failed", "error");
  }
}

async function restoreSelected() {
  if (selectedItems.size === 0) {
    safeToast("No items selected", "warning");
    return;
  }

  const selectedIds = Array.from(selectedItems);
  const selectedItemsObj = selectedIds
    .map((id) => allTrashItems.find((f) => f.id === id))
    .filter(Boolean);

  // Check if any selected item has a parent in trash
  const itemsWithParentInTrash = selectedItemsObj.filter(
    (item) =>
      item.parentId && allTrashItems.find((f) => f.id === item.parentId),
  );

  if (itemsWithParentInTrash.length > 0) {
    const parentNames = [
      ...new Set(
        itemsWithParentInTrash.map((item) => {
          const parent = allTrashItems.find((f) => f.id === item.parentId);
          return parent ? parent.name : "unknown";
        }),
      ),
    ];
    const message = `Some items are inside deleted folders (${parentNames.join(", ")}). To restore them, the parent folders must be restored first. Would you like to restore all parent folders as well?`;
    const confirmed = await showConfirm(
      message,
      "Restore with Parents",
      "Restore All",
      "Cancel",
      "primary",
    );
    if (!confirmed) return;

    const parentIds = [
      ...new Set(itemsWithParentInTrash.map((item) => item.parentId)),
    ];
    try {
      for (const parentId of parentIds) {
        await restoreFromTrash(parentId);
      }
      await loadTrash();
      safeToast(
        `Restored ${parentIds.length} folder(s) and their contents`,
        "success",
      );
      selectedItems.clear();
    } catch (error) {
      safeToast(error.message || "Restore failed", "error");
    }
    return;
  }

  const confirmed = await showConfirm(
    `Restore ${selectedItems.size} item(s) from trash?`,
    "Restore Items",
    "Restore",
    "Cancel",
  );
  if (!confirmed) return;

  let successCount = 0,
    failCount = 0;
  for (const id of selectedIds) {
    try {
      await restoreFromTrash(id);
      successCount++;
    } catch (error) {
      failCount++;
    }
  }
  selectedItems.clear();
  await loadTrash();
  safeToast(
    `Restored ${successCount} item(s)${failCount > 0 ? `, ${failCount} failed` : ""}`,
    "success",
  );
}

async function emptyTrash() {
  if (allTrashItems.length === 0) {
    safeToast("Trash is already empty", "info");
    return;
  }
  const confirmed = await showConfirm(
    "Permanently delete ALL items in trash? This cannot be undone.",
    "Empty Trash",
    "Empty",
    "Cancel",
    "danger",
  );
  if (!confirmed) return;
  try {
    await emptyTrashApi();
    selectedItems.clear();
    await loadTrash();
    safeToast("Trash emptied successfully", "success");
  } catch (error) {
    safeToast(error.message || "Empty trash failed", "error");
  }
}

// ============================
// CONTEXT MENU
// ============================
function showTrashContextMenu(event, id, name, type, fileType, isProtected) {
  event.stopPropagation();
  const existingMenu = document.querySelector(".dropdown-menu");
  if (existingMenu) existingMenu.remove();

  const menu = document.createElement("div");
  menu.className = "dropdown-menu show";
  menu.style.position = "absolute";
  menu.style.top = `${event.clientY}px`;
  menu.style.left = `${event.clientX}px`;

  const items = [
    { icon: "↩️", label: "Restore", action: () => restoreItem(id) },
    {
      icon: "🗑️",
      label: "Delete Permanently",
      action: () => deletePermanently(id),
    },
  ];
  if (type === "FILE") {
    items.push({
      icon: "⬇️",
      label: "Download",
      action: () => downloadFile(id, name),
    });
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
  setTimeout(
    () =>
      document.addEventListener("click", () => menu.remove(), { once: true }),
    0,
  );
}

async function downloadFile(fileId, filename) {
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
  } catch (error) {
    safeToast("Download failed: " + error.message, "error");
  }
}

// ============================
// UTILITY HELPERS
// ============================
function showLoading(container, show) {
  if (show) container.innerHTML = '<div class="loading">Loading trash...</div>';
}
function showEmptyState(
  container,
  message = "This folder is empty",
  subtitle = "",
) {
  const subtitleHtml = subtitle
    ? `<div style="font-size:12px;margin-top:8px;">${subtitle}</div>`
    : "";
  container.innerHTML = `
        <div class="empty-state">
            <div class="empty-state-icon">🗑️</div>
            <div>${message}</div>
            ${subtitleHtml}
        </div>
    `;
}
function showError(container, message) {
  container.innerHTML = `
        <div class="empty-state">
            <div class="empty-state-icon">❌</div>
            <div>${message}</div>
        </div>
    `;
}
function safeToast(message, type = "info", duration = 3000) {
  if (typeof showToast === "function") showToast(message, type, duration);
  else alert(message);
}

// ============================
// EXPOSE GLOBALLY
// ============================
window.restoreItem = restoreItem;
window.deletePermanently = deletePermanently;
window.restoreSelected = restoreSelected;
window.emptyTrash = emptyTrash;
window.showTrashContextMenu = showTrashContextMenu;
window.handleItemClick = handleItemClick;
window.toggleSelectItem = toggleSelectItem;
window.toggleSelectAll = toggleSelectAll;
window.loadTrash = loadTrash;
window.openTrashFolder = openTrashFolder;
window.navigateToTrashRoot = navigateToTrashRoot;

console.log("✅ trash.js loaded with search filter");