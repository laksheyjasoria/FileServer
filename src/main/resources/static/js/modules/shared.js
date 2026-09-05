// shared.js – Shared with me (with search)

let currentShares = [];

document.addEventListener("DOMContentLoaded", () => {
  loadSharedItems();
  setupSidebarNavigation();
  setupSearch(); // 👈 Add search
});

async function loadSharedItems() {
  const container = document.getElementById("fileContainer");
  if (!container) {
    safeToast("Container not found", "error");
    return;
  }
  showLoading(container, true);
  document.getElementById("breadcrumb").innerHTML =
    '<span class="breadcrumb-item">Shared with me</span>';

  try {
    const response = await apiCall("/share/shared-with-me");
    if (!response.ok) throw new Error("API Error");
    const shares = await response.json();
    currentShares = shares; // store for search
    if (shares && shares.length > 0) {
      renderSharedFiles(shares);
      safeToast(`Loaded ${shares.length} shared item(s).`, "info", 2000);
    } else {
      showEmptyState(container, "No items shared with you");
      safeToast("No items shared with you.", "info", 2000);
    }
  } catch (error) {
    console.error(error);
    showError(container);
    safeToast("Failed to load shared items: " + error.message, "error");
  }
}

function renderSharedFiles(shares) {
  const container = document.getElementById("fileContainer");
  let html = '<div class="file-grid">';
  shares.forEach((item) => {
    const isFolder = item.driveType === "FOLDER" || item.driveType === "MULTI";
    const icon = isFolder ? "📁" : getFileIcon(item.driveName);
    let infoText = `Shared by ${escapeHtml(item.createdBy)}`;
    if (!isFolder && item.fileSize > 0) {
      infoText = `${formatFileSize(item.fileSize)} • ${infoText}`;
    } else if (isFolder) {
      infoText = `Folder • ${infoText}`;
    }
    html += `
            <div class="file-item" onclick="handleSharedItemClick(event, '${escapeJSString(item.token)}')" ondblclick="openSharedItem('${escapeJSString(item.token)}')">
                <div class="file-icon">${icon}</div>
                <div class="file-name">${escapeHtml(item.driveName)}</div>
                <div class="file-info">${infoText}</div>
                <div class="file-menu" onclick="showSharedContextMenu(event, '${escapeJSString(item.token)}', '${escapeJSString(item.driveName)}', '${item.driveType}')">
                    ⋮
                </div>
            </div>
        `;
  });
  html += "</div>";
  container.innerHTML = html;
}

function handleSharedItemClick(event, token) {
  if (event.target.classList.contains("file-menu")) return;
}

function openSharedItem(token) {
  window.open(`/share2.html?token=${encodeURIComponent(token)}`, "_blank");
}

function setupSearch() {
  const searchInput = document.getElementById("searchInput");
  if (searchInput) {
    searchInput.addEventListener("input", (e) => {
      const term = e.target.value.toLowerCase().trim();
      if (term === "") {
        renderSharedFiles(currentShares);
      } else {
        const filtered = currentShares.filter(
          (share) =>
            share.driveName.toLowerCase().includes(term) ||
            (share.createdBy && share.createdBy.toLowerCase().includes(term)),
        );
        renderSharedFiles(filtered);
      }
    });
  }
}

// Context menu (kept from earlier)
function showSharedContextMenu(event, token, name, type) {
  event.stopPropagation();
  const existingMenu = document.querySelector(".dropdown-menu");
  if (existingMenu) existingMenu.remove();
  const menu = document.createElement("div");
  menu.className = "dropdown-menu show";
  menu.style.position = "absolute";
  menu.style.top = `${event.clientY}px`;
  menu.style.left = `${event.clientX}px`;
  let items = [];
  if (type === "FILE") {
    items = [
      { icon: "👁️", label: "View", action: () => openSharedItem(token) },
      {
        icon: "⬇️",
        label: "Download",
        action: () => downloadSharedFile(token),
      },
    ];
  } else {
    items = [
      { icon: "📂", label: "Open", action: () => openSharedItem(token) },
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
  setTimeout(() => {
    document.addEventListener("click", () => menu.remove(), { once: true });
  }, 0);
}

function downloadSharedFile(token) {
  safeToast("Starting download...", "info", 1500);
  window.location.href = `/share/download/${token}`;
}
