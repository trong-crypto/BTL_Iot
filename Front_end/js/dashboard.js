let realtimeChart = null;
let selectedSensorIndex = null;

function createDashboardChart() {
    const canvas = document.getElementById("realtimeChart");
    if (!canvas || typeof Chart === "undefined") return;
    const colors = ["#f97316", "#06b6d4", "#eab308"];
    realtimeChart = new Chart(canvas, {
        type: "line",
        data: {
            labels: [],
            datasets: ["Nhiệt độ", "Độ ẩm", "Ánh sáng"].map((label, index) => ({
                label,
                data: [],
                borderColor: colors[index],
                backgroundColor: colors[index],
                borderWidth: 2,
                pointRadius: 2,
                tension: 0.25,
                spanGaps: true,
                yAxisID: index === 2 ? "light" : "weather"
            }))
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            interaction: { mode: "index", intersect: false },
            plugins: {
                legend: { display: false },
                tooltip: { callbacks: {
                    label(context) {
                        const unit = context.datasetIndex === 0 ? " °C" : context.datasetIndex === 1 ? " %" : " ADC";
                        return `${context.dataset.label}: ${context.parsed.y}${unit}`;
                    }
                }}
            },
            scales: {
                weather: { position: "left", min: 0, max: 100, title: { display: true, text: "°C / %" } },
                light: { position: "right", min: 0, max: 4095, grid: { drawOnChartArea: false },
                    title: { display: true, text: "Ánh sáng (ADC)" } },
                x: { ticks: { maxTicksLimit: 6 } }
            }
        }
    });
}

function updateChartLegendState() {
    document.querySelectorAll(".chart-legend .legend-item").forEach(button => {
        const index = Number(button.dataset.datasetIndex);
        const selected = selectedSensorIndex === index;
        button.classList.toggle("is-active", selected);
        button.classList.toggle("is-muted", selectedSensorIndex !== null && !selected);
        button.setAttribute("aria-pressed", String(selected));
    });
}

function initializeChartLegend() {
    document.querySelectorAll(".chart-legend .legend-item").forEach(button => {
        button.addEventListener("click", () => {
            if (!realtimeChart) return;
            const index = Number(button.dataset.datasetIndex);
            selectedSensorIndex = selectedSensorIndex === index ? null : index;
            realtimeChart.data.datasets.forEach((_, i) =>
                realtimeChart.setDatasetVisibility(i, selectedSensorIndex === null || selectedSensorIndex === i));
            realtimeChart.options.scales.light.display = selectedSensorIndex === null || selectedSensorIndex === 2;
            realtimeChart.options.scales.weather.display = selectedSensorIndex !== 2;
            realtimeChart.update();
            updateChartLegendState();
        });
    });
    updateChartLegendState();
}

const deviceCache = new Map();
const chartPoints = new Map();
const commandResults = new Map();
const commandWaiters = new Map();
let socketReady = false;
let lastReadingTime = null;

function renderDevices(updates = []) {
    updates.forEach(update => {
        const old = deviceCache.get(update.deviceKey);
        const fresh = !old?.statusConfirmedAt || (update.statusConfirmedAt && update.statusConfirmedAt >= old.statusConfirmedAt);
        deviceCache.set(update.deviceKey, {
            ...old, ...update,
            id: update.deviceId ?? update.id ?? old?.id,
            status: fresh ? update.status : old?.status,
            statusConfirmedAt: fresh ? update.statusConfirmedAt : old?.statusConfirmedAt
        });
    });
    document.querySelectorAll('.device-row').forEach(row => {
        if (row.getAttribute('aria-busy') === 'true') return;
        const device = deviceCache.get(row.dataset.device);
        const isOn = device?.status === 'ON';
        row.dataset.deviceId = device?.id ?? '';
        row.querySelector('.device-status').textContent = !device ? 'Đang tải trạng thái...' :
            !socketReady ? 'Mất kết nối cập nhật' : !device.statusConfirmedAt ? 'Chưa nhận trạng thái ESP32' :
            device.online ? (isOn ? 'Đang Bật' : 'Đang Tắt') : `Mất kết nối · lần cuối ${isOn ? 'Bật' : 'Tắt'}`;
        row.querySelector('.device-icon').classList.toggle('is-on', isOn);
        row.querySelector('[data-action="on"]').classList.toggle('active', isOn);
        row.querySelector('[data-action="off"]').classList.toggle('active', Boolean(device) && !isOn);
        row.querySelectorAll('.device-button').forEach(button => {
            button.disabled = !socketReady || !device?.online || !device.statusConfirmedAt;
        });
    });
    if (socketReady) document.querySelector('.system-health span:last-child').textContent =
        [...deviceCache.values()].some(device => device.online) ? 'ESP32 đang kết nối' : 'ESP32 chưa kết nối';
}

function applyReadings(snapshot) {
    if (lastReadingTime && (!snapshot.time || snapshot.time < lastReadingTime)) return;
    if (snapshot.time) lastReadingTime = snapshot.time;
    for (const [key, id] of [['temperature', 'temperatureValue'], ['humidity', 'humidityValue'], ['light', 'lightValue']]) {
        const value = snapshot[key];
        document.getElementById(id).textContent = Number.isFinite(value) ?
            (key === 'light' ? Math.round(value) : Number(value).toFixed(1)) : '—';
    }
}

