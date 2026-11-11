document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("loginForm");
    const error = document.getElementById("loginError");
    const button = form.querySelector('button[type="submit"]');
    form.addEventListener("submit", async event => {
        event.preventDefault();
        button.disabled = true;
        error.hidden = true;
        try {
            const response = await fetch("/api/auth/login", {
                method: "POST", credentials: "omit",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ username: form.username.value.trim(), password: form.password.value })
            });
            if (!response.ok) throw new Error(response.status === 401 ?
                "Tên đăng nhập hoặc mật khẩu không đúng." : `Không đăng nhập được (HTTP ${response.status}).`);
            window.iotAuth.save(await response.json());
            location.href = "/index.html";
        } catch (cause) {
            error.textContent = cause.message || "Không kết nối được backend.";
            error.hidden = false;
            button.disabled = false;
        }
    });
});
