// viewer.js – Single file viewer

const urlParams = new URLSearchParams(window.location.search);
const fileId = urlParams.get("id");
const token = urlParams.get("token");
let fileInfo = null;
let fileBlob = null;
let fileObjectUrl = null;

if (!fileId || !token) {
  showError("Invalid file link. Missing file ID or authentication token.");
} else {
  loadFile();
}

async function loadFile() {
  try {
    const infoResponse = await fetch(
      `${API_URL}/download/${fileId}/metadata`,
      {
        headers: { Authorization: `Bearer ${token}` },
      },
    );
    if (!infoResponse.ok) {
      if (infoResponse.status === 401)
        throw new Error("Authentication failed. Please login again.");
      if (infoResponse.status === 404) throw new Error("File not found.");
      throw new Error("Failed to get file information");
    }
    fileInfo = await infoResponse.json();

    document.getElementById("fileName").textContent = fileInfo.name;
    document.getElementById("fileIcon").textContent = getFileIcon(
      fileInfo.name,
    );
    document.title = `${fileInfo.name} - File Viewer`;

    const fileSize = formatFileSize(fileInfo.size);
    const fileType = fileInfo.contentType || "Unknown type";
    document.getElementById("fileMeta").textContent =
      `${fileSize} • ${fileType}`;

    document.getElementById("downloadBtn").onclick = () => downloadFile();

    const fileResponse = await fetch(`${API_URL}/download/${fileId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!fileResponse.ok)
      throw new Error(
        `Failed to load file content (Status: ${fileResponse.status})`,
      );
    // Keep the downloaded bytes so the Download button does not fetch the
    // entire file a second time. Also detect truncated server streams explicitly.
    fileBlob = await fileResponse.blob();
    if (fileBlob.size === 0 && Number(fileInfo.size) > 0) {
      throw new Error("The server returned an empty file.");
    }
    const expectedSize = Number(fileInfo.size);
    if (Number.isFinite(expectedSize) && expectedSize >= 0 && fileBlob.size !== expectedSize) {
      throw new Error(
        `Incomplete file transfer: received ${formatFileSize(fileBlob.size)} of ${formatFileSize(expectedSize)}. Please retry the download.`,
      );
    }
    fileObjectUrl = URL.createObjectURL(fileBlob);
    const contentType = fileInfo.contentType || fileBlob.type || "application/octet-stream";
    displayFile(fileObjectUrl, contentType, fileInfo.name);
    window.addEventListener("beforeunload", () => {
      if (fileObjectUrl) URL.revokeObjectURL(fileObjectUrl);
    });
  } catch (error) {
    console.error("Error loading file:", error);
    showError(error.message);
    safeToast("Failed to load file: " + error.message, "error");
  }
}

function displayFile(url, contentType, filename) {
  const container = document.getElementById("viewerContainer");
  const ext = filename.split(".").pop().toLowerCase();

  if (contentType.startsWith("image/")) {
    container.innerHTML = `<img src="${url}" alt="${filename}" class="image-viewer" onerror="handleLoadError(this)">`;
  } else if (contentType === "application/pdf") {
    container.innerHTML = `<iframe src="${url}" class="pdf-viewer" title="${filename}"></iframe>`;
  } else if (contentType.startsWith("video/")) {
    container.innerHTML = `<video controls class="video-viewer" autoplay><source src="${url}" type="${contentType}">Your browser does not support video playback.</video>`;
  } else if (contentType.startsWith("audio/")) {
    container.innerHTML = `<audio controls class="audio-viewer" autoplay><source src="${url}" type="${contentType}">Your browser does not support audio playback.</audio>`;
  } else if (
    contentType.startsWith("text/") ||
    ["txt", "log", "md", "csv"].includes(ext)
  ) {
    fetch(url)
      .then((res) => res.text())
      .then((text) => {
        container.innerHTML = `<div class="text-viewer">${escapeHtml(text)}</div>`;
      })
      .catch(() => {
        container.innerHTML = `<div class="text-viewer">Failed to load text content.</div>`;
        safeToast("Failed to load text", "error");
      });
  } else if (
    [
      "js",
      "java",
      "py",
      "html",
      "css",
      "json",
      "xml",
      "yaml",
      "yml",
      "sql",
      "sh",
      "bat",
    ].includes(ext)
  ) {
    fetch(url)
      .then((res) => res.text())
      .then((text) => {
        container.innerHTML = `<pre class="code-viewer">${escapeHtml(text)}</pre>`;
      })
      .catch(() => {
        container.innerHTML = `<div class="code-viewer">Failed to load code content.</div>`;
        safeToast("Failed to load code", "error");
      });
  } else {
    container.innerHTML = `
            <div class="unsupported">
                <div class="unsupported-icon">📎</div>
                <h3>${filename}</h3>
                <p>Preview not available for this file type.</p>
                <button class="btn btn-primary" onclick="downloadFile()" style="margin-top:20px;">⬇️ Download File</button>
            </div>
        `;
  }
}

function handleLoadError(element) {
  element.onerror = null;
  const container = document.getElementById("viewerContainer");
  container.innerHTML = `
        <div class="error">
            <div class="error-icon">🖼️</div>
            <h3>Failed to load image</h3>
            <p>The image could not be displayed.</p>
            <button class="btn btn-primary" onclick="downloadFile()" style="margin-top:20px;">⬇️ Download Instead</button>
        </div>
    `;
  safeToast("Failed to load image", "error");
}

function downloadFile() {
  if (fileBlob) {
    triggerBlobDownload(fileBlob);
    return;
  }

  safeToast("Preparing download...", "info", 2000);
  fetch(`${API_URL}/download/${encodeURIComponent(fileId)}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
    .then(async (response) => {
      if (!response.ok) {
        const details = await response.text().catch(() => "");
        throw new Error(details || `Download failed (HTTP ${response.status})`);
      }
      return response.blob();
    })
    .then((blob) => {
      if (Number(fileInfo?.size) > 0 && blob.size !== Number(fileInfo.size)) {
        throw new Error(
          `Incomplete download: received ${formatFileSize(blob.size)} of ${formatFileSize(Number(fileInfo.size))}.`,
        );
      }
      triggerBlobDownload(blob);
    })
    .catch((error) => {
      console.error("File download failed:", error);
      safeToast("Download failed: " + error.message, "error");
    });
}

function triggerBlobDownload(blob) {
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = fileInfo ? fileInfo.name : "download";
  link.style.display = "none";
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Revoke only after the browser has had time to start consuming the blob URL.
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
  safeToast("Download started!", "success", 2000);
}

function closeViewer() {
  window.close();
}

function showError(message) {
  document.getElementById("fileName").textContent = "Error";
  document.getElementById("fileMeta").textContent = "Failed to load";
  document.title = "Error - File Viewer";
  document.getElementById("viewerContainer").innerHTML = `
        <div class="error">
            <div class="error-icon">❌</div>
            <h3>Failed to Load File</h3>
            <p>${escapeHtml(message)}</p>
            <button class="btn btn-primary" onclick="window.close()" style="margin-top:20px;">✖️ Close</button>
        </div>
    `;
  safeToast(message, "error");
}
