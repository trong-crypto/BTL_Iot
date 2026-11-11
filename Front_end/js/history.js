(() => {
    "use strict";

    const state = {
        page: 1,
        pageSize: window.matchMedia("(max-width: 700px), (max-height: 700px)").matches ? 5 : 10,
        ascending: false,
        filters: {}
    };

    function showError(message) {
        const error = document.getElementById("historyFilterError");
        error.textContent = message;
        error.hidden = false;
    }

    function renderRows(items) {
        const tbody = document.getElementById("historyTableBody");
        tbody.replaceChildren();
        const labels = { SUCCESS: "Thành công", FAILED: "Thất bại", PENDING: "Đang chờ" };
        for (const item of items) {
            const row = document.createElement("tr");
            for (const text of [item.id, item.username, item.deviceName]) {
                const cell = document.createElement("td");
                cell.textContent = text;
                row.append(cell);
            }
            const actionCell = document.createElement("td");
            const action = document.createElement("span");
            action.className = `history-action${item.action === "OFF" ? " is-off" : ""}`;
            action.textContent = item.action === "ON" ? "Bật" : "Tắt";
            actionCell.append(action);
            const statusCell = document.createElement("td");
            const status = document.createElement("span");
            status.className = `history-status${item.status === "FAILED" ? " is-failure" : ""}`;
            status.textContent = labels[item.status] || item.status;
            statusCell.append(status);
            const timeCell = window.createTimestampCell(item.createdAt, `lệnh ${item.id}`);
            row.append(actionCell, statusCell, timeCell);
            tbody.append(row);
        }
        document.getElementById("historyEmptyState").hidden = items.length > 0;
    }

    async function loadHistory() {
        const params = new URLSearchParams({
            page: String(state.page - 1),
            size: String(state.pageSize),
            sort: `createdAt,${state.ascending ? "asc" : "desc"}`,
            ...state.filters
        });
        try {
            const result = await window.iotApi.get(`/api/devices/action-history?${params}`);
            renderRows(result.content);
            const start = result.totalElements ? (state.page - 1) * state.pageSize + 1 : 0;
            const end = result.content.length ? start + result.content.length - 1 : 0;
            document.getElementById("historyResultSummary").textContent =
                `Hiển thị ${start}-${end} của ${result.totalElements.toLocaleString("vi-VN")} kết quả`;
            window.renderTablePagination(document.getElementById("historyPagination"), {
                page: state.page,
                pageSize: state.pageSize,
                total: result.totalElements,
                onPageChange(page) { state.page = page; loadHistory(); },
                onPageSizeChange(size) { state.pageSize = size; state.page = 1; loadHistory(); }
            });
        } catch (error) {
            showError(error.message);
            document.getElementById("historyResultSummary").textContent = "Không tải được lịch sử.";
        }
    }

    function applyFilters(event) {
        event.preventDefault();
        const filters = {};
        const device = document.getElementById("deviceId").value;
        const action = document.getElementById("actionFilter").value;
        const status = document.getElementById("statusFilter").value;
        const input = document.getElementById("fromTime");
        const time = input.value.trim();
        if (device !== "all") filters.deviceKey = device;
        if (action !== "all") filters.action = action.toUpperCase();
        if (status !== "all") filters.status = status;
        if (time) {
            const range = window.parseTimeFilter(time);
            if (!range) {
                showError("Nhập YYYY-MM-DD hoặc YYYY-MM-DD HH:mm:ss hợp lệ.");
                input.setAttribute("aria-invalid", "true");
                return;
            }
            Object.assign(filters, range);
        }
        input.removeAttribute("aria-invalid");
        document.getElementById("historyFilterError").hidden = true;
        state.filters = filters;
        state.page = 1;
        loadHistory();
    }

    document.addEventListener("DOMContentLoaded", () => {
        document.getElementById("historyFilterForm").addEventListener("submit", applyFilters);
        document.getElementById("fromTime").addEventListener("input", () => {
            document.getElementById("historyFilterError").hidden = true;
        });
        document.getElementById("sortHistory").addEventListener("click", event => {
            state.ascending = !state.ascending;
            state.page = 1;
            event.currentTarget.setAttribute("aria-pressed", String(state.ascending));
            loadHistory();
        });
        loadHistory();
    });
})();
