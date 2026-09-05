// sharedByMe.js – Shared by me (with search)

let currentShares = [];

document.addEventListener("DOMContentLoaded", () => {
  loadSharedByMe();
  setupSidebarNavigation();
  setupSearch(); // 👈 Add search
});

async function loadSharedByMe() {
  const container = document.getElementById("fileContainer");
  if (!container) {
    safeToast("Container not found", "error");
    return;
  }
  showLoading(container, true);
  document.getElementById("breadcrumb").innerHTML =
    '<span class="breadcrumb-item">Shared by me</span>';

  try {
    const response = await apiCall("/share/shared-by-me");
    if (!response.ok) throw new Error("API Error");
    const shares = await response.json();
    currentShares = shares; // store for search
    if (shares && shares.length > 0) {
      renderSharedByMe(shares);
      safeToast(`Loaded ${shares.length} shared item(s).`, "info", 2000);
    } else {
      showEmptyState(container, "You have not created any shares yet");
      safeToast("No shares created yet.", "info", 2000);
    }
  } catch (error) {
    showError(container);
    safeToast("Failed to load: " + error.message, "error");
  }
}

function renderSharedByMe(shares) {
  const container = document.getElementById("fileContainer");
  let html = '<div class="file-grid">';
  shares.forEach((item) => {
    const isFolder = item.driveType === "FOLDER" || item.driveType === "MULTI";
    const icon = isFolder ? "📁" : getFileIcon(item.driveName);
    const expiry = item.expiresAt
      ? new Date(item.expiresAt).toLocaleDateString()
      : "Never";
    html += `
            <div class="file-item">
                <div class="file-icon">${icon}</div>
                <div class="file-name">${escapeHtml(item.driveName)}</div>
                <div class="file-info">${item.shareType} • Expires: ${expiry}</div>
                <div class="file-menu" onclick="showSharedByMeContextMenu(event, '${escapeJSString(item.token)}', '${escapeJSString(item.driveName)}')">
                    ⋮
                </div>
            </div>
        `;
  });
  html += "</div>";
  container.innerHTML = html;
}

function setupSearch() {
  const searchInput = document.getElementById("searchInput");
  if (searchInput) {
    searchInput.addEventListener("input", (e) => {
      const term = e.target.value.toLowerCase().trim();
      if (term === "") {
        renderSharedByMe(currentShares);
      } else {
        const filtered = currentShares.filter((share) =>
          share.driveName.toLowerCase().includes(term),
        );
        renderSharedByMe(filtered);
      }
    });
  }
}

function showSharedByMeContextMenu(event, token, name) {
  event.stopPropagation();
  const existingMenu = document.querySelector(".dropdown-menu");
  if (existingMenu) existingMenu.remove();
  const menu = document.createElement("div");
  menu.className = "dropdown-menu show";
  menu.style.position = "absolute";
  menu.style.top = `${event.clientY}px`;
  menu.style.left = `${event.clientX}px`;
  const items = [
    { icon: "🔗", label: "Copy Link", action: () => copyShareLink(token) },
    { icon: "🗑️", label: "Delete Share", action: () => deleteShare(token) },
  ];
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

async function copyShareLink(token) {
  try {
    await navigator.clipboard.writeText(
      `${window.location.origin}/share2.html?token=${token}`,
    );
    safeToast("Link copied!", "success");
  } catch {
    safeToast("Failed to copy", "error");
  }
}

async function deleteShare(token) {
  const confirmed = await showConfirm(
    "Delete this share?",
    "Delete Share",
    "Delete",
    "Cancel",
    "danger",
  );
  if (!confirmed) return;
  try {
    const response = await apiCall(`/share/${token}`, { method: "DELETE" });
    if (!response.ok) throw new Error("Delete failed");
    safeToast("Share deleted.", "success");
    loadSharedByMe();
  } catch (error) {
    safeToast("Failed to delete: " + error.message, "error");
  }
}