function drawChart() {
    if (!realtimeChart) return;
    const points = [...chartPoints].sort(([a], [b]) => a.localeCompare(b)).slice(-30);
    chartPoints.clear();
    points.forEach(([time, values]) => chartPoints.set(time, values));
    realtimeChart.data.labels = points.map(([time]) => time.slice(11, 19));
    ['temperature', 'humidity', 'light'].forEach((key, index) => {
        realtimeChart.data.datasets[index].data = points.map(([, values]) => values[key] ?? null);
    });
    realtimeChart.update();
}

function onSensor(snapshot) {
    applyReadings(snapshot);
    if (snapshot.time) chartPoints.set(snapshot.time, snapshot);
    drawChart();
}

async function loadDevices() {
    const devices = await window.iotApi.get('/api/devices');
    const statuses = await Promise.all(devices.map(device => window.iotApi.get(`/api/devices/${device.id}/status`)));
    renderDevices(statuses);
}

async function loadChart() {
    const data = await window.iotApi.get('/api/sensor-data/chart?limit=30');
    for (const key of ['temperature', 'humidity', 'light']) {
        for (const point of data[key]) {
            const values = chartPoints.get(point.time) || {};
            values[key] = point.value;
            chartPoints.set(point.time, values);
        }
    }
    drawChart();
}

function onCommand(result) {
    if (result.status === 'PENDING') return;
    commandResults.set(result.requestId, result.status);
    if (commandResults.size > 100) commandResults.delete(commandResults.keys().next().value);
    const waiter = commandWaiters.get(result.requestId);
    if (waiter) {
        clearTimeout(waiter.timer);
        commandWaiters.delete(result.requestId);
        waiter.resolve(result.status);
    }
}

function waitForCommand(receipt) {
    if (commandResults.has(receipt.requestId)) return Promise.resolve(commandResults.get(receipt.requestId));
    return new Promise((resolve, reject) => {
        const timer = setTimeout(async () => {
            commandWaiters.delete(receipt.requestId);
            try {
                const result = await window.iotApi.get(`/api/commands/${encodeURIComponent(receipt.requestId)}`);
                resolve(result.status);
            } catch (error) { reject(error); }
        }, (receipt.timeoutSeconds + 2) * 1000);
        commandWaiters.set(receipt.requestId, { resolve, timer });
    });
}

function initializeDeviceControls() {
    const message = document.getElementById('commandMessage');
    document.querySelectorAll('.device-row').forEach(row => {
        row.querySelectorAll('.device-button').forEach(button => {
            button.addEventListener('click', async () => {
                if (!socketReady || !row.dataset.deviceId || row.getAttribute('aria-busy') === 'true') return;
                const action = button.dataset.action.toUpperCase();
                row.setAttribute('aria-busy', 'true');
                row.querySelectorAll('.device-button').forEach(item => { item.disabled = true; });
                button.classList.add('is-loading');
                message.textContent = 'Đang gửi lệnh và chờ ESP32 xác nhận...';
                try {
                    const receipt = await window.iotApi.post(`/api/devices/${row.dataset.deviceId}/control`, {
                        deviceId: Number(row.dataset.deviceId), action
                    });
                    const status = await waitForCommand(receipt);
                    message.textContent = status === 'SUCCESS' ? 'ESP32 đã xác nhận lệnh.' :
                        status === 'TIMEOUT' ? 'Hết thời gian chờ ESP32 xác nhận.' :
                        status === 'PENDING' ? 'Chưa nhận được kết quả cuối cùng của lệnh.' : 'Lệnh không thực hiện được.';
                } catch (error) { message.textContent = error.message; }
                finally {
                    button.classList.remove('is-loading');
                    row.removeAttribute('aria-busy');
                    renderDevices();
                }
            });
        });
    });
}

async function synchronizeDashboard() {
    socketReady = true;
    const results = await Promise.allSettled([
        loadDevices(),
        window.iotApi.get('/api/sensor-data/latest').then(applyReadings),
        loadChart()
    ]);
    if (results.some(result => result.status === 'rejected'))
        document.querySelector('.system-health span:last-child').textContent = 'Chưa tải đủ dữ liệu ban đầu; hãy tải lại trang.';
    // Recover a command whose final push arrived while the connection was down.
    for (const requestId of commandWaiters.keys()) {
        try { onCommand(await window.iotApi.get(`/api/commands/${encodeURIComponent(requestId)}`)); }
        catch { /* the existing timeout performs one final reconciliation */ }
    }
}

 document.addEventListener('DOMContentLoaded', () => {
    if (window.lucide) window.lucide.createIcons({ attrs: { 'aria-hidden': 'true' } });
    createDashboardChart();
    initializeChartLegend();
    initializeDeviceControls();
    window.iotRealtime.start({
        sensor: onSensor,
        device: device => renderDevices([device]),
        command: onCommand,
        availability: ({ online }) => {
            deviceCache.forEach(device => { device.online = online; });
            renderDevices();
        },
        connected: synchronizeDashboard,
        disconnected: message => {
            socketReady = false;
            renderDevices();
            document.querySelector('.system-health span:last-child').textContent = message;
        }
    });
});
