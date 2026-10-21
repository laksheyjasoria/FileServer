// Resumable chunked uploader for the FileServer Telegram-backed storage path.
// The server is authoritative for chunk size, completed chunks and finalization.

const UPLOAD_PARALLELISM = 3;
const UPLOAD_MAX_RETRIES = 3;


const UPLOAD_SESSION_STORAGE_KEY = 'fileserver.resumableUploadSessions';
function readSavedUploadSessions() {
  try {
    const parsed = JSON.parse(localStorage.getItem(UPLOAD_SESSION_STORAGE_KEY) || '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch (_) { return []; }
}
function saveUploadSession(item) {
  if (!item.uploadId) return;
  const sessions = readSavedUploadSessions().filter(s => s.uploadId !== item.uploadId);
  sessions.push({
    uploadId: item.uploadId,
    fileName: item.file.name,
    totalSize: item.file.size,
    chunkSize: item.chunkSize,
    totalChunks: item.totalChunks,
    savedAt: Date.now(),
  });
  try { localStorage.setItem(UPLOAD_SESSION_STORAGE_KEY, JSON.stringify(sessions.slice(-30))); }
  catch (error) { console.warn('Could not persist resumable upload metadata:', error); }
}
function findSavedUploadSession(file) {
  return readSavedUploadSessions().find(s => s.fileName === file.name && Number(s.totalSize) === file.size) || null;
}
function removeSavedUploadSession(uploadId) {
  if (!uploadId) return;
  try {
    localStorage.setItem(UPLOAD_SESSION_STORAGE_KEY,
      JSON.stringify(readSavedUploadSessions().filter(s => s.uploadId !== uploadId)));
  } catch (_) { }
}

function showUploadModal() {
  const modal = document.getElementById('uploadModal');
  if (modal) { modal.classList.add('active'); modal.style.display = 'flex'; }
}
function hideUploadModal() {
  const modal = document.getElementById('uploadModal');
  if (modal) { modal.classList.remove('active'); modal.style.display = 'none'; }
}

function addToUploadQueue(files) {
  if (!files || !files.length) return;
  showUploadModal();
  const list = document.getElementById('uploadList');
  if (!list) { console.error('uploadList container not found'); return; }

  for (const file of files) {
    const id = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const prior = findSavedUploadSession(file);
    const resumePrior = prior && confirm(`A previous upload session exists for "${file.name}" (${formatUploadBytes(file.size)}). Resume it?`);
    const item = {
      id, file, uploadId: resumePrior ? prior.uploadId : null,
      chunkSize: resumePrior ? Number(prior.chunkSize) : 0,
      totalChunks: resumePrior ? Number(prior.totalChunks) : 0,
      completed: new Set(), failed: new Set(), inFlight: new Map(),
      status: 'pending', pauseRequested: false, cancelRequested: false,
      runner: null, uploadedBytes: 0, lastError: null, startedAt: Date.now(),
      lastSampleAt: Date.now(), lastSampleBytes: 0, speedBps: 0,
    };
    uploadQueue.push(item);
    activeUploads.set(id, item);

    const row = document.createElement('div');
    row.className = 'upload-item';
    row.id = `upload-${id}`;
    row.innerHTML = `
      <div class="upload-filename">${escapeHtml(file.name)}</div>
      <div class="progress-bar"><div class="progress-fill" style="width:0%"></div></div>
      <div class="upload-metrics">0 B / ${formatUploadBytes(file.size)} · calculating speed…</div>
      <div class="upload-status">
        <span class="status-text">Preparing upload…</span>
        <button type="button" class="pause-upload" onclick="pauseUpload('${id}')">Pause</button>
        <button type="button" class="resume-upload" onclick="resumeUpload('${id}')" disabled>Resume</button>
        <button type="button" class="cancel-upload" onclick="cancelUpload('${id}')">Cancel</button>
        <button type="button" class="remove-upload" onclick="removeUploadItem('${id}')" hidden>Remove</button>
      </div>`;
    list.appendChild(row);
    void startUpload(id);
  }
}

function authHeaders() {
  const token = localStorage.getItem('jwtToken');
  return token ? { Authorization: `Bearer ${token}` } : {};
}
function uploadUrl(path) { return `${API_URL}${path}`; }

async function apiRequest(path, options = {}) {
  const response = await fetch(uploadUrl(path), {
    ...options,
    headers: { ...authHeaders(), ...(options.headers || {}) },
  });
  const text = await response.text();
  let body = null;
  if (text) { try { body = JSON.parse(text); } catch (_) { body = text; } }
  if (!response.ok) {
    const message = body && typeof body === 'object' ? (body.message || body.error) : body;
    throw new Error(message || `HTTP ${response.status}`);
  }
  return body;
}

async function startUpload(id) {
  const item = activeUploads.get(id);
  if (!item || item.status === 'cancelled' || item.status === 'completed') return;
  try {
    // Check for a duplicate only when creating a new server-side upload session.
    if (!item.uploadId) {
      try {
        const files = await getFilesForFolder(currentFolderId);
        if (files.some(f => String(f.name || '').toLowerCase() === item.file.name.toLowerCase())) {
          throw new Error(`A file named "${item.file.name}" already exists in this folder.`);
        }
      } catch (error) {
        if (error.message && error.message.includes('already exists')) throw error;
        // Listing may be unavailable for some folder states; the upload API remains authoritative.
      }
      setUploadStatus(id, 'Creating resumable upload…');
      const created = await apiRequest('/chunk-upload/create', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          fileName: item.file.name,
          totalSize: item.file.size,
          contentType: item.file.type || 'application/octet-stream',
          parentId: currentFolderId || null,
        }),
      });
      item.uploadId = created.uploadId || created.id;
      item.chunkSize = Number(created.chunkSize);
      item.totalChunks = Number(created.totalChunks);
      saveUploadSession(item);
      if (!item.uploadId || !Number.isSafeInteger(item.chunkSize) || item.chunkSize <= 0 ||
          !Number.isSafeInteger(item.totalChunks) || item.totalChunks < 0) {
        throw new Error('Server returned invalid upload-session metadata.');
      }
      if (item.file.size === 0) {
        const emptyStatus = await apiRequest(`/chunk-upload/${encodeURIComponent(item.uploadId)}/status`);
        if (emptyStatus.status !== 'COMPLETED') throw new Error('Server did not finalize the empty file.');
        item.status = 'completed';
        removeSavedUploadSession(item.uploadId);
        setUploadStatus(item.id, 'Completed ✓');
        setUploadButtons(item);
        updateUploadProgress(item);
        checkAllUploadsComplete();
        return;
      }
    } else {
      await apiRequest(`/chunk-upload/${encodeURIComponent(item.uploadId)}/resume`, { method: 'POST' });
    }
    await runUpload(item);
  } catch (error) {
    if (item.cancelRequested) return;
    item.status = item.pauseRequested ? 'paused' : 'failed';
    item.lastError = error;
    setUploadStatus(id, item.pauseRequested ? 'Paused' : `Failed: ${error.message}`);
    setUploadButtons(item);
    checkAllUploadsComplete();
  }
}

