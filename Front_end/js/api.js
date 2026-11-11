(() => {
    "use strict";

    async function request(path, options = {}) {
        const headers = new Headers(options.headers || {});
        if (options.body && !headers.has("Content-Type")) {
            headers.set("Content-Type", "application/json");
        }
        headers.set("Authorization", `Bearer ${window.iotAuth.token()}`);
        let response;
        try {
            response = await fetch(path, { ...options, headers, credentials: "omit" });
        } catch {
            throw new Error("Không kết nối được backend. Hãy kiểm tra máy chủ đang chạy.");
        }
        if (response.status === 401) {
            window.iotAuth.signOut();
            throw new Error("Phiên đăng nhập đã hết hạn.");
        }
        if (!response.ok) {
            if (response.status === 403) {
                throw new Error("Tài khoản không có quyền thực hiện yêu cầu.");
            }
            throw new Error(response.status === 409 ? "ESP32 hoặc broker chưa sẵn sàng, hoặc đèn đang có lệnh chờ." :
                `Yêu cầu thất bại (HTTP ${response.status}).`);
        }
        return response.status === 204 ? null : response.json();
    }

    window.iotApi = {
        get(path) { return request(path); },
        post(path, body) { return request(path, { method: "POST", body: JSON.stringify(body) }); }
    };
})();
