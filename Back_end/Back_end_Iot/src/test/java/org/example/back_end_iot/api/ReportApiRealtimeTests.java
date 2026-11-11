package org.example.back_end_iot.api;

import org.example.back_end_iot.mqtt.MqttEventHandler;
import org.example.back_end_iot.mqtt.MqttGateway;
import org.example.back_end_iot.repository.DeviceRepository;
import org.example.back_end_iot.repository.SensorDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReportApiRealtimeTests {
    private String accessToken;
    @LocalServerPort int port;
    @Autowired JsonMapper mapper;
    @Autowired MqttEventHandler mqtt;
    @Autowired SensorDataRepository readings;
    @Autowired DeviceRepository devices;
    @Autowired SimpleBrokerMessageHandler broker;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean MqttGateway gateway;

    @Test
    void jsonLoginReportApisAndCommittedStompPushWorkTogether() throws Exception {
        when(gateway.isConnected()).thenReturn(true);
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
        var unauthenticated = client.send(HttpRequest.newBuilder(URI.create(base() + "/api/devices"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, unauthenticated.statusCode());
        var invalidLogin = post(client, "/api/auth/login", "{\"username\":\"admin\"}", null);
        assertEquals(400, invalidLogin.statusCode());
        var badLogin = post(client, "/api/auth/login", "{\"username\":\"admin\",\"password\":\"wrong\"}", null);
        assertEquals(401, badLogin.statusCode());
        var login = post(client, "/api/auth/login", "{\"username\":\"admin\",\"password\":\"test-admin-password\"}", null);
        assertEquals(200, login.statusCode());
        assertEquals("admin", mapper.readTree(login.body()).get("username").stringValue());
        accessToken = mapper.readTree(login.body()).get("accessToken").stringValue();
        assertEquals("Bearer", mapper.readTree(login.body()).get("tokenType").stringValue());
        assertTrue(login.headers().firstValue("Set-Cookie").isEmpty());
        assertEquals("no-store", login.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base() + "/api/devices"))
                .GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(401, client.send(HttpRequest.newBuilder(URI.create(base() + "/api/devices"))
                .header("Authorization", "Bearer " + accessToken.substring(0, accessToken.length() - 8) + "INVALID!")
                .GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        assertTrue(mapper.readTree(login.body()).has("full_name"));
        assertEquals(2, json(client, "/api/devices").size());


        Frames frames = new Frames();
        WebSocket socket = client.newWebSocketBuilder().header("Origin", base())
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), frames).get(5, TimeUnit.SECONDS);
        try {
            socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n" +
                    "Authorization:Bearer " + accessToken + "\n\n\0", true).join();
            assertTrue(frames.take("CONNECTED", 5).startsWith("CONNECTED"));
            subscribe(socket, "sensors", "/topic/sensor-data");
            subscribe(socket, "devices", "/topic/devices");
            subscribe(socket, "commands", "/user/queue/commands");
            awaitSubscription("/topic/sensor-data");
            awaitSubscription("/topic/devices");

            mqtt.handle("iot/device01/sensor", "{\"temperature\":28.5,\"humidity\":70.2,\"light\":1530}", false);
            String sensorFrame = frames.take("\"temperature\":28.5", 5);
            assertTrue(sensorFrame.startsWith("MESSAGE"));
            assertEquals(3, readings.count());
            assertEquals(28.5, json(client, "/api/sensor-data/latest").get("temperature").doubleValue());
            assertEquals(1, json(client, "/api/sensor-data/chart?limit=20").get("light").size());
            assertEquals(3, json(client, "/api/sensor-data/history?page=0&size=10&sort=createdAt,desc").get("content").size());

            // A rolled-back measurement must not become a WebSocket update.
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                mqtt.handle("iot/device01/sensor", "{\"temperature\":99,\"humidity\":50,\"light\":100}", false);
                status.setRollbackOnly();
            });
            assertEquals(3, readings.count());
            assertNull(frames.queue.poll(250, TimeUnit.MILLISECONDS));

            mqtt.handle("iot/device01/availability", "ONLINE", true);
            mqtt.handle("iot/device01/status/lamp1", "{\"deviceKey\":\"lamp1\",\"state\":\"OFF\"}", true);
            frames.take("\"deviceKey\":\"lamp1\"", 5);
            int deviceId = devices.findByDeviceKey("lamp1").orElseThrow().getId();
            assertEquals("OFF", json(client, "/api/devices/" + deviceId + "/status").get("status").stringValue());
            var receiptResponse = post(client, "/api/devices/" + deviceId + "/control", "{\"action\":\"ON\"}", accessToken);
            assertEquals(202, receiptResponse.statusCode());
            JsonNode receipt = mapper.readTree(receiptResponse.body());
            String requestId = receipt.get("requestId").stringValue();
            String otherUserToken = signedToken(Instant.now().plusSeconds(60), "other-user");
            var otherUserResult = client.send(HttpRequest.newBuilder(URI.create(base() + "/api/commands/" + requestId))
                    .header("Authorization", "Bearer " + otherUserToken).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, otherUserResult.statusCode(), "JWT identity must not expose another user's command");
            mqtt.handle("iot/device01/ack", "{\"requestId\":\"" + requestId +
                    "\",\"deviceKey\":\"lamp1\",\"action\":\"ON\",\"result\":\"SUCCESS\",\"state\":\"ON\"}", false);
            frames.take("\"status\":\"SUCCESS\"", 5);
            assertEquals("SUCCESS", json(client, "/api/devices/action-history?deviceId=" + deviceId)
                    .get("content").get(0).get("status").stringValue());

            post(client, "/api/devices/" + deviceId + "/control", "{\"action\":\"OFF\"}", accessToken);
            frames.take("\"status\":\"TIMEOUT\"", 8);
            var failures = json(client, "/api/devices/action-history?deviceId=" + deviceId + "&status=FAILED&size=1");
            assertEquals(1, failures.get("totalElements").intValue());
            assertEquals("FAILED", failures.get("content").get(0).get("status").stringValue());
            assertEquals("FAILED", json(client, "/api/devices/action-history?deviceId=" + deviceId)
                    .get("content").get(0).get("status").stringValue());
            assertEquals("ON", devices.findById(deviceId).orElseThrow().getStatus().name());
            socket.sendText("SEND\ndestination:/topic/sensor-data\ncontent-type:application/json\n\n{}\0", true).join();
            assertTrue(frames.take("ERROR", 5).startsWith("ERROR"));
        } finally { socket.abort(); }

        // An open handshake does not authorize STOMP without a Bearer JWT.
        Frames rejected = new Frames();
        WebSocket noJwt = client.newWebSocketBuilder().header("Origin", base())
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), rejected).get(5, TimeUnit.SECONDS);
        noJwt.sendText("CONNECT\naccept-version:1.2\nhost:localhost\n\n\0", true).join();
        assertTrue(rejected.take("ERROR", 5).startsWith("ERROR"));
        noJwt.abort();
    }

    private String base() { return "http://127.0.0.1:" + port; }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "IOT_PLAYWRIGHT_MODULE", matches = ".+")
    void browserLoginUsesJwtForRestAndStomp() throws Exception {
        var process = new ProcessBuilder("node", "src/test/browser/jwt-login.cjs", base()).inheritIO().start();
        if (!process.waitFor(45, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Browser JWT test timed out");
        }
        assertEquals(0, process.exitValue(), "Browser JWT verification failed");
    }

    @Test
    void expiredTokensFailRestAndStompAndConnectedSocketClosesAtExpiry() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String expired = signedToken(Instant.now().minusSeconds(2));
        var response = client.send(HttpRequest.newBuilder(URI.create(base() + "/api/devices"))
                .header("Authorization", "Bearer " + expired).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        Frames rejected = new Frames();
        WebSocket invalid = client.newWebSocketBuilder().header("Origin", base())
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), rejected).get(5, TimeUnit.SECONDS);
        invalid.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer " + expired + "\n\n\0", true).join();
        assertTrue(rejected.take("ERROR", 5).startsWith("ERROR"));
        invalid.abort();

        Frames frames = new Frames();
        WebSocket socket = client.newWebSocketBuilder().header("Origin", base())
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), frames).get(5, TimeUnit.SECONDS);
        socket.sendText("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer " +
                signedToken(Instant.now().plusSeconds(3)) + "\n\n\0", true).join();
        assertTrue(frames.take("CONNECTED", 5).startsWith("CONNECTED"));
        assertEquals(1008, frames.closed.get(6, TimeUnit.SECONDS));
        socket.abort();
    }

    private String signedToken(Instant expiry) throws Exception {
        return signedToken(expiry, "admin");
    }

    private String signedToken(Instant expiry, String subject) throws Exception {
        // Matches the zero-byte key in application-test.yaml; never used outside tests.
        var claims = new JWTClaimsSet.Builder().issuer("iot-command-center").subject(subject)
                .audience("iot-web").issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(expiry)).build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(new byte[32]));
        return jwt.serialize();
    }
    private JsonNode json(HttpClient client, String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(base() + path)).header("Authorization", "Bearer " + accessToken).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), path + ": " + response.body());
        return mapper.readTree(response.body());
    }
    private HttpResponse<String> post(HttpClient client, String path, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private void subscribe(WebSocket socket, String id, String destination) {
        socket.sendText("SUBSCRIBE\nid:" + id + "\ndestination:" + destination + "\nack:auto\n\n\0", true).join();
    }
    private void awaitSubscription(String destination) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        var message = MessageBuilder.withPayload(new byte[0])
                .setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, org.springframework.messaging.simp.SimpMessageType.MESSAGE)
                .setHeader(SimpMessageHeaderAccessor.DESTINATION_HEADER, destination).build();
        while (broker.getSubscriptionRegistry().findSubscriptions(message).isEmpty() && System.nanoTime() < end) Thread.sleep(10);
        assertFalse(broker.getSubscriptionRegistry().findSubscriptions(message).isEmpty());
    }
    static class Frames implements WebSocket.Listener {
        final java.util.concurrent.CompletableFuture<Integer> closed = new java.util.concurrent.CompletableFuture<>();
        @Override public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            closed.complete(code); return null;
        }
        final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        final StringBuilder buffer = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            buffer.append(text);
            int end;
            while ((end = buffer.indexOf("\0")) >= 0) {
                queue.add(buffer.substring(0, end).stripLeading());
                buffer.delete(0, end + 1);
            }
            socket.request(1);
            return null;
        }
        @Override public CompletionStage<?> onBinary(WebSocket socket, java.nio.ByteBuffer data, boolean last) {
            return onText(socket, java.nio.charset.StandardCharsets.UTF_8.decode(data), last);
        }
        String take(String match, int seconds) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
            StringBuilder seen = new StringBuilder();
            while (System.nanoTime() < end) {
                String frame = queue.poll(100, TimeUnit.MILLISECONDS);
                if (frame != null && frame.contains(match)) return frame;
                if (frame != null) seen.append(frame).append('\n');
            }
            fail("Không nhận frame STOMP: " + match + "; đã nhận: " + seen);
            return null;
        }
    }
}