async function runUpload(item) {
  if (item.runner) return item.runner;
  item.status = 'uploading';
  item.pauseRequested = false;
  setUploadButtons(item);
  item.runner = (async () => {
    try {
      const completed = await apiRequest(`/chunk-upload/${encodeURIComponent(item.uploadId)}/resume`);
      item.completed = new Set((Array.isArray(completed) ? completed : completed?.uploadedChunks || [])
        .map(Number).filter(n => Number.isInteger(n) && n >= 0 && n < item.totalChunks));
      item.failed.clear();
      item.uploadedBytes = [...item.completed].reduce((sum, index) => sum + chunkLength(item, index), 0);
      updateUploadProgress(item);

      let cursor = 0;
      const worker = async () => {
        while (!item.pauseRequested && !item.cancelRequested) {
          let index = -1;
          while (cursor < item.totalChunks) {
            const candidate = cursor++;
            if (!item.completed.has(candidate)) { index = candidate; break; }
          }
          if (index < 0) return;
          try {
            await sendChunkWithRetry(item, index);
            item.completed.add(index);
            item.failed.delete(index);
            item.uploadedBytes = [...item.completed].reduce((sum, n) => sum + chunkLength(item, n), 0);
            updateUploadProgress(item);
            setUploadStatus(item.id, `Uploaded ${item.completed.size}/${item.totalChunks} chunks`);
          } catch (error) {
            item.failed.add(index);
            item.lastError = error;
            setUploadStatus(item.id, `Chunk ${index + 1} failed: ${error.message}`);
          }
        }
      };

      await Promise.all(Array.from({ length: Math.min(UPLOAD_PARALLELISM, Math.max(1, item.totalChunks)) }, worker));
      if (item.cancelRequested) return;
      if (item.pauseRequested) {
        item.status = 'paused';
        setUploadStatus(item.id, 'Paused. Resume to continue from completed chunks.');
        setUploadButtons(item);
        return;
      }
      if (item.failed.size) {
        item.status = 'failed';
        setUploadStatus(item.id, `${item.failed.size} chunk(s) failed. Click Resume to retry.`);
        setUploadButtons(item);
        return;
      }

      const status = await apiRequest(`/chunk-upload/${encodeURIComponent(item.uploadId)}/status`);
      if (status.status !== 'COMPLETED') {
        item.status = 'failed';
        throw new Error(`Server has not finalized the upload (status: ${status.status}).`);
      }
      item.status = 'completed';
      removeSavedUploadSession(item.uploadId);
      item.uploadedBytes = item.file.size;
      updateUploadProgress(item);
      setUploadStatus(item.id, 'Completed ✓');
      setUploadButtons(item);
      safeToast(`Uploaded: ${item.file.name}`, 'success');
      checkAllUploadsComplete();
    } finally {
      item.runner = null;
    }
  })();
  return item.runner;
}

