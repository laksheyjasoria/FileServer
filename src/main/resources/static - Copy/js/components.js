// Component Templates
function createUploadQueueItem(uploadId, filename) {
    const div = document.createElement('div');
    div.className = 'upload-item';
    div.id = `upload-${uploadId}`;
    div.innerHTML = `
        <div class="upload-filename">${escapeHtml(filename)}</div>
        <div class="progress-bar">
            <div class="progress-fill" style="width: 0%"></div>
        </div>
        <div class="upload-status">
            <span class="status-text">Pending...</span>
            <button class="cancel-upload" onclick="cancelUpload('${uploadId}')">Cancel</button>
        </div>
    `;
    return div;
}

function createFileItem(item, isSelected, isFolder, isProtected, doubleClickAction) {
    const icon = isFolder ? '📁' : getFileIcon(item.name);
    const info = isFolder ? (item.hasChildren ? 'Contains items' : 'Empty') : formatFileSize(item.fileSize);
    const lockIcon = isProtected ? '🔒 ' : '';
    
    return `
        <div class="file-item ${isSelected ? 'selected' : ''}" 
             data-id="${item.id}" 
             data-parent-id="${item.parentId || ''}"
             data-type="${item.driveType}" 
             data-name="${escapeHtml(item.name)}"
             ondblclick="${doubleClickAction}"
             onclick="handleItemClick(event, ${item.id}, ${isFolder})">
            <input type="checkbox" class="checkbox" ${isSelected ? 'checked' : ''} onchange="toggleSelectItem(event, ${item.id}, this.checked)">
            <div class="file-icon">${icon}</div>
            <div class="file-name">${lockIcon}${escapeHtml(item.name)}</div>
            <div class="file-info">${info}</div>
            <div class="file-menu" onclick="showContextMenu(event, ${item.id}, '${escapeHtml(item.name)}', '${item.driveType}', '${item.fileType || ''}', ${isProtected}, ${item.parentId || 'null'})">
                ⋮
            </div>
        </div>
    `;
}