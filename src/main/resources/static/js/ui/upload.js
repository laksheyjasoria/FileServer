// upload.js – Upload queue with progress modal

// ============================
// UI: Show/Hide Upload Modal
// ============================
function showUploadModal() {
  const modal = document.getElementById("uploadModal");
  if (modal) {
    modal.classList.add("active");
    modal.style.display = "flex";
  }
}

function hideUploadModal() {
  const modal = document.getElementById("uploadModal");
  if (modal) {
    modal.classList.remove("active");
    modal.style.display = "none";
  }
}

// ============================
// ADD FILES TO QUEUE
// ============================
function addToUploadQueue(files) {
  if (!files || files.length === 0) return;

  showUploadModal();

  const listContainer = document.getElementById("uploadList");
  if (!listContainer) {
    console.error("uploadList container not found");
    return;
  }

  for (const file of files) {
    const uploadId = Date.now() + "-" + Math.random().toString(36).substr(2, 6);
    const uploadItem = {
      id: uploadId,
      file: file,
      progress: 0,
      status: "pending",
      xhr: null,
    };

    uploadQueue.push(uploadItem);
    activeUploads.set(uploadId, uploadItem);

    const itemDiv = document.createElement("div");
    itemDiv.className = "upload-item";
    itemDiv.id = `upload-${uploadId}`;
    itemDiv.innerHTML = `
            <div class="upload-filename">${escapeHtml(file.name)}</div>
            <div class="progress-bar">
                <div class="progress-fill" style="width: 0%"></div>
            </div>
            <div class="upload-status">
                <span class="status-text">Pending...</span>
                <button class="cancel-upload" onclick="cancelUpload('${uploadId}')">Cancel</button>
            </div>
        `;
    listContainer.appendChild(itemDiv);

    setTimeout(() => startUpload(uploadId), 100);
  }
}

// ============================
// START UPLOAD
// ============================
async function startUpload(uploadId) {
  const uploadItem = activeUploads.get(uploadId);
  if (!uploadItem || uploadItem.status === "cancelled") return;

  const folderId = currentFolderId;

  // Optional duplicate check
  try {
    const files = await getFilesForFolder(folderId);
    const exists = files.some(
      (item) => item.name.toLowerCase() === uploadItem.file.name.toLowerCase(),
    );
    if (exists) {
      safeToast(
        `File "${uploadItem.file.name}" already exists. Upload cancelled.`,
        "warning",
      );
      cancelUpload(uploadId);
      return;
    }
  } catch (e) {
    // ignore
  }

  uploadItem.status = "uploading";
  updateUploadStatus(uploadId, "Uploading...", false);

  const formData = new FormData();
  formData.append("file", uploadItem.file);
  if (folderId) {
    formData.append("parentId", folderId);
  }

  const xhr = new XMLHttpRequest();
  uploadItem.xhr = xhr;

  xhr.upload.addEventListener("progress", (e) => {
    if (e.lengthComputable) {
      const percent = Math.round((e.loaded / e.total) * 100);
      uploadItem.progress = percent;
      const itemDiv = document.getElementById(`upload-${uploadId}`);
      if (itemDiv) {
        const fill = itemDiv.querySelector(".progress-fill");
        if (fill) fill.style.width = `${percent}%`;
      }
      updateUploadStatus(uploadId, `Uploading ${percent}%`, false);
    }
  });

  xhr.onload = () => {
    if (xhr.status >= 200 && xhr.status < 300) {
      uploadItem.status = "completed";
      updateUploadStatus(uploadId, "Completed ✅", true);
      const cancelBtn = document.querySelector(
        `#upload-${uploadId} .cancel-upload`,
      );
      if (cancelBtn) cancelBtn.remove();
      safeToast(`Uploaded: ${uploadItem.file.name}`, "success");
    } else {
      uploadItem.status = "failed";
      let message = "Upload failed";
      try {
        const err = JSON.parse(xhr.responseText);
        message = err.message || message;
      } catch (_) {
        message = xhr.responseText || message;
      }
      updateUploadStatus(uploadId, `${message} (HTTP ${xhr.status})`, true);
      safeToast(`Upload failed: ${uploadItem.file.name}`, "error");
    }
    checkAllUploadsComplete();
  };

  xhr.onerror = () => {
    uploadItem.status = "failed";
    updateUploadStatus(uploadId, "Network error ❌", true);
    safeToast(`Network error uploading: ${uploadItem.file.name}`, "error");
    checkAllUploadsComplete();
  };

  xhr.onabort = () => {
    // cancelled – do nothing
  };

  const token = localStorage.getItem("jwtToken");
  xhr.open("POST", `${API_URL}/upload`);
  xhr.setRequestHeader("Authorization", `Bearer ${token}`);
  xhr.send(formData);
}