function chunkLength(item, index) {
  const start = index * item.chunkSize;
  return Math.max(0, Math.min(item.chunkSize, item.file.size - start));
}

async function sendChunkWithRetry(item, index) {
  let lastError;
  for (let attempt = 1; attempt <= UPLOAD_MAX_RETRIES; attempt++) {
    if (item.cancelRequested) throw new Error('Upload cancelled.');
    const start = index * item.chunkSize;
    const blob = item.file.slice(start, Math.min(start + item.chunkSize, item.file.size));
    try {
      await sendChunk(item, index, blob);
      return;
    } catch (error) {
      lastError = error;
      if (item.pauseRequested || item.cancelRequested) throw error;
      if (attempt < UPLOAD_MAX_RETRIES) await new Promise(resolve => setTimeout(resolve, 400 * attempt));
    }
  }
  throw lastError || new Error('Chunk upload failed.');
}

function sendChunk(item, index, blob) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    item.inFlight.set(index, { xhr, loaded: 0, total: blob.size });
    xhr.open('POST', uploadUrl(`/chunk-upload/${encodeURIComponent(item.uploadId)}/${index}`), true);
    const token = localStorage.getItem('jwtToken');
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    xhr.upload.onprogress = event => {
      const current = item.inFlight.get(index);
      if (current) current.loaded = event.loaded;
      updateUploadProgress(item);
    };
    xhr.onload = () => {
      item.inFlight.delete(index);
      if (xhr.status >= 200 && xhr.status < 300) resolve();
      else reject(new Error(`HTTP ${xhr.status}: ${xhr.responseText || xhr.statusText}`));
      updateUploadProgress(item);
    };
    xhr.onerror = () => { item.inFlight.delete(index); reject(new Error('Network error')); updateUploadProgress(item); };
    xhr.onabort = () => { item.inFlight.delete(index); reject(new Error('Request aborted')); updateUploadProgress(item); };
    const form = new FormData();
    form.append('file', blob, item.file.name);
    xhr.send(form);
  });
}

async function pauseUpload(id) {
  const item = activeUploads.get(id);
  if (!item || item.status !== 'uploading') return;
  item.pauseRequested = true;
  setUploadStatus(id, 'Pausing…');
  // Abort in-flight requests so the queue can settle promptly. Completed chunks
  // remain server-confirmed and are re-read on resume.
  for (const { xhr } of item.inFlight.values()) xhr.abort();
  try {
    await apiRequest(`/chunk-upload/${encodeURIComponent(item.uploadId)}/pause`, { method: 'POST' });
  } catch (error) {
    // Pause can race with finalization or another request; reconcile on resume.
    console.warn('Server-side pause request did not complete:', error);
  }
  setUploadButtons(item);
}

async function resumeUpload(id) {
  const item = activeUploads.get(id);
  if (!item || !['paused', 'failed'].includes(item.status)) return;
  item.pauseRequested = false;
  item.failed.clear();
  item.status = 'uploading';
  setUploadStatus(id, 'Resuming from server-confirmed completed chunks…');
  setUploadButtons(item);
  await startUpload(id);
}

async function cancelUpload(id) {
  const item = activeUploads.get(id);
  if (!item || item.status === 'completed' || item.status === 'cancelled') return;
  item.cancelRequested = true;
  item.status = 'cancelled';
  for (const { xhr } of item.inFlight.values()) xhr.abort();
  if (item.uploadId) {
    try { await apiRequest(`/chunk-upload/${encodeURIComponent(item.uploadId)}`, { method: 'DELETE' }); }
    catch (error) { console.warn('Server-side cancellation failed:', error); }
  }
  removeSavedUploadSession(item.uploadId);
  setUploadStatus(id, 'Cancelled');
  setUploadButtons(item);
  checkAllUploadsComplete();
}

