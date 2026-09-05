// toast.js – Guaranteed working version with icons
(function () {
  let container = document.getElementById("toast-container");
  if (!container) {
    container = document.createElement("div");
    container.id = "toast-container";
    document.body.appendChild(container);
  }

  const ICONS = {
    success: "✅",
    warning: "⚠️",
    error: "❌",
    info: "ℹ️",
  };

  const COLORS = {
    success: "#0d9488",
    warning: "#f59e0b",
    error: "#d93025",
    info: "#1a73e8",
  };

  window.showToast = function (message, type = "info", duration = 3500) {
    const icon = ICONS[type] || "ℹ️";
    const color = COLORS[type] || "#1a73e8";

    const toast = document.createElement("div");
    toast.style.cssText = `
            background: white;
            padding: 14px 18px;
            border-radius: 10px;
            box-shadow: 0 6px 20px rgba(0,0,0,0.12);
            display: flex;
            align-items: center;
            gap: 12px;
            border-left: 5px solid ${color};
            margin-bottom: 10px;
            font-family: 'Segoe UI', sans-serif;
            font-size: 14px;
            font-weight: 600;
            color: #202124;
            transform: translateX(120%);
            opacity: 0;
            transition: all 0.35s ease;
        `;

    toast.innerHTML = `
            <span style="font-size:1.3rem; color:${color};">${icon}</span>
            <span style="flex:1;">${message}</span>
            <button style="background:none;border:none;font-size:1.5rem;cursor:pointer;color:#999;padding:0 4px;">&times;</button>
        `;

    container.appendChild(toast);

    requestAnimationFrame(() => {
      toast.style.transform = "translateX(0)";
      toast.style.opacity = "1";
    });

    const closeBtn = toast.querySelector("button");
    closeBtn.addEventListener("click", () => {
      toast.style.transform = "translateX(120%)";
      toast.style.opacity = "0";
      setTimeout(() => toast.remove(), 400);
    });

    if (duration > 0) {
      setTimeout(() => {
        toast.style.transform = "translateX(120%)";
        toast.style.opacity = "0";
        setTimeout(() => toast.remove(), 400);
      }, duration);
    }
  };
})();
