(() => {
    "use strict";
    const key = "iot.auth";
    let expiryTimer;

    function clear() {
        sessionStorage.removeItem(key);
        clearTimeout(expiryTimer);
        window.dispatchEvent(new Event("iot:logout"));
    }

    function signOut() {
        clear();
        location.replace("/login.html");
    }

    function session() {
        try {
            const value = JSON.parse(sessionStorage.getItem(key));
            if (value?.accessToken && Date.parse(value.expiresAt) > Date.now()) return value;
        } catch { /* Discard incomplete or expired browser state. */ }
        return null;
    }

    function schedule() {
        clearTimeout(expiryTimer);
        const value = session();
        if (value) expiryTimer = setTimeout(signOut, Math.max(0, Date.parse(value.expiresAt) - Date.now()));
    }

    window.iotAuth = {
        save(value) {
            if (!value.accessToken || !Number.isFinite(Date.parse(value.expiresAt)))
                throw new Error("Phản hồi đăng nhập thiếu JWT hoặc thời hạn token.");
            sessionStorage.setItem(key, JSON.stringify({ accessToken: value.accessToken, expiresAt: value.expiresAt }));
            schedule();
        },
        token() {
            const value = session();
            if (!value) {
                signOut();
                throw new Error("Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.");
            }
            return value.accessToken;
        },
        signOut,
        requireLogin() { this.token(); schedule(); }
    };
    window.addEventListener("pageshow", () => {
        if (location.pathname.endsWith("login.html")) return;
        if (!session()) signOut();
        else schedule();
    });
})();
