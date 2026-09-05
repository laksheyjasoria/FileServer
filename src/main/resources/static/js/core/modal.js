// modal.js – Global Alert & Confirm Modal (fixed)

(function () {
  let overlay = document.getElementById("modal-overlay");
  if (!overlay) {
    overlay = document.createElement("div");
    overlay.id = "modal-overlay";
    overlay.className = "modal-overlay";
    document.body.appendChild(overlay);
  }

  if (!overlay.querySelector(".modal-box")) {
    overlay.innerHTML = `
            <div class="modal-box">
                <div class="modal-title" id="modalTitle">Alert</div>
                <div class="modal-message" id="modalMessage">Message</div>
                <div class="modal-actions" id="modalActions">
                    <button class="modal-btn modal-btn-primary" id="modalConfirmBtn">OK</button>
                </div>
            </div>
        `;
  }

  const titleEl = document.getElementById("modalTitle");
  const messageEl = document.getElementById("modalMessage");
  const actionsEl = document.getElementById("modalActions");

  let resolvePromise = null;
  let isResolved = false;

  function hideModal() {
    overlay.classList.remove("active");
    overlay.style.display = "none";
    // Only resolve false if not already resolved
    if (resolvePromise && !isResolved) {
      resolvePromise(false);
      resolvePromise = null;
    }
  }

  function showModal() {
    isResolved = false;
    overlay.style.display = "flex";
    overlay.classList.add("active");
  }

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && overlay.classList.contains("active")) {
      hideModal();
    }
  });

  window.showAlert = function (message, title = "Alert") {
    return new Promise((resolve) => {
      titleEl.textContent = title;
      messageEl.textContent = message;
      actionsEl.innerHTML = `
                <button class="modal-btn modal-btn-primary" id="modalConfirmBtn">OK</button>
            `;
      const btn = document.getElementById("modalConfirmBtn");
      btn.addEventListener("click", () => {
        if (!isResolved) {
          isResolved = true;
          hideModal();
          resolve(true);
        }
      });
      showModal();
      setTimeout(() => btn.focus(), 100);
      resolvePromise = resolve;
    });
  };

  window.showConfirm = function (
    message,
    title = "Confirm",
    confirmText = "Confirm",
    cancelText = "Cancel",
    confirmType = "primary",
  ) {
    return new Promise((resolve) => {
      titleEl.textContent = title;
      messageEl.textContent = message;
      const btnClass =
        confirmType === "danger" ? "modal-btn-danger" : "modal-btn-primary";
      actionsEl.innerHTML = `
                <button class="modal-btn modal-btn-secondary" id="modalCancelBtn">${cancelText}</button>
                <button class="modal-btn ${btnClass}" id="modalConfirmBtn">${confirmText}</button>
            `;
      const confirmBtn = document.getElementById("modalConfirmBtn");
      const cancelBtn = document.getElementById("modalCancelBtn");
      confirmBtn.addEventListener("click", () => {
        if (!isResolved) {
          isResolved = true;
          hideModal();
          resolve(true);
        }
      });
      cancelBtn.addEventListener("click", () => {
        if (!isResolved) {
          isResolved = true;
          hideModal();
          resolve(false);
        }
      });
      showModal();
      setTimeout(() => confirmBtn.focus(), 100);
      resolvePromise = resolve;
    });
  };

  window.hideModal = hideModal;
})();
