// upload.js – production resumable chunk upload queue

const UPLOAD_MAX_PARALLEL_CHUNKS = 4;
const UPLOAD_MAX_RETRIES = 3;
const UPLOAD_RETRY_BASE_MS = 600;

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

function formatUploadBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const exponent = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / Math.pow(1024, exponent)).toFixed(exponent === 0 ? 0 : 1)} ${units[exponent]}`;
}

function formatUploadSpeed(bytesPerSecond) {
  return bytesPerSecond > 0 ? `${formatUploadBytes(bytesPerSecond)}/s` : "—";
}

function formatUploadEta(seconds) {
  if (!Number.isFinite(seconds) || seconds <= 0) return "calculating…";
  const value = Math.ceil(seconds);
  if (value < 60) return `${value}s remaining`;
  const minutes = Math.floor(value / 60);
  const secondsPart = value % 60;
  return secondsPart ? `${minutes}m ${secondsPart}s remaining` : `${minutes}m remaining`;
}

function addToUploadQueue(files) {
  if (!files || files.length === 0) return;

  showUploadModal();
  const listContainer = document.getElementById("uploadList");
  if (!listContainer) {
    console.error("uploadList container not found");
    return;
  }

  for (const file of files) {
    const uploadId = Date.now() + "-" + Math.random().toString(36).slice(2, 8);
    const uploadItem = {
      id: uploadId,
      file,
      progress: 0,
      uploadedBytes: 0,
      status: "pending",
      uploadJobId: null,
      chunkSize: 0,
      totalChunks: 0,
      completedChunks: new Set(),
      activeRequests: new Map(),
      retryCounts: new Map(),
      paused: false,
      cancelled: false,
      startedAt: null,
      lastProgressAt: null,
      lastProgressBytes: 0,
      speed: 0,
    };

    uploadQueue.push(uploadItem);
    activeUploads.set(uploadId, uploadItem);

    const itemDiv = document.createElement("div");
    itemDiv.className = "upload-item";
    itemDiv.id = `upload-${uploadId}`;
    itemDiv.innerHTML = `
      <div class="upload-filename">${escapeHtml(file.name)}</div>
      <div class="progress-bar"><div class="progress-fill" style="width:0%"></div></div>
      <div class="upload-progress-meta">
        <span class="upload-progress-bytes">0 B / ${formatUploadBytes(file.size)}</span>
        <span class="upload-progress-percent">0%</span>
      </div>
      <div class="upload-progress-details">
        <span class="upload-speed">Speed: —</span>
        <span class="upload-eta">ETA: calculating…</span>
        <span class="upload-chunks">Chunks: —</span>
      </div>
      <div class="upload-status">
        <span class="status-text">Pending...</span>
        <span class="upload-actions">
          <button class="pause-upload" onclick="pauseUpload('${uploadId}')">Pause</button>
          <button class="resume-upload" onclick="resumeUpload('${uploadId}')" style="display:none">Resume</button>
          <button class="cancel-upload" onclick="cancelUpload('${uploadId}')">Cancel</button>
        </span>
      </div>
    `;
    listContainer.appendChild(itemDiv);

    setTimeout(() => startUpload(uploadId), 50);
  }
}

async function startUpload(localUploadId) {
  const uploadItem = activeUploads.get(localUploadId);
  if (!uploadItem || uploadItem.cancelled || uploadItem.status === "completed") return;

  const folderId = currentFolderId || null;

  try {
    const files = await getFilesForFolder(folderId);
    const exists = files.some(
      (item) => item.name.toLowerCase() === uploadItem.file.name.toLowerCase(),
    );
    if (exists) {
      throw new Error(`File "${uploadItem.file.name}" already exists.`);
    }
  } catch (error) {
    if (error.message && error.message.includes("already exists")) {
      uploadItem.status = "failed";
      updateUploadStatus(localUploadId, error.message, true);
      safeToast(error.message, "warning");
      checkAllUploadsComplete();
      return;
    }
  }

  try {
    uploadItem.status = "creating";
    updateUploadStatus(localUploadId, "Preparing resumable upload…", false);

    const createResponse = await apiCall("/chunk-upload/create", {
      method: "POST",
      body: JSON.stringify({
        fileName: uploadItem.file.name,
        totalSize: uploadItem.file.size,
        // The server is authoritative for chunk size/count.
        totalChunks: null,
        parentId: folderId,
        contentType: uploadItem.file.type || "application/octet-stream",
      }),
      skipDedupe: true,
    });

    if (!createResponse || !createResponse.ok) {
      throw await responseToUploadError(createResponse, "Unable to create upload.");
    }

    const job = await createResponse.json();
    uploadItem.uploadJobId = job.id || job.uploadId;
    uploadItem.chunkSize = Number(job.chunkSize);
    uploadItem.totalChunks = Number(job.totalChunks);

    if (!uploadItem.uploadJobId || !Number.isFinite(uploadItem.chunkSize) || uploadItem.chunkSize <= 0 ||
        !Number.isInteger(uploadItem.totalChunks) || uploadItem.totalChunks < 1) {
      throw new Error("Server returned an invalid upload plan.");
    }

    if (uploadItem.file.size === 0) {
      uploadItem.status = "completed";
      uploadItem.progress = 100;
      updateUploadProgress(localUploadId);
      updateUploadStatus(localUploadId, "Completed ✅", true);
      checkAllUploadsComplete();
      return;
    }

    updateUploadProgress(localUploadId);
    await loadCompletedChunks(uploadItem);

    if (uploadItem.completedChunks.size >= uploadItem.totalChunks) {
      uploadItem.status = "completed";
      uploadItem.uploadedBytes = uploadItem.file.size;
      updateUploadProgress(localUploadId);
      updateUploadStatus(localUploadId, "Completed ✅", true);
      checkAllUploadsComplete();
      return;
    }

    uploadItem.status = "uploading";
    uploadItem.startedAt = performance.now();
    uploadItem.lastProgressAt = uploadItem.startedAt;
    uploadItem.lastProgressBytes = uploadItem.uploadedBytes;
    updateUploadStatus(localUploadId, "Uploading…", false);

    await uploadRemainingChunks(uploadItem);

    if (!uploadItem.cancelled && !uploadItem.paused && uploadItem.completedChunks.size === uploadItem.totalChunks) {
      uploadItem.status = "completed";
      uploadItem.uploadedBytes = uploadItem.file.size;
      updateUploadProgress(localUploadId);
      updateUploadStatus(localUploadId, "Completed ✅", true);
      removeUploadActionButtons(localUploadId);
      safeToast(`Uploaded: ${uploadItem.file.name}`, "success");
    }
  } catch (error) {
    if (uploadItem.cancelled) return;
    if (uploadItem.paused) return;
    uploadItem.status = "failed";
    updateUploadStatus(localUploadId, error.message || "Upload failed", true);
    safeToast(`Upload failed: ${uploadItem.file.name}`, "error");
  }

  checkAllUploadsComplete();
}

async function loadCompletedChunks(uploadItem) {
  const response = await apiCall(`/chunk-upload/${encodeURIComponent(uploadItem.uploadJobId)}/resume`, {
    method: "GET",
    skipDedupe: true,
  });
  if (!response || !response.ok) {
    throw await responseToUploadError(response, "Unable to resume upload state.");
  }

  const indexes = await response.json();
  uploadItem.completedChunks = new Set(
    Array.isArray(indexes) ? indexes.map(Number).filter(Number.isInteger) : [],
  );
  uploadItem.uploadedBytes = 0;
  for (const index of uploadItem.completedChunks) {
    const start = index * uploadItem.chunkSize;
    const end = Math.min(uploadItem.file.size, start + uploadItem.chunkSize);
    uploadItem.uploadedBytes += Math.max(0, end - start);
  }
}

async function uploadRemainingChunks(uploadItem) {
  while (!uploadItem.cancelled && !uploadItem.paused && uploadItem.completedChunks.size < uploadItem.totalChunks) {
    const pending = [];
    const availableSlots = Math.max(1, UPLOAD_MAX_PARALLEL_CHUNKS - uploadItem.activeRequests.size);

    for (let index = 0; index < uploadItem.totalChunks && pending.length < availableSlots; index++) {
      if (!uploadItem.completedChunks.has(index) && !uploadItem.activeRequests.has(index)) {
        pending.push(uploadOneChunk(uploadItem, index));
      }
    }

    if (pending.length === 0) {
      if (uploadItem.activeRequests.size > 0) {
        await Promise.race(Array.from(uploadItem.activeRequests.values()).map((entry) => entry.promise));
        continue;
      }
      throw new Error("No upload progress was possible.");
    }

    await Promise.all(pending);
  }
}

function uploadOneChunk(uploadItem, chunkIndex) {
  const start = chunkIndex * uploadItem.chunkSize;
  const end = Math.min(uploadItem.file.size, start + uploadItem.chunkSize);
  const chunkBlob = uploadItem.file.slice(start, end, uploadItem.file.type || "application/octet-stream");

  const requestEntry = { xhr: null, promise: null };
  const promise = new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    requestEntry.xhr = xhr;
    uploadItem.activeRequests.set(chunkIndex, requestEntry);

    xhr.upload.addEventListener("progress", (event) => {
      if (!event.lengthComputable) return;
      const inFlightBytes = Array.from(uploadItem.activeRequests.entries()).reduce((sum, [index, entry]) => {
        if (index === chunkIndex) return sum + Math.min(event.loaded, end - start);
        return sum + (entry.uploadedBytes || 0);
      }, 0);
      updateUploadProgress(uploadItem.id, inFlightBytes);
    });

    xhr.onload = async () => {
      uploadItem.activeRequests.delete(chunkIndex);
      if (xhr.status >= 200 && xhr.status < 300) {
        uploadItem.completedChunks.add(chunkIndex);
        uploadItem.uploadedBytes = calculateCompletedBytes(uploadItem);
        uploadItem.retryCounts.delete(chunkIndex);
        updateUploadProgress(uploadItem.id);
        resolve();
        return;
      }

      const message = await xhrErrorMessage(xhr, `Chunk ${chunkIndex + 1} failed`);
      retryChunkOrFail(uploadItem, chunkIndex, message, reject);
    };

    xhr.onerror = () => {
      uploadItem.activeRequests.delete(chunkIndex);
      retryChunkOrFail(uploadItem, chunkIndex, "Network error", reject);
    };

    xhr.onabort = () => {
      uploadItem.activeRequests.delete(chunkIndex);
      if (uploadItem.paused || uploadItem.cancelled) {
        resolve();
      } else {
        retryChunkOrFail(uploadItem, chunkIndex, "Upload request aborted", reject);
      }
    };

    const token = localStorage.getItem("jwtToken");
    const formData = new FormData();
    formData.append("file", chunkBlob, uploadItem.file.name);

    xhr.open(
      "POST",
      `${API_URL}/chunk-upload/${encodeURIComponent(uploadItem.uploadJobId)}/${chunkIndex}`,
    );
    xhr.setRequestHeader("Authorization", `Bearer ${token}`);
    xhr.send(formData);
  });

  requestEntry.promise = promise;
  uploadItem.activeRequests.set(chunkIndex, requestEntry);
  return promise;
}

function retryChunkOrFail(uploadItem, chunkIndex, message, reject) {
  if (uploadItem.cancelled || uploadItem.paused) {
    reject(new Error(message));
    return;
  }

  const retryCount = (uploadItem.retryCounts.get(chunkIndex) || 0) + 1;
  uploadItem.retryCounts.set(chunkIndex, retryCount);

  if (retryCount > UPLOAD_MAX_RETRIES) {
    reject(new Error(`${message} after ${UPLOAD_MAX_RETRIES} retries.`));
    return;
  }

  updateUploadStatus(uploadItem.id, `Retrying chunk ${chunkIndex + 1} (${retryCount}/${UPLOAD_MAX_RETRIES})…`, false);
  setTimeout(() => {
    if (!uploadItem.cancelled && !uploadItem.paused) {
      uploadOneChunk(uploadItem, chunkIndex).then(() => {}, reject);
    } else {
      reject(new Error(message));
    }
  }, UPLOAD_RETRY_BASE_MS * Math.pow(2, retryCount - 1));
}

async function pauseUpload(localUploadId) {
  const uploadItem = activeUploads.get(localUploadId);
  if (!uploadItem || !uploadItem.uploadJobId || uploadItem.status === "completed") return;

  uploadItem.paused = true;
  uploadItem.status = "pausing";
  updateUploadStatus(localUploadId, "Pausing…", false);

  for (const entry of uploadItem.activeRequests.values()) {
    entry.xhr.abort();
  }

  try {
    const response = await apiCall(`/chunk-upload/${encodeURIComponent(uploadItem.uploadJobId)}/pause`, {
      method: "POST",
      skipDedupe: true,
    });
    if (!response || !response.ok) {
      throw await responseToUploadError(response, "Unable to pause upload.");
    }
    uploadItem.status = "paused";
    updateUploadStatus(localUploadId, "Paused", false);
    toggleUploadPauseButtons(localUploadId, true);
  } catch (error) {
    uploadItem.paused = false;
    uploadItem.status = "uploading";
    updateUploadStatus(localUploadId, error.message || "Pause failed", false);
  }
}

async function resumeUpload(localUploadId) {
  const uploadItem = activeUploads.get(localUploadId);
  if (!uploadItem || !uploadItem.uploadJobId || uploadItem.status === "completed") return;

  try {
    uploadItem.paused = false;
    uploadItem.status = "resuming";
    updateUploadStatus(localUploadId, "Resuming…", false);
    toggleUploadPauseButtons(localUploadId, false);

    const response = await apiCall(
      `/chunk-upload/${encodeURIComponent(uploadItem.uploadJobId)}/resume`,
      { method: "POST", skipDedupe: true },
    );
    if (!response || !response.ok) {
      throw await responseToUploadError(response, "Unable to resume upload.");
    }

    const indexes = await response.json();
    uploadItem.completedChunks = new Set(
      Array.isArray(indexes) ? indexes.map(Number).filter(Number.isInteger) : [],
    );
    uploadItem.uploadedBytes = calculateCompletedBytes(uploadItem);
    updateUploadProgress(localUploadId);

    uploadItem.status = "uploading";
    await uploadRemainingChunks(uploadItem);

    if (uploadItem.completedChunks.size === uploadItem.totalChunks) {
      uploadItem.status = "completed";
      uploadItem.uploadedBytes = uploadItem.file.size;
      updateUploadProgress(localUploadId);
      updateUploadStatus(localUploadId, "Completed ✅", true);
      removeUploadActionButtons(localUploadId);
      safeToast(`Uploaded: ${uploadItem.file.name}`, "success");
    }
  } catch (error) {
    if (uploadItem.cancelled) return;
    uploadItem.status = "failed";
    updateUploadStatus(localUploadId, error.message || "Resume failed", true);
  }
  checkAllUploadsComplete();
}

async function cancelUpload(localUploadId) {
  const uploadItem = activeUploads.get(localUploadId);
  if (!uploadItem) return;

  uploadItem.cancelled = true;
  uploadItem.status = "cancelled";
  for (const entry of uploadItem.activeRequests.values()) entry.xhr.abort();

  if (uploadItem.uploadJobId) {
    try {
      await apiCall(`/chunk-upload/${encodeURIComponent(uploadItem.uploadJobId)}`, {
        method: "DELETE",
        skipDedupe: true,
      });
    } catch (error) {
      console.warn("Unable to cancel server upload:", error);
    }
  }

  updateUploadStatus(localUploadId, "Cancelled ✖️", true);
  removeUploadActionButtons(localUploadId);
  safeToast(`Upload cancelled: ${uploadItem.file.name}`, "info");
  checkAllUploadsComplete();
}

function calculateCompletedBytes(uploadItem) {
  let bytes = 0;
  for (const index of uploadItem.completedChunks) {
    const start = index * uploadItem.chunkSize;
    const end = Math.min(uploadItem.file.size, start + uploadItem.chunkSize);
    bytes += Math.max(0, end - start);
  }
  return bytes;
}

function updateUploadProgress(localUploadId, inFlightBytes = 0) {
  const uploadItem = activeUploads.get(localUploadId);
  if (!uploadItem) return;

  const displayBytes = Math.min(uploadItem.file.size, uploadItem.uploadedBytes + Math.max(0, inFlightBytes));
  const percent = uploadItem.file.size === 0 ? 100 : Math.min(100, (displayBytes / uploadItem.file.size) * 100);
  uploadItem.progress = percent;

  const now = performance.now();
  if (uploadItem.lastProgressAt) {
    const elapsedSeconds = (now - uploadItem.lastProgressAt) / 1000;
    const deltaBytes = Math.max(0, displayBytes - uploadItem.lastProgressBytes);
    if (elapsedSeconds >= 0.25 && deltaBytes >= 0) {
      const instantSpeed = deltaBytes / elapsedSeconds;
      uploadItem.speed = uploadItem.speed > 0 ? uploadItem.speed * 0.7 + instantSpeed * 0.3 : instantSpeed;
      uploadItem.lastProgressAt = now;
      uploadItem.lastProgressBytes = displayBytes;
    }
  } else {
    uploadItem.lastProgressAt = now;
    uploadItem.lastProgressBytes = displayBytes;
  }

  const itemDiv = document.getElementById(`upload-${localUploadId}`);
  if (!itemDiv) return;
  const fill = itemDiv.querySelector(".progress-fill");
  const bytes = itemDiv.querySelector(".upload-progress-bytes");
  const percentEl = itemDiv.querySelector(".upload-progress-percent");
  const speed = itemDiv.querySelector(".upload-speed");
  const eta = itemDiv.querySelector(".upload-eta");
  const chunks = itemDiv.querySelector(".upload-chunks");

  if (fill) fill.style.width = `${percent.toFixed(1)}%`;
  if (bytes) bytes.textContent = `${formatUploadBytes(displayBytes)} / ${formatUploadBytes(uploadItem.file.size)}`;
  if (percentEl) percentEl.textContent = `${Math.round(percent)}%`;
  if (speed) speed.textContent = `Speed: ${formatUploadSpeed(uploadItem.speed)}`;
  if (eta) eta.textContent = uploadItem.speed > 0 ? `ETA: ${formatUploadEta((uploadItem.file.size - displayBytes) / uploadItem.speed)}` : "ETA: calculating…";
  if (chunks) chunks.textContent = uploadItem.totalChunks ? `Chunks: ${uploadItem.completedChunks.size} / ${uploadItem.totalChunks}` : "Chunks: preparing…";
}

function updateUploadStatus(uploadId, text, isFinal) {
  const itemDiv = document.getElementById(`upload-${uploadId}`);
  if (!itemDiv) return;
  const statusSpan = itemDiv.querySelector(".status-text");
  if (statusSpan) {
    statusSpan.textContent = text;
    if (isFinal) {
      if (text.includes("Completed")) statusSpan.className = "status-text completed-status";
      else if (text.includes("Failed") || text.includes("Error") || text.includes("failed")) statusSpan.className = "status-text failed-status";
      else if (text.includes("Cancelled")) statusSpan.className = "status-text cancelled-status";
    }
  }
}

function toggleUploadPauseButtons(uploadId, paused) {
  const itemDiv = document.getElementById(`upload-${uploadId}`);
  if (!itemDiv) return;
  const pause = itemDiv.querySelector(".pause-upload");
  const resume = itemDiv.querySelector(".resume-upload");
  if (pause) pause.style.display = paused ? "none" : "inline-flex";
  if (resume) resume.style.display = paused ? "inline-flex" : "none";
}

function removeUploadActionButtons(uploadId) {
  const itemDiv = document.getElementById(`upload-${uploadId}`);
  if (!itemDiv) return;
  const actions = itemDiv.querySelector(".upload-actions");
  if (actions) actions.remove();
}

async function responseToUploadError(response, fallback) {
  if (!response) return new Error(fallback);
  try {
    const data = await response.json();
    return new Error(data.message || data.error || fallback + ` (HTTP ${response.status})`);
  } catch (_) {
    try {
      const text = await response.text();
      return new Error(text || fallback + ` (HTTP ${response.status})`);
    } catch (_) {
      return new Error(fallback + ` (HTTP ${response.status})`);
    }
  }
}

function xhrErrorMessage(xhr, fallback) {
  try {
    const data = JSON.parse(xhr.responseText || "{}");
    return data.message || data.error || `${fallback} (HTTP ${xhr.status})`;
  } catch (_) {
    return xhr.responseText || `${fallback} (HTTP ${xhr.status})`;
  }
}

function checkAllUploadsComplete() {
  const remaining = Array.from(activeUploads.values()).filter(
    (item) => item.status === "pending" || item.status === "creating" || item.status === "uploading" || item.status === "resuming" || item.status === "pausing",
  );
  if (remaining.length === 0 && activeUploads.size > 0) {
    setTimeout(async () => {
      if (typeof loadFiles === "function") await loadFiles();
      setTimeout(() => {
        const hasActive = Array.from(activeUploads.values()).some((item) =>
          ["pending", "creating", "uploading", "resuming", "pausing"].includes(item.status),
        );
        if (!hasActive) {
          hideUploadModal();
          const list = document.getElementById("uploadList");
          if (list) list.innerHTML = "";
          uploadQueue = [];
          activeUploads.clear();
        }
      }, 1000);
    }, 300);
  }
}

function closeUploadModal() {
  const active = Array.from(activeUploads.values()).filter((item) =>
    ["pending", "creating", "uploading", "resuming", "pausing"].includes(item.status),
  );
  if (active.length > 0) {
    if (!confirm("Uploads in progress. Cancel them?")) return;
    active.forEach((item) => cancelUpload(item.id));
  }
  hideUploadModal();
}

window.addToUploadQueue = addToUploadQueue;
window.cancelUpload = cancelUpload;
window.pauseUpload = pauseUpload;
window.resumeUpload = resumeUpload;
window.closeUploadModal = closeUploadModal;
window.showUploadModal = showUploadModal;
window.hideUploadModal = hideUploadModal;

console.log("✅ production resumable upload.js loaded");
