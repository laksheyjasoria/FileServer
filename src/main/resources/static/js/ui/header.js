// header.js – Dynamic header with MutationObserver

console.log("🔥 header.js started");

(function () {
  console.log("📌 header.js IIFE running");

  const PLACEHOLDERS = {
    "/index.html": "Search files and folders...",
    "/shared.html": "Search shared items...",
    "/shared-by-me.html": "Search shares...",
    "/master.html": "Search master drives...",
    "/logger.html": "Search loggers...",
    "/admin-users.html": "Search users...",
    "/trash.html": "Search deleted items...",
    "/profile.html": "", // hidden
  };

  function getSearchPlaceholder() {
    const path = window.location.pathname.replace(/\/$/, "");
    return PLACEHOLDERS[path] || PLACEHOLDERS[window.location.pathname] || "";
  }

  function setInitials(user) {
    const avatarEl = document.getElementById("userAvatar");
    if (!avatarEl) return;
    avatarEl.style.backgroundImage = "none";
    avatarEl.style.backgroundColor = "#e2e8f0";
    avatarEl.style.display = "flex";
    avatarEl.style.alignItems = "center";
    avatarEl.style.justifyContent = "center";
    avatarEl.style.fontSize = "20px";
    avatarEl.style.fontWeight = "600";
    avatarEl.style.color = "#4a5568";
    avatarEl.style.width = "40px";
    avatarEl.style.height = "40px";
    avatarEl.style.borderRadius = "50%";
    const name = user?.name || user?.email || "U";
    avatarEl.textContent = name.charAt(0).toUpperCase();
  }

  function applyAvatar(dataUrl) {
    const avatarEl = document.getElementById("userAvatar");
    if (!avatarEl) {
      console.warn("⚠️ avatarEl not found");
      return;
    }

    // Ensure size and shape
    avatarEl.style.width = "40px";
    avatarEl.style.height = "40px";
    avatarEl.style.borderRadius = "50%";
    avatarEl.style.display = "flex";
    avatarEl.style.alignItems = "center";
    avatarEl.style.justifyContent = "center";
    avatarEl.style.textContent = "";

    if (dataUrl && typeof dataUrl === "string" && dataUrl.startsWith("data:") && dataUrl.length > 1000) {
      console.log("🖼️ Applying Data URL (length:", dataUrl.length, ")");
      avatarEl.style.backgroundImage = `url(${dataUrl})`;
      avatarEl.style.backgroundSize = "cover";
      avatarEl.style.backgroundColor = "transparent";
    } else {
      console.warn("⚠️ Invalid Data URL, falling back to initials");
      const user = window.getUserData?.() || window.getUserFromToken?.() || null;
      setInitials(user);
    }
  }

  function tryFetchAvatar() {
    if (typeof window.fetchAndCacheAvatar === "function") {
      console.log("📡 Fetching avatar in background...");
      window.fetchAndCacheAvatar().then((dataUrl) => {
        if (dataUrl) {
          console.log("🖼️ Avatar fetched, applying");
          applyAvatar(dataUrl);
        }
      });
    } else {
      console.warn("⚠️ fetchAndCacheAvatar not defined – retrying...");
      setTimeout(tryFetchAvatar, 200);
    }
  }

  let avatarObserver = null;

  function observeAvatar() {
    const avatarEl = document.getElementById("userAvatar");
    if (!avatarEl) return;

    // Disconnect existing observer
    if (avatarObserver) avatarObserver.disconnect();

    // Watch for attribute changes (style, class) or element removal
    avatarObserver = new MutationObserver(() => {
      const cached = window.getCachedAvatarDataUrl?.() || null;
      if (cached) {
        console.log("🔄 Avatar element changed – re‑applying cache");
        applyAvatar(cached);
      }
    });

    avatarObserver.observe(avatarEl, {
      attributes: true,
      attributeFilter: ["style", "class"],
      childList: true,
      subtree: false,
    });
  }

  function renderHeader() {
    console.log("📌 renderHeader called");
    const placeholder = document.getElementById("header-container");
    if (!placeholder) {
      console.warn("⚠️ No header-container, creating one...");
      const newPlaceholder = document.createElement("div");
      newPlaceholder.id = "header-container";
      document.body.prepend(newPlaceholder);
      setTimeout(renderHeader, 50);
      return;
    }

    // Build header HTML
    placeholder.innerHTML = `
      <header class="header">
        <div class="logo">
          <span class="logo-icon">📁</span>
          <span class="logo-text">File Server</span>
        </div>
        <div class="search-bar">
          <input type="text" id="searchInput" placeholder="" />
        </div>
        <div class="user-menu">
          <span id="userEmail" class="user-email"></span>
          <div class="user-avatar" id="userAvatar"></div>
        </div>
      </header>
    `;
    console.log("✅ Header HTML injected");

    // Search placeholder
    const searchInput = document.getElementById("searchInput");
    if (searchInput) {
      const text = getSearchPlaceholder();
      if (text) {
        searchInput.placeholder = text;
        searchInput.style.display = "block";
      } else {
        searchInput.style.display = "none";
      }
    }

    // Set email
    const user = window.getUserData?.() || window.getUserFromToken?.() || null;
    const emailEl = document.getElementById("userEmail");
    if (user && user.email) {
      emailEl.textContent = user.email;
      console.log("📧 Email set:", user.email);
    } else {
      emailEl.textContent = "";
    }

    // ---- Start observing avatar element ----
    observeAvatar();

    // ---- Check cache ----
    const cached = window.getCachedAvatarDataUrl?.() || null;
    if (cached) {
      console.log("🖼️ Found cached avatar Data URL");
      applyAvatar(cached);
      return;
    }

    console.log("🖼️ No cache – showing initials, fetching...");
    setInitials(user);
    tryFetchAvatar();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", renderHeader);
  } else {
    renderHeader();
  }

  window.renderHeader = renderHeader;
})();