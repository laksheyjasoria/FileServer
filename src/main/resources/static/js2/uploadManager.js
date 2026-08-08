// Upload Operations (static2 - adjusted endpoints)
function addToUploadQueue(files) {
    const uploadQueueDiv = document.getElementById('uploadQueue');
    const uploadList = document.getElementById('uploadList');

    uploadQueueDiv.style.display = 'block';

    for (const file of files) {
        const uploadId = Date.now() + '-' + Math.random();
        const uploadItem = {
            id: uploadId,
            file: file,
            progress: 0,
            status: 'pending',
            xhr: null
        };

        uploadQueue.push(uploadItem);
        activeUploads.set(uploadId, uploadItem);

        const itemDiv = document.createElement('div');
        itemDiv.className = 'upload-item';
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
        uploadList.appendChild(itemDiv);

        startUpload(uploadId);
    }
}

async function startUpload(uploadId) {
    const uploadItem = activeUploads.get(uploadId);
    if (!uploadItem || uploadItem.status === 'cancelled') return;

    // Check for duplicate filename
    try {
        const url = currentFolderId
            ? `/drive/${currentFolderId}/contents`
            : `/drive`;

        const response = await apiCall(url);
        const items = await response.json();

        const exists = items.some(item => item.name.toLowerCase() === uploadItem.file.name.toLowerCase());
        if (exists) {
            alert(`File "${uploadItem.file.name}" already exists in this location. Upload cancelled.`);
            cancelUpload(uploadId);
            return;
        }
    } catch (error) {
        console.error('Error checking duplicate:', error);
    }

    uploadItem.status = 'uploading';
    updateUploadStatus(uploadId, 'Uploading...', false);

    const formData = new FormData();
    formData.append('file', uploadItem.file);
    if (currentFolderId) {
        formData.append('parentId', currentFolderId);
    }

    if (!jwtToken) {
        uploadItem.status = 'failed';
        updateUploadStatus(uploadId, 'Please login again to upload', true);
        return;
    }

    const xhr = new XMLHttpRequest();
    uploadItem.xhr = xhr;

    xhr.upload.addEventListener('progress', (e) => {
        if (e.lengthComputable) {
            const percent = (e.loaded / e.total) * 100;
            uploadItem.progress = percent;
            const itemDiv = document.getElementById(`upload-${uploadId}`);
            const fill = itemDiv ? itemDiv.querySelector('.progress-fill') : null;
            if (fill) fill.style.width = `${percent}%`;
            updateUploadStatus(uploadId, `Uploading ${Math.round(percent)}%`, false);
        }
    });

    xhr.onload = () => {
        if (xhr.status >= 200 && xhr.status < 300) {
            uploadItem.status = 'completed';
            updateUploadStatus(uploadId, 'Completed ✅', true);
            const cancelBtn = document.querySelector(`#upload-${uploadId} .cancel-upload`);
            if (cancelBtn) cancelBtn.remove();
            loadFiles();
        } else {
            uploadItem.status = 'failed';
            let message = 'Upload failed';
            try {
                const error = JSON.parse(xhr.responseText);
                message = error.message || message;
            } catch (_) {
                message = xhr.responseText || message;
            }
            updateUploadStatus(uploadId, `${message} (HTTP ${xhr.status})`, true);
        }
    };

    xhr.onerror = () => {
        uploadItem.status = 'failed';
        updateUploadStatus(uploadId, 'Failed ❌', true);
    };

    xhr.open('POST', `${API_URL}/upload`);
    xhr.setRequestHeader('Authorization', `Bearer ${jwtToken}`);
    xhr.send(formData);
}

function updateUploadStatus(uploadId, text, isFinal) {
    const itemDiv = document.getElementById(`upload-${uploadId}`);
    if (itemDiv) {
        const statusSpan = itemDiv.querySelector('.status-text');
        if (statusSpan) {
            statusSpan.textContent = text;
            if (isFinal) {
                if (text.includes('Completed')) {
                    statusSpan.className = 'completed-status';
                } else if (text.includes('Failed')) {
                    statusSpan.className = 'failed-status';
                } else if (text.includes('Cancelled')) {
                    statusSpan.className = 'cancelled-status';
                }
            }
        }
    }
}

function cancelUpload(uploadId) {
    const uploadItem = activeUploads.get(uploadId);
    if (uploadItem && uploadItem.xhr) {
        uploadItem.xhr.abort();
        uploadItem.status = 'cancelled';
        updateUploadStatus(uploadId, 'Cancelled ✖️', true);

        const cancelBtn = document.querySelector(`#upload-${uploadId} .cancel-upload`);
        if (cancelBtn) cancelBtn.remove();
    }

    setTimeout(() => {
        const itemDiv = document.getElementById(`upload-${uploadId}`);
        if (itemDiv) {
            itemDiv.style.opacity = '0.5';
            setTimeout(() => {
                if (itemDiv.parentNode) itemDiv.remove();
            }, 2000);
        }
    }, 1000);

    const index = uploadQueue.findIndex(u => u.id === uploadId);
    if (index !== -1) uploadQueue.splice(index, 1);
    activeUploads.delete(uploadId);
}
