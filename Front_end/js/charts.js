const sensorState = {
    page: 1,
    pageSize: window.matchMedia("(max-width: 700px), (max-height: 700px)").matches ? 5 : 10,
    ascending: false,
    total: 0,
    filters: {}
};

function updateSearchField() {
    const isTime = document.getElementById("sensorType").value === "time";
    const input = document.getElementById("sensorSearchValue");
    input.placeholder = isTime ? "YYYY-MM-DD HH:mm:ss" : "Nhập giá trị cần tìm...";
    input.title = isTime ? "Chỉ tìm trong ngày, phút hoặc giây đã nhập. Có thể dán thời gian vừa sao chép hoặc nhập DD/MM/YYYY - HH:mm:ss." : "";
    input.inputMode = isTime ? "text" : "decimal";
    input.setAttribute("aria-label", isTime ? "Thời gian tìm kiếm" : "Giá trị tìm kiếm");
    document.getElementById("sensorFilterError").hidden = true;
}

function showSensorError(message) {
    const error = document.getElementById("sensorFilterError");
    error.textContent = message;
    error.hidden = false;
}

function renderSensorRows(items) {
    const body = document.getElementById("sensorTableBody");
    body.replaceChildren();
    for (const item of items) {
        const row = document.createElement("tr");
        const id = document.createElement("td");
        id.textContent = item.id;
        const name = document.createElement("td");
        name.textContent = item.name;
        const value = document.createElement("td");
        value.className = "sensor-value-cell value-column";
        const strong = document.createElement("strong");
        strong.textContent = item.value;
        const unit = document.createElement("small");
        unit.textContent = ` ${item.unit || ""}`;
        value.append(strong, unit);
        const time = window.createTimestampCell(item.createdAt, `bản ghi ${item.id}`);
        row.append(id, name, value, time);
        body.append(row);
    }
    document.getElementById("emptyState").hidden = items.length > 0;
}

async function loadSensorPage() {
    const params = new URLSearchParams({
        page: String(sensorState.page - 1),
        size: String(sensorState.pageSize),
        sort: `createdAt,${sensorState.ascending ? "asc" : "desc"}`,
        ...sensorState.filters
    });
    try {
        const result = await window.iotApi.get(`/api/sensor-data/history?${params}`);
        sensorState.total = result.totalElements;
        renderSensorRows(result.content);
        document.getElementById("recordCount").textContent = `${result.totalElements.toLocaleString("vi-VN")} bản ghi`;
        const start = result.totalElements ? (sensorState.page - 1) * sensorState.pageSize + 1 : 0;
        const end = Math.min(start + result.content.length - (result.content.length ? 1 : 0), result.totalElements);
        document.getElementById("resultSummary").textContent =
            `Hiển thị ${start} - ${end} của ${result.totalElements.toLocaleString("vi-VN")} kết quả`;
        window.renderTablePagination(document.getElementById("pagination"), {
            page: sensorState.page,
            pageSize: sensorState.pageSize,
            total: result.totalElements,
            onPageChange(page) { sensorState.page = page; loadSensorPage(); },
            onPageSizeChange(size) { sensorState.pageSize = size; sensorState.page = 1; loadSensorPage(); }
        });
    } catch (error) {
        showSensorError(error.message);
        document.getElementById("resultSummary").textContent = "Không tải được dữ liệu cảm biến.";
    }
}

function applySensorFilters(event) {
    event.preventDefault();
    const type = document.getElementById("sensorType").value;
    const query = document.getElementById("sensorSearchValue").value.trim();
    const input = document.getElementById("sensorSearchValue");
    const filters = {};
    if (type === "time" && query) {
        const range = window.parseTimeFilter(query);
        if (!range) {
            showSensorError("Nhập YYYY-MM-DD hoặc YYYY-MM-DD HH:mm:ss (cũng hỗ trợ DD/MM/YYYY - HH:mm:ss).");
            input.setAttribute("aria-invalid", "true");
            return;
        }
        Object.assign(filters, range);
    } else if (type !== "all" && type !== "time") {
        filters.sensorKey = type;
        if (query) {
            const value = Number(query.replace(",", "."));
            if (!Number.isFinite(value)) {
                showSensorError("Nhập một giá trị số hợp lệ, ví dụ 26.5.");
                input.setAttribute("aria-invalid", "true");
                return;
            }
            filters.value = String(value);
        }
    }
    input.removeAttribute("aria-invalid");
    document.getElementById("sensorFilterError").hidden = true;
    sensorState.filters = filters;
    sensorState.page = 1;
    loadSensorPage();
}

document.addEventListener("DOMContentLoaded", () => {
    updateSearchField();
    document.getElementById("sensorType").addEventListener("change", updateSearchField);
    document.getElementById("sensorSearchValue").addEventListener("input", () => {
        document.getElementById("sensorFilterError").hidden = true;
    });
    document.getElementById("sensorFilterForm").addEventListener("submit", applySensorFilters);
    for (const id of ["sortByTime", "sensorSortButton"]) {
        document.getElementById(id).addEventListener("click", () => {
            sensorState.ascending = !sensorState.ascending;
            sensorState.page = 1;
            document.getElementById("sensorSortButton").setAttribute("aria-pressed", String(sensorState.ascending));
            loadSensorPage();
        });
    }
    if (window.lucide) window.lucide.createIcons({ attrs: { "aria-hidden": "true" } });
    loadSensorPage();
});
