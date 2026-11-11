package org.example.back_end_iot.mqtt;

import org.example.back_end_iot.entity.ActionHistory;
import org.example.back_end_iot.entity.Device;
import org.example.back_end_iot.entity.Sensor;
import org.example.back_end_iot.entity.SensorData;
import org.example.back_end_iot.enums.ActionStatus;
import org.example.back_end_iot.enums.DeviceStatus;
import org.example.back_end_iot.repository.ActionHistoryRepository;
import org.example.back_end_iot.repository.DeviceRepository;
import org.example.back_end_iot.repository.SensorDataRepository;
import org.example.back_end_iot.repository.SensorRepository;
import org.example.back_end_iot.realtime.RealtimeEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MqttEventHandler {
    private static final Logger log = LoggerFactory.getLogger(MqttEventHandler.class);
    private static final String SENSOR_TOPIC = "iot/device01/sensor";
    private static final String ACK_TOPIC = "iot/device01/ack";
    private static final String STATUS_PREFIX = "iot/device01/status/";
    private static final String AVAILABILITY_TOPIC = "iot/device01/availability";
    private static final List<String> SENSOR_KEYS = List.of("temperature", "humidity", "light");

    private final JsonMapper mapper;
    private final SensorRepository sensorRepository;
    private final SensorDataRepository sensorDataRepository;
    private final DeviceRepository deviceRepository;
    private final ActionHistoryRepository actionHistoryRepository;
    private volatile boolean deviceOnline;
    private final RealtimeEvents realtime;

    public MqttEventHandler(JsonMapper mapper, SensorRepository sensorRepository,
                            SensorDataRepository sensorDataRepository, DeviceRepository deviceRepository,
                            ActionHistoryRepository actionHistoryRepository, RealtimeEvents realtime) {
        this.mapper = mapper;
        this.sensorRepository = sensorRepository;
        this.sensorDataRepository = sensorDataRepository;
        this.deviceRepository = deviceRepository;
        this.actionHistoryRepository = actionHistoryRepository;
        this.realtime = realtime;
    }

    @Transactional
    public void handle(String topic, String payload, boolean retained) {
        if (SENSOR_TOPIC.equals(topic)) {
            // Older firmware retained sensor readings; never save one again on subscribe.
            if (!retained) handleSensor(payload);
        } else if (ACK_TOPIC.equals(topic)) {
            if (!retained) handleAck(payload);
        } else if (topic.startsWith(STATUS_PREFIX)) {
            handleStatus(topic.substring(STATUS_PREFIX.length()), payload);
        } else if (AVAILABILITY_TOPIC.equals(topic)) {
            deviceOnline = "ONLINE".equals(payload);
            realtime.publish("/topic/availability", java.util.Map.of("online", deviceOnline));
        }
    }

    public boolean isDeviceOnline() {
        return deviceOnline;
    }

    public void markOffline() {
        deviceOnline = false;
        realtime.publish("/topic/availability", java.util.Map.of("online", false));
    }

    private void handleSensor(String payload) {
        JsonNode root = mapper.readTree(payload);
        if (root == null || !root.isObject()) return;
        List<SensorData> readings = new ArrayList<>(3);
        LocalDateTime receivedAt = LocalDateTime.now();
        for (String key : SENSOR_KEYS) {
            JsonNode node = root.get(key);
            if (node == null || !node.isNumber() || !Double.isFinite(node.doubleValue())) {
                log.warn("Bỏ qua bản tin cảm biến thiếu hoặc sai {}", key);
                return;
            }
            double value = node.doubleValue();
            if (("humidity".equals(key) && (value < 0 || value > 100))
                    || ("light".equals(key) && (value < 0 || value > 4095))) {
                log.warn("Bỏ qua giá trị cảm biến ngoài phạm vi: {}", key);
                return;
            }
            Optional<Sensor> sensor = sensorRepository.findBySensorKey(key);
            if (sensor.isEmpty()) {
                log.warn("Chưa khai báo cảm biến {} trong database", key);
                return;
            }
            SensorData reading = new SensorData();
            reading.setSensor(sensor.get());
            reading.setValue(value);
            reading.setCreatedAt(receivedAt);
            readings.add(reading);
        }
        sensorDataRepository.saveAll(readings);
        realtime.publish("/topic/sensor-data", new RealtimeEvents.SensorSnapshot(
                readings.get(0).getValue(), readings.get(1).getValue(), readings.get(2).getValue(), receivedAt));
    }

    private void handleStatus(String topicKey, String payload) {
        JsonNode root = mapper.readTree(payload);
        if (root == null || !root.isObject()) return;
        String payloadKey = text(root, "deviceKey");
        DeviceStatus status = deviceStatus(text(root, "state"));
        if (!topicKey.equals(payloadKey) || status == null) {
            log.warn("Bỏ qua trạng thái đèn không khớp topic hoặc không hợp lệ: {}", topicKey);
            return;
        }
        Optional<Device> device = deviceRepository.findByDeviceKey(topicKey);
        if (device.isEmpty()) return;
        device.get().setStatus(status);
        device.get().setStatusConfirmedAt(LocalDateTime.now());
        realtime.device(device.get(), deviceOnline);
    }

    private void handleAck(String payload) {
        JsonNode root = mapper.readTree(payload);
        if (root == null || !root.isObject()) return;
        String requestId = text(root, "requestId");
        String deviceKey = text(root, "deviceKey");
        String actionText = text(root, "action");
        String result = text(root, "result");
        DeviceStatus state = deviceStatus(text(root, "state"));
        if (requestId == null || deviceKey == null || actionText == null || result == null) return;

        Optional<ActionHistory> existing = actionHistoryRepository.findByRequestIdForUpdate(requestId);
        if (existing.isEmpty()) return;
        ActionHistory history = existing.get();
        if (!deviceKey.equals(history.getDevice().getDeviceKey())
                || !actionText.equals(history.getAction().name())
                || history.getStatus() != ActionStatus.PENDING) return;

        if (history.getCreatedAt().plusSeconds(5).isBefore(LocalDateTime.now())) {
            history.setStatus(ActionStatus.TIMEOUT);
            realtime.command(history);
            return;
        }
        if ("FAILED".equals(result)) {
            history.setStatus(ActionStatus.FAILED);
        } else if ("SUCCESS".equals(result) && state != null
                && state.name().equals(history.getAction().name())) {
            history.setStatus(ActionStatus.SUCCESS);
            Device device = history.getDevice();
            device.setStatus(state);
            device.setStatusConfirmedAt(LocalDateTime.now());
            realtime.device(device, deviceOnline);
        } else {
            log.warn("Bỏ qua xác nhận lệnh có kết quả không hợp lệ: {}", requestId);
            return;
        }
        realtime.command(history);
    }

    private static String text(JsonNode root, String name) {
        JsonNode value = root.get(name);
        return value != null && value.isTextual() ? value.stringValue() : null;
    }

    private static DeviceStatus deviceStatus(String value) {
        if ("ON".equals(value)) return DeviceStatus.ON;
        if ("OFF".equals(value)) return DeviceStatus.OFF;
        return null;
    }
}
