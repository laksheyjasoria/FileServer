// viewer.js – single file viewer with JWT token (for authenticated users)

// Helper: Use global toast or fallback to alert
function safeToast(message, type = 'info', duration = 3000) {
    if (typeof showToast === 'function') {
        showToast(message, type, duration);
    } else {
        alert(message);
    }
}

const urlParams = new URLSearchParams(window.location.search);
const fileId = urlParams.get('id');
const token = urlParams.get('token');

let fileInfo = null;

if (!fileId || !token) {
    showError('Invalid file link. Missing file ID or authentication token.');
} else {
    loadFile();
}

async function loadFile() {
    try {
        // Fetch file metadata
        const infoResponse = await fetch(`${API_URL}/download/${fileId}?metadata=true`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!infoResponse.ok) {
            if (infoResponse.status === 401) throw new Error('Authentication failed. Please login again.');
            if (infoResponse.status === 404) throw new Error('File not found.');
            throw new Error('Failed to get file information');
        }

        fileInfo = await infoResponse.json();

        document.getElementById('fileName').textContent = fileInfo.name;
        document.getElementById('fileIcon').textContent = getFileIcon(fileInfo.name);
        document.title = `${fileInfo.name} - File Viewer`;

        const fileSize = formatFileSize(fileInfo.size);
        const fileType = fileInfo.contentType || 'Unknown type';
        document.getElementById('fileMeta').textContent = `${fileSize} • ${fileType}`;

        document.getElementById('downloadBtn').onclick = () => downloadFile();

        // Fetch file content
        const fileResponse = await fetch(`${API_URL}/download/${fileId}`, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!fileResponse.ok) {
            if (fileResponse.status === 404) throw new Error('Stream endpoint not found. Please check server configuration.');
            throw new Error(`Failed to load file content (Status: ${fileResponse.status})`);
        }

        const blob = await fileResponse.blob();
        if (blob.size === 0) throw new Error('File is empty');

        const url = URL.createObjectURL(blob);
        const contentType = fileInfo.contentType || blob.type;
        displayFile(url, contentType, fileInfo.name);

        window.addEventListener('beforeunload', () => URL.revokeObjectURL(url));
    } catch (error) {
        console.error('Error loading file:', error);
        showError(error.message);
        safeToast('Failed to load file: ' + error.message, 'error');
    }
}

function displayFile(url, contentType, filename) {
    const container = document.getElementById('viewerContainer');
    const ext = filename.split('.').pop().toLowerCase();

    if (contentType.startsWith('image/')) {
        container.innerHTML = `<img src="${url}" alt="${filename}" class="image-viewer" onerror="handleLoadError(this)">`;
        return;
    }
    if (contentType === 'application/pdf') {
        container.innerHTML = `<iframe src="${url}" class="pdf-viewer" title="${filename}"></iframe>`;
        return;
    }
    if (contentType.startsWith('video/')) {
        container.innerHTML = `
            <video controls class="video-viewer" autoplay>
                <source src="${url}" type="${contentType}">
                Your browser does not support video playback.
            </video>
        `;
        return;
    }
    if (contentType.startsWith('audio/')) {
        container.innerHTML = `
            <audio controls class="audio-viewer" autoplay>
                <source src="${url}" type="${contentType}">
                Your browser does not support audio playback.
            </audio>
        `;
        return;
    }
    if (contentType.startsWith('text/') || ['txt', 'log', 'md', 'csv'].includes(ext)) {
        fetch(url)
            .then(res => res.text())
            .then(text => {
                container.innerHTML = `<div class="text-viewer">${escapeHtml(text)}</div>`;
            })
            .catch(() => {
                container.innerHTML = `<div class="text-viewer">Failed to load text content.</div>`;
                safeToast('Failed to load text content', 'error');
            });
        return;
    }
    if (['js', 'java', 'py', 'html', 'css', 'json', 'xml', 'yaml', 'yml', 'sql', 'sh', 'bat'].includes(ext)) {
        fetch(url)
            .then(res => res.text())
            .then(text => {
                container.innerHTML = `<pre class="code-viewer">${escapeHtml(text)}</pre>`;
            })
            .catch(() => {
                container.innerHTML = `<div class="code-viewer">Failed to load code content.</div>`;
                safeToast('Failed to load code content', 'error');
            });
        return;
    }
    if (['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(ext)) {
        container.innerHTML = `
            <div class="unsupported">
                <div class="unsupported-icon">📄</div>
                <h3>${filename}</h3>
                <p>This file type cannot be previewed in the browser.</p>
                <button class="btn btn-primary" onclick="downloadFile()" style="margin-top:20px;">⬇️ Download to View</button>
            </div>
        `;
        return;
    }
    container.innerHTML = `
        <div class="unsupported">
            <div class="unsupported-icon">📎</div>
            <h3>${filename}</h3>
            <p>Preview not available for this file type.</p>
            <button class="btn btn-primary" onclick="downloadFile()" style="margin-top:20px;">⬇️ Download File</button>
        </div>
    `;
}

function handleLoadError(element) {
    element.onerror = null;
    const container = document.getElementById('viewerContainer');
    container.innerHTML = `
        <div class="error">
            <div class="error-icon">🖼️</div>
            <h3>Failed to load image</h3>
            <p>The image could not be displayed.</p>
            <button class="btn btn-primary" onclick="downloadFile()" style="margin-top:20px;">⬇️ Download Instead</button>
        </div>
    `;
    safeToast('Failed to load image', 'error');
}

function downloadFile() {
    safeToast('Preparing download...', 'info', 2000);
    fetch(`${API_URL}/download/${fileId}`, { headers: { 'Authorization': `Bearer ${token}` } })
        .then(response => {
            if (!response.ok) throw new Error('Download failed');
            return response.blob();
        })
        .then(blob => {
            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = fileInfo ? fileInfo.name : 'download';
            link.click();
            URL.revokeObjectURL(link.href);
            safeToast('Download started!', 'success', 2000);
        })
        .catch(error => {
            console.error('Download error:', error);
            safeToast('Download failed: ' + error.message, 'error');
        });
}

function closeViewer() {
    window.close();
}

function showError(message) {
    document.getElementById('fileName').textContent = 'Error';
    document.getElementById('fileMeta').textContent = 'Failed to load';
    document.title = 'Error - File Viewer';
    document.getElementById('viewerContainer').innerHTML = `
        <div class="error">
            <div class="error-icon">❌</div>
            <h3>Failed to Load File</h3>
            <p>${escapeHtml(message)}</p>
            <button class="btn btn-primary" onclick="window.close()" style="margin-top:20px;">✖️ Close</button>
        </div>
    `;
    // Also show toast for critical error (optional)
    safeToast(message, 'error');
}