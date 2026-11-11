package org.example.back_end_iot.mqtt;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.example.back_end_iot.entity.ActionHistory;
import org.example.back_end_iot.entity.Device;
import org.example.back_end_iot.entity.User;
import org.example.back_end_iot.enums.ActionStatus;
import org.example.back_end_iot.enums.DeviceAction;
import org.example.back_end_iot.repository.ActionHistoryRepository;
import org.example.back_end_iot.repository.DeviceRepository;
import org.example.back_end_iot.repository.UserRepository;
import org.example.back_end_iot.realtime.RealtimeEvents;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class DeviceControlService {
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final ActionHistoryRepository actionHistoryRepository;
    private final MqttGateway gateway;
    private final MqttEventHandler eventHandler;
    private final JsonMapper mapper;
    private final TransactionTemplate transactions;
    private final RealtimeEvents realtime;

    public DeviceControlService(UserRepository userRepository, DeviceRepository deviceRepository,
                                ActionHistoryRepository actionHistoryRepository, MqttGateway gateway,
                                MqttEventHandler eventHandler, JsonMapper mapper,
                                PlatformTransactionManager transactionManager, RealtimeEvents realtime) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.actionHistoryRepository = actionHistoryRepository;
        this.gateway = gateway;
        this.eventHandler = eventHandler;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.realtime = realtime;
    }

    public CommandReceipt requestControl(Integer userId, Integer deviceId, DeviceAction action) {
        if (action == null) throw new IllegalArgumentException("Thiếu lệnh ON/OFF");
        if (!gateway.isConnected() || !eventHandler.isDeviceOnline()) {
            throw new IllegalStateException("ESP32 hoặc MQTT broker chưa kết nối");
        }

        CommandReceipt receipt = transactions.execute(status -> {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng"));
            Device device = deviceRepository.findByIdForUpdate(deviceId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thiết bị"));
            if (device.getStatusConfirmedAt() == null) {
                throw new IllegalStateException("Chưa có trạng thái được ESP32 xác nhận");
            }
            if (actionHistoryRepository.existsByDeviceAndStatus(device, ActionStatus.PENDING)) {
                throw new IllegalStateException("Đèn đang chờ xác nhận lệnh trước");
            }
            ActionHistory history = new ActionHistory();
            history.setUser(user);
            history.setDevice(device);
            history.setAction(action);
            history.setStatus(ActionStatus.PENDING);
            history.setRequestId(UUID.randomUUID().toString());
            actionHistoryRepository.saveAndFlush(history);
            realtime.command(history);
            return new CommandReceipt(history.getId(), history.getRequestId(),
                    device.getDeviceKey(), action, ActionStatus.PENDING, 5);
        });

        if (receipt == null) throw new IllegalStateException("Không tạo được lệnh điều khiển");
        String payload = mapper.valueToTree(Map.of(
                "requestId", receipt.requestId(),
                "deviceKey", receipt.deviceKey(),
                "action", receipt.action().name())).toString();
        try {
            gateway.publishCommand(payload);
        } catch (MqttException ex) {
            transactions.executeWithoutResult(status -> actionHistoryRepository
                    .findByRequestIdForUpdate(receipt.requestId())
                    .filter(history -> history.getStatus() == ActionStatus.PENDING)
                    .ifPresent(history -> {
                        history.setStatus(ActionStatus.FAILED);
                        realtime.command(history);
                    }));
            throw new IllegalStateException("Không gửi được lệnh MQTT", ex);
        }
        return receipt;
    }

    @Scheduled(fixedDelay = 500)
    public void expirePendingCommands() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(5);
        transactions.executeWithoutResult(status -> {
            for (ActionHistory candidate : actionHistoryRepository
                    .findByStatusAndCreatedAtBefore(ActionStatus.PENDING, cutoff)) {
                actionHistoryRepository.findByRequestIdForUpdate(candidate.getRequestId())
                        .filter(history -> history.getStatus() == ActionStatus.PENDING)
                        .ifPresent(history -> {
                            history.setStatus(ActionStatus.TIMEOUT);
                            realtime.command(history);
                        });
            }
        });
    }

    public record CommandReceipt(Integer id, String requestId, String deviceKey,
                                 DeviceAction action, ActionStatus status, int timeoutSeconds) {
    }
}
