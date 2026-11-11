package org.example.back_end_iot.mqtt;

import org.example.back_end_iot.entity.ActionHistory;
import org.example.back_end_iot.entity.Device;
import org.example.back_end_iot.entity.Sensor;
import org.example.back_end_iot.entity.SensorData;
import org.example.back_end_iot.enums.ActionStatus;
import org.example.back_end_iot.enums.DeviceAction;
import org.example.back_end_iot.repository.ActionHistoryRepository;
import org.example.back_end_iot.repository.DeviceRepository;
import org.example.back_end_iot.repository.SensorDataRepository;
import org.example.back_end_iot.repository.SensorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqttEventHandlerTests {
    private SensorRepository sensors;
    private SensorDataRepository readings;
    private DeviceRepository devices;
    private ActionHistoryRepository actions;
    private MqttEventHandler handler;

    @BeforeEach
    void setUp() {
        sensors = mock(SensorRepository.class);
        readings = mock(SensorDataRepository.class);
        devices = mock(DeviceRepository.class);
        actions = mock(ActionHistoryRepository.class);
        handler = new MqttEventHandler(JsonMapper.builder().build(), sensors, readings, devices, actions,
                mock(org.example.back_end_iot.realtime.RealtimeEvents.class));
    }

    @Test
    void savesThreeReadingsWithOneTimestamp() {
        for (String key : List.of("temperature", "humidity", "light")) {
            when(sensors.findBySensorKey(key)).thenReturn(Optional.of(new Sensor()));
        }

        handler.handle("iot/device01/sensor",
                "{\"temperature\":28.5,\"humidity\":70.2,\"light\":1530}", false);

        verify(readings).saveAll(org.mockito.ArgumentMatchers.argThat(rows -> {
            List<SensorData> saved = (List<SensorData>) rows;
            return saved.size() == 3
                    && saved.get(0).getValue() == 28.5
                    && saved.get(1).getValue() == 70.2
                    && saved.get(2).getValue() == 1530.0
                    && saved.get(0).getCreatedAt().equals(saved.get(1).getCreatedAt())
                    && saved.get(1).getCreatedAt().equals(saved.get(2).getCreatedAt());
        }));
    }

    @Test
    void ignoresRetainedOrIncompleteSensorReadings() {
        for (String key : List.of("temperature", "humidity", "light")) {
            when(sensors.findBySensorKey(key)).thenReturn(Optional.of(new Sensor()));
        }
        handler.handle("iot/device01/sensor",
                "{\"temperature\":28,\"humidity\":70,\"light\":1000}", true);
        handler.handle("iot/device01/sensor",
                "{\"temperature\":28,\"humidity\":70}", false);
        verify(readings, never()).saveAll(any());
    }

    @Test
    void onlyMatchingTimelyAckCompletesCommand() throws Exception {
        Device device = new Device();
        device.setDeviceKey("lamp1");
        ActionHistory history = pending(device, LocalDateTime.now());
        when(actions.findByRequestIdForUpdate("550e8400-e29b-41d4-a716-446655440000"))
                .thenReturn(Optional.of(history));

        handler.handle("iot/device01/ack", ack("lamp2", "SUCCESS"), false);
        assertEquals(ActionStatus.PENDING, history.getStatus());
        assertNull(device.getStatusConfirmedAt());

        handler.handle("iot/device01/ack", ack("lamp1", "SUCCESS"), false);
        assertEquals(ActionStatus.SUCCESS, history.getStatus());
        assertSame(org.example.back_end_iot.enums.DeviceStatus.ON, device.getStatus());
    }

    @Test
    void lateAckLeavesCommandTimedOut() throws Exception {
        Device device = new Device();
        device.setDeviceKey("lamp1");
        ActionHistory history = pending(device, LocalDateTime.now().minusSeconds(6));
        when(actions.findByRequestIdForUpdate("550e8400-e29b-41d4-a716-446655440000"))
                .thenReturn(Optional.of(history));

        handler.handle("iot/device01/ack", ack("lamp1", "SUCCESS"), false);

        assertEquals(ActionStatus.TIMEOUT, history.getStatus());
        assertNull(device.getStatusConfirmedAt());
    }

    @Test
    void failedAckMarksHistoryWithoutClaimingDeviceStatus() throws Exception {
        Device device = new Device();
        device.setDeviceKey("lamp2");
        ActionHistory history = pending(device, LocalDateTime.now());
        when(actions.findByRequestIdForUpdate("550e8400-e29b-41d4-a716-446655440000"))
                .thenReturn(Optional.of(history));

        handler.handle("iot/device01/ack", ack("lamp2", "FAILED"), false);

        assertEquals(ActionStatus.FAILED, history.getStatus());
        assertNull(device.getStatusConfirmedAt());
    }

    private static ActionHistory pending(Device device, LocalDateTime createdAt) throws Exception {
        ActionHistory history = new ActionHistory();
        history.setDevice(device);
        history.setAction(DeviceAction.ON);
        history.setStatus(ActionStatus.PENDING);
        Field field = ActionHistory.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(history, createdAt);
        return history;
    }

    private static String ack(String deviceKey, String result) {
        return "{\"requestId\":\"550e8400-e29b-41d4-a716-446655440000\"," +
                "\"deviceKey\":\"" + deviceKey + "\",\"action\":\"ON\"," +
                "\"result\":\"" + result + "\",\"state\":\"ON\"}";
    }
}