function updateUploadStatus(uploadId, text, isFinal) {
  const itemDiv = document.getElementById(`upload-${uploadId}`);
  if (!itemDiv) return;
  const statusSpan = itemDiv.querySelector(".status-text");
  if (statusSpan) {
    statusSpan.textContent = text;
    if (isFinal) {
      if (text.includes("Completed")) statusSpan.className = "completed-status";
      else if (text.includes("Failed") || text.includes("Error"))
        statusSpan.className = "failed-status";
      else if (text.includes("Cancelled"))
        statusSpan.className = "cancelled-status";
    }
  }
}

function cancelUpload(uploadId) {
  const uploadItem = activeUploads.get(uploadId);
  if (uploadItem && uploadItem.xhr) {
    uploadItem.xhr.abort();
    uploadItem.status = "cancelled";
    updateUploadStatus(uploadId, "Cancelled ✖️", true);
    const cancelBtn = document.querySelector(
      `#upload-${uploadId} .cancel-upload`,
    );
    if (cancelBtn) cancelBtn.remove();
    safeToast(`Upload cancelled: ${uploadItem.file.name}`, "info");
  }

  setTimeout(() => {
    const itemDiv = document.getElementById(`upload-${uploadId}`);
    if (itemDiv) {
      itemDiv.style.opacity = "0.5";
      setTimeout(() => {
        if (itemDiv.parentNode) itemDiv.remove();
      }, 2000);
    }
  }, 1000);

  const index = uploadQueue.findIndex((u) => u.id === uploadId);
  if (index !== -1) uploadQueue.splice(index, 1);
  activeUploads.delete(uploadId);

  checkAllUploadsComplete();
}

function checkAllUploadsComplete() {
  const remaining = Array.from(activeUploads.values()).filter(
    (item) => item.status === "pending" || item.status === "uploading",
  );
  if (remaining.length === 0) {
    setTimeout(() => {
      if (typeof loadFiles === "function") loadFiles();
      setTimeout(() => {
        hideUploadModal();
        const list = document.getElementById("uploadList");
        if (list) list.innerHTML = "";
        uploadQueue = [];
        activeUploads.clear();
      }, 1000);
    }, 1000);
  }
}

function closeUploadModal() {
  const active = Array.from(activeUploads.values()).filter(
    (item) => item.status === "pending" || item.status === "uploading",
  );
  if (active.length > 0) {
    if (!confirm("Uploads in progress. Close anyway?")) return;
    active.forEach((item) => cancelUpload(item.id));
  }
  hideUploadModal();
  const list = document.getElementById("uploadList");
  if (list) list.innerHTML = "";
  uploadQueue = [];
  activeUploads.clear();
}

// Expose globally
window.addToUploadQueue = addToUploadQueue;
window.cancelUpload = cancelUpload;
window.closeUploadModal = closeUploadModal;
window.showUploadModal = showUploadModal;
window.hideUploadModal = hideUploadModal;

console.log("✅ upload.js loaded");
