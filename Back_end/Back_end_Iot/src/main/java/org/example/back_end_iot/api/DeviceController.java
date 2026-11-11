package org.example.back_end_iot.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.example.back_end_iot.entity.ActionHistory;
import org.example.back_end_iot.entity.Device;
import org.example.back_end_iot.entity.User;
import org.example.back_end_iot.enums.ActionStatus;
import org.example.back_end_iot.enums.DeviceAction;
import org.example.back_end_iot.enums.DeviceStatus;
import org.example.back_end_iot.mqtt.DeviceControlService;
import org.example.back_end_iot.mqtt.MqttEventHandler;
import org.example.back_end_iot.repository.ActionHistoryRepository;
import org.example.back_end_iot.repository.DeviceRepository;
import org.example.back_end_iot.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class DeviceController {
    private final DeviceRepository devices;
    private final UserRepository users;
    private final ActionHistoryRepository actions;
    private final DeviceControlService control;
    private final MqttEventHandler mqttEvents;

    public DeviceController(DeviceRepository devices, UserRepository users,
                            ActionHistoryRepository actions, DeviceControlService control,
                            MqttEventHandler mqttEvents) {
        this.devices = devices;
        this.users = users;
        this.actions = actions;
        this.control = control;
        this.mqttEvents = mqttEvents;
    }

    @GetMapping("/devices")
    public List<DeviceView> devices() {
        boolean online = mqttEvents.isDeviceOnline();
        return devices.findAll().stream().map(device -> new DeviceView(
                device.getId(), device.getDeviceKey(), device.getName(),
                device.getStatus(), device.getStatusConfirmedAt(), online)).toList();
    }

    @GetMapping("/devices/{deviceId}/status")
    public DeviceStatusView status(@PathVariable Integer deviceId) {
        Device device = devices.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đèn"));
        return new DeviceStatusView(device.getId(), device.getDeviceKey(), device.getName(), device.getStatus(),
                device.getStatusConfirmedAt(), mqttEvents.isDeviceOnline());
    }

    @PostMapping("/devices/{deviceId}/control")
    public ResponseEntity<ControlReceipt> control(@PathVariable Integer deviceId,
            @Valid @RequestBody ControlRequest request, Principal principal) {
        if (request.deviceId() != null && !deviceId.equals(request.deviceId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deviceId không khớp đường dẫn");
        User user = users.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!devices.existsById(deviceId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đèn");
        try {
            var receipt = control.requestControl(user.getId(), deviceId, request.action());
            return ResponseEntity.accepted().body(new ControlReceipt(receipt.id(), receipt.requestId(), deviceId,
                    receipt.deviceKey(), receipt.action(), receipt.status(), receipt.timeoutSeconds()));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    @PostMapping("/devices/{deviceKey}/commands")
    public ResponseEntity<DeviceControlService.CommandReceipt> command(
            @PathVariable String deviceKey, @Valid @RequestBody CommandRequest request,
            Principal principal) {
        User user = users.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Device device = devices.findByDeviceKey(deviceKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đèn"));
        try {
            return ResponseEntity.accepted().body(
                    control.requestControl(user.getId(), device.getId(), request.action()));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    @GetMapping("/commands/{requestId}")
    public CommandView commandStatus(@PathVariable String requestId, Principal principal) {
        ActionHistory action = actions.findByRequestIdAndUsername(requestId, principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new CommandView(action.getId(), action.getRequestId(),
                action.getDevice().getDeviceKey(), action.getAction(),
                action.getStatus(), action.getCreatedAt());
    }

    public record CommandRequest(@NotNull DeviceAction action) {
    }
    public record ControlRequest(Integer deviceId, @NotNull DeviceAction action) { }
    public record ControlReceipt(Integer id, String requestId, Integer deviceId, String deviceKey,
                                 DeviceAction action, ActionStatus status, int timeoutSeconds) { }
    public record DeviceStatusView(Integer deviceId, String deviceKey, String name, DeviceStatus status,
                                   LocalDateTime statusConfirmedAt, boolean online) { }

    public record DeviceView(Integer id, String deviceKey, String name, DeviceStatus status,
                             LocalDateTime statusConfirmedAt, boolean online) {
    }

    public record CommandView(Integer id, String requestId, String deviceKey,
                              DeviceAction action, ActionStatus status, LocalDateTime createdAt) {
    }
}
