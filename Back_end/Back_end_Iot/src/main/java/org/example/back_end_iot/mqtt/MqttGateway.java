package org.example.back_end_iot.mqtt;

import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MqttGateway {
    private static final Logger log = LoggerFactory.getLogger(MqttGateway.class);
    public static final String CONTROL_TOPIC = "iot/device01/control";
    private static final String SENSOR_TOPIC = "iot/device01/sensor";
    private static final String ACK_TOPIC = "iot/device01/ack";
    private static final String STATUS_TOPIC = "iot/device01/status/+";
    private static final String AVAILABILITY_TOPIC = "iot/device01/availability";

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String clientId;
    private final MqttEventHandler eventHandler;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ThreadPoolExecutor sensorExecutor = worker("mqtt-sensor");
    private final ThreadPoolExecutor controlExecutor = worker("mqtt-control");
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final AtomicBoolean subscribing = new AtomicBoolean();
    private volatile boolean subscribed;
    private MqttAsyncClient client;

    public MqttGateway(
            @Value("${iot.mqtt.host}") String host,
            @Value("${iot.mqtt.port}") int port,
            @Value("${iot.mqtt.username:}") String username,
            @Value("${iot.mqtt.password:}") String password,
            @Value("${iot.mqtt.client-id}") String clientId,
            MqttEventHandler eventHandler) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.clientId = clientId;
        this.eventHandler = eventHandler;
    }

    private static ThreadPoolExecutor worker(String name) {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(256), runnable -> {
                    Thread thread = new Thread(runnable, name);
                    thread.setDaemon(true);
                    return thread;
                }, (runnable, executor) -> log.warn("MQTT worker {} đầy; bỏ qua bản tin", name));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() throws MqttException {
        client = new MqttAsyncClient("tcp://" + host + ":" + port, clientId, new MemoryPersistence());
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                subscribed = false;
                subscribeIfNeeded();
                log.info("Đã kết nối MQTT: {}", serverURI);
            }

            @Override
            public void connectionLost(Throwable cause) {
                subscribed = false;
                eventHandler.markOffline();
                log.warn("Mất kết nối MQTT: {}", cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                Runnable task = () -> {
                    try {
                        eventHandler.handle(topic, payload, message.isRetained());
                    } catch (Exception ex) {
                        log.error("Không xử lý được bản tin MQTT từ {}", topic, ex);
                    }
                };
                if (SENSOR_TOPIC.equals(topic)) sensorExecutor.execute(task);
                else controlExecutor.execute(task);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        reconnectExecutor.scheduleWithFixedDelay(this::connectIfNeeded, 0, 5, TimeUnit.SECONDS);
    }

    private void connectIfNeeded() {
        if (client.isConnected()) {
            subscribeIfNeeded();
            return;
        }
        if (!connecting.compareAndSet(false, true)) return;
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(3);
        if (!username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        try {
            client.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                    connecting.set(false);
                    subscribeIfNeeded();
                }

                @Override
                public void onFailure(IMqttToken token, Throwable exception) {
                    connecting.set(false);
                    log.warn("Chưa kết nối được MQTT: {}", exception.getMessage());
                }
            });
        } catch (MqttException ex) {
            connecting.set(false);
            log.warn("Chưa kết nối được MQTT: {}", ex.getMessage());
        }
    }

    private void subscribeIfNeeded() {
        if (!client.isConnected() || subscribed || !subscribing.compareAndSet(false, true)) return;
        String[] topics = {SENSOR_TOPIC, ACK_TOPIC, STATUS_TOPIC, AVAILABILITY_TOPIC};
        int[] qos = {0, 0, 0, 0};
        try {
            client.subscribe(topics, qos, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                    subscribed = true;
                    subscribing.set(false);
                }

                @Override
                public void onFailure(IMqttToken token, Throwable exception) {
                    subscribing.set(false);
                    log.warn("Không subscribe được MQTT: {}", exception.getMessage());
                }
            });
        } catch (MqttException ex) {
            subscribing.set(false);
            log.warn("Không subscribe được MQTT: {}", ex.getMessage());
        }
    }

    public void publishCommand(String payload) throws MqttException {
        if (client == null || !client.isConnected()) throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(0);
        message.setRetained(false);
        client.publish(CONTROL_TOPIC, message).waitForCompletion(2000);
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    @PreDestroy
    public void stop() {
        reconnectExecutor.shutdownNow();
        sensorExecutor.shutdown();
        controlExecutor.shutdown();
        if (client == null) return;
        try {
            if (client.isConnected()) client.disconnectForcibly();
            client.close();
        } catch (MqttException ex) {
            log.warn("Không đóng được MQTT client: {}", ex.getMessage());
        }
    }
}
