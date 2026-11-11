package org.example.back_end_iot.mqtt;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class MqttGatewayBrokerTests {
    @Test
    void receivesSensorMessageAndPublishesControlMessage() throws Exception {
        String portSetting = System.getProperty("iot.mqtt.test.port");
        Assumptions.assumeTrue(portSetting != null, "Chỉ chạy khi có broker thử nghiệm");
        int port = Integer.parseInt(portSetting);
        String server = "tcp://127.0.0.1:" + port;
        MqttEventHandler handler = mock(MqttEventHandler.class);
        MqttGateway gateway = new MqttGateway("127.0.0.1", port, "", "",
                "iot-backend-test-" + UUID.randomUUID(), handler);
        MqttAsyncClient observer = new MqttAsyncClient(server, "iot-observer-" + UUID.randomUUID());
        CountDownLatch commandReceived = new CountDownLatch(1);
        observer.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                if (MqttGateway.CONTROL_TOPIC.equals(topic)
                        && "{\"test\":true}".equals(new String(message.getPayload(), StandardCharsets.UTF_8))) {
                    commandReceived.countDown();
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });

        try {
            gateway.start();
            observer.connect().waitForCompletion(5000);
            observer.subscribe(MqttGateway.CONTROL_TOPIC, 0).waitForCompletion(5000);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!gateway.isConnected() && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(gateway.isConnected());
            Thread.sleep(300);

            String sensor = "{\"temperature\":28.5,\"humidity\":70.2,\"light\":1530}";
            observer.publish("iot/device01/sensor",
                    new MqttMessage(sensor.getBytes(StandardCharsets.UTF_8))).waitForCompletion(3000);
            verify(handler, timeout(3000)).handle(eq("iot/device01/sensor"), eq(sensor), eq(false));

            gateway.publishCommand("{\"test\":true}");
            assertTrue(commandReceived.await(3, TimeUnit.SECONDS));
        } finally {
            gateway.stop();
            if (observer.isConnected()) observer.disconnectForcibly();
            observer.close();
        }
    }
}
