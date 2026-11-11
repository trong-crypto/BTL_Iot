document.addEventListener("DOMContentLoaded", () => {
    window.iotAuth.requireLogin();
    highlightCurrentNav();
    const button = document.createElement("button");
    button.type = "button";
    button.className = "btn";
    button.textContent = "Đăng xuất";
    button.addEventListener("click", () => window.iotAuth.signOut());
    document.querySelector(".topbar")?.append(button);
});

function highlightCurrentNav() {
    const currentPage = window.location.pathname.split("/").pop() || "index.html";

    document.querySelectorAll(".sidebar-nav a").forEach(link => {
        const href = new URL(link.getAttribute("href"), window.location.href).pathname.split("/").pop();
        const isCurrentPage = href === currentPage;
        link.classList.toggle("active", isCurrentPage);
        if (isCurrentPage) link.setAttribute("aria-current", "page");
        else link.removeAttribute("aria-current");
    });
}