function setUploadStatus(id, message) {
  const row = document.getElementById(`upload-${id}`);
  const span = row?.querySelector('.status-text');
  if (span) span.textContent = message;
}
function setUploadButtons(item) {
  const row = document.getElementById(`upload-${item.id}`);
  if (!row) return;
  const pause = row.querySelector('.pause-upload');
  const resume = row.querySelector('.resume-upload');
  const cancel = row.querySelector('.cancel-upload');
  const remove = row.querySelector('.remove-upload');
  const terminal = ['completed', 'cancelled'].includes(item.status);
  if (pause) { pause.hidden = terminal; pause.disabled = item.status !== 'uploading'; }
  if (resume) { resume.hidden = terminal; resume.disabled = !['paused', 'failed'].includes(item.status); }
  if (cancel) { cancel.hidden = terminal; cancel.disabled = terminal; }
  if (remove) remove.hidden = !terminal;
}

function removeUploadItem(id) {
  const item = activeUploads.get(id);
  if (!item || !['completed', 'cancelled'].includes(item.status)) return;
  document.getElementById(`upload-${id}`)?.remove();
  activeUploads.delete(id);
  checkAllUploadsComplete();
}
function updateUploadProgress(item) {
  const inFlightBytes = [...item.inFlight.values()].reduce((sum, x) => sum + Math.min(x.loaded, x.total), 0);
  const total = item.file.size || 0;
  const done = Math.min(total, item.uploadedBytes + inFlightBytes);
  const percent = total === 0 ? (item.status === 'completed' ? 100 : 0) : Math.min(100, Math.floor(done * 100 / total));
  const now = Date.now();
  const sampleMs = now - item.lastSampleAt;
  if (sampleMs >= 500) {
    const sampleSpeed = Math.max(0, (done - item.lastSampleBytes) * 1000 / sampleMs);
    item.speedBps = item.speedBps ? item.speedBps * 0.65 + sampleSpeed * 0.35 : sampleSpeed;
    item.lastSampleAt = now;
    item.lastSampleBytes = done;
  }
  const remaining = Math.max(0, total - done);
  const etaSeconds = item.speedBps > 0 ? Math.ceil(remaining / item.speedBps) : null;
  const row = document.getElementById(`upload-${item.id}`);
  const fill = row?.querySelector('.progress-fill');
  const metrics = row?.querySelector('.upload-metrics');
  if (fill) fill.style.width = `${percent}%`;
  if (metrics) {
    const speedText = item.speedBps > 0 ? `${formatUploadBytes(item.speedBps)}/s` : 'calculating speed…';
    const etaText = item.status === 'completed' ? 'complete' : (etaSeconds == null ? 'ETA —' : `ETA ${formatUploadDuration(etaSeconds)}`);
    metrics.textContent = `${formatUploadBytes(done)} / ${formatUploadBytes(total)} · ${speedText} · ${etaText}`;
  }
}
function formatUploadBytes(value) {
  if (!Number.isFinite(value) || value <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let n = value, unit = 0;
  while (n >= 1024 && unit < units.length - 1) { n /= 1024; unit++; }
  return `${n.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
}
function formatUploadDuration(seconds) {
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60), remaining = seconds % 60;
  if (minutes < 60) return `${minutes}m ${remaining}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
}
function checkAllUploadsComplete() {
  const remaining = Array.from(activeUploads.values()).some(item =>
    ['pending', 'uploading'].includes(item.status));
  if (!remaining) {
    if (typeof loadFiles === 'function') void loadFiles();
  }
}
function closeUploadModal() {
  const running = Array.from(activeUploads.values()).filter(item => ['pending', 'uploading'].includes(item.status));
  if (running.length && !confirm('Uploads are in progress. Cancel them before closing?')) return;
  running.forEach(item => { void cancelUpload(item.id); });
  hideUploadModal();
}

window.addToUploadQueue = addToUploadQueue;
window.cancelUpload = cancelUpload;
window.removeUploadItem = removeUploadItem;
window.pauseUpload = pauseUpload;
window.resumeUpload = resumeUpload;
window.closeUploadModal = closeUploadModal;
window.showUploadModal = showUploadModal;
window.hideUploadModal = hideUploadModal;
console.log('FileServer resumable chunk uploader loaded.');
