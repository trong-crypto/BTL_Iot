package org.example.back_end_iot.realtime;

import org.example.back_end_iot.entity.ActionHistory;
import org.example.back_end_iot.entity.Device;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.MimeTypeUtils;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class RealtimeEvents {
    private final ApplicationEventPublisher events;
    private final SimpMessagingTemplate messaging;
    private final JsonMapper mapper;

    public RealtimeEvents(ApplicationEventPublisher events, SimpMessagingTemplate messaging, JsonMapper mapper) {
        this.events = events;
        this.messaging = messaging;
        this.mapper = mapper;
    }

    public void publish(String destination, Object payload) {
        events.publishEvent(new Update(destination, null, payload));
    }

    public void device(Device device, boolean online) {
        publish("/topic/devices", new DeviceUpdate(device.getId(), device.getDeviceKey(), device.getName(),
                device.getStatus().name(), device.getStatusConfirmedAt(), online));
    }

    public void command(ActionHistory history) {
        events.publishEvent(new Update("/queue/commands", history.getUser().getUsername(),
                new CommandUpdate(history.getId(), history.getRequestId(), history.getDevice().getId(),
                        history.getDevice().getDeviceKey(), history.getAction().name(), history.getStatus().name())));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(Update update) {
        byte[] json = mapper.writeValueAsString(update.payload()).getBytes(StandardCharsets.UTF_8);
        Map<String, Object> headers = Map.of(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON);
        if (update.username() == null) messaging.convertAndSend(update.destination(), json, headers);
        else messaging.convertAndSendToUser(update.username(), update.destination(), json, headers);
    }

    public record Update(String destination, String username, Object payload) { }
    public record SensorSnapshot(Double temperature, Double humidity, Double light, java.time.LocalDateTime time) { }
    public record DeviceUpdate(Integer deviceId, String deviceKey, String name, String status,
                               java.time.LocalDateTime statusConfirmedAt, boolean online) { }
    public record CommandUpdate(Integer id, String requestId, Integer deviceId, String deviceKey,
                                String action, String status) { }
}
