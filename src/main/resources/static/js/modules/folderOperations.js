// folderOperations.js – Create and rename folders

function togglePasswordField() {
  const isPrivate = document.getElementById("isPrivateToggle").checked;
  const passwordField = document.getElementById("passwordField");
  const passwordInput = document.getElementById("folderPassword");
  if (isPrivate) {
    passwordField.style.display = "block";
    passwordInput.required = true;
  } else {
    passwordField.style.display = "none";
    passwordInput.required = false;
    passwordInput.value = "";
  }
}

function resetFolderModal() {
  document.getElementById("folderName").value = "";
  document.getElementById("isPrivateToggle").checked = false;
  document.getElementById("passwordField").style.display = "none";
  document.getElementById("folderPassword").value = "";
}

function showCreateFolderModal() {
  resetFolderModal();
  showModal("folderModal");
}

async function createFolder() {
  const name = document.getElementById("folderName").value.trim();
  if (!name) {
    safeToast("Please enter a folder name", "warning");
    return;
  }
  const isPrivate = document.getElementById("isPrivateToggle").checked;
  const password = document.getElementById("folderPassword").value;
  if (isPrivate && (!password || password.length < 4)) {
    safeToast("Password required (min 4 chars) for private folder", "warning");
    return;
  }
  try {
    await checkDuplicateName(name, currentFolderId);
  } catch (error) {
    safeToast(error.message, "error");
    return;
  }

  // ✅ FIX: Use the button's ID instead of class selector
  const btn = document.getElementById("createFolderBtn");
  if (!btn) {
    safeToast("Create button not found.", "error");
    return;
  }
  const originalText = btn.textContent;
  btn.textContent = "Creating...";
  btn.disabled = true;

  try {
    const response = await apiCall("/resources/action", {
      method: "POST",
      body: JSON.stringify({
        action: "CREATE_FOLDER",
        ids: [],
        destination: currentFolderId || null,
        name,
      }),
    });
    if (!response.ok) throw new Error("Failed to create folder");
    closeModal("folderModal");
    resetFolderModal();
    await loadFiles();
    safeToast(`Folder "${name}" created.`, "success");
  } catch (error) {
    safeToast("Failed to create folder: " + error.message, "error");
  } finally {
    btn.textContent = originalText;
    btn.disabled = false;
  }
}

function showRenameModal(id, currentName) {
  contextMenuItem = { id, name: currentName };
  document.getElementById("newName").value = currentName;
  showModal("renameModal");
}

async function executeRename() {
  const newName = document.getElementById("newName").value.trim();
  if (!newName) {
    safeToast("Please enter a new name", "warning");
    return;
  }
  try {
    await checkDuplicateName(
      newName,
      contextMenuItem.parentId || currentFolderId,
    );
  } catch (error) {
    safeToast(error.message, "error");
    return;
  }
  try {
    const response = await apiCall("/resources/action", {
      method: "POST",
      body: JSON.stringify({
        ids: [contextMenuItem.id],
        action: "RENAME",
        name: newName,
      }),
    });
    if (!response.ok) throw new Error("Rename failed");
    closeModal("renameModal");
    await loadFiles();
    safeToast("Item renamed successfully.", "success");
  } catch (error) {
    safeToast("Failed to rename: " + error.message, "error");
  }
}

// Expose globally
window.togglePasswordField = togglePasswordField;
window.resetFolderModal = resetFolderModal;
window.showCreateFolderModal = showCreateFolderModal;
window.createFolder = createFolder;
window.showRenameModal = showRenameModal;
window.executeRename = executeRename;
