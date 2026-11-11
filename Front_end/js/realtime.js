(() => {
    "use strict";
    window.iotRealtime = {
        start(handlers) {
            const client = new StompJs.Client({
                brokerURL: `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws`,
                reconnectDelay: 3000,
                connectionTimeout: 10000,
                heartbeatIncoming: 10000,
                heartbeatOutgoing: 10000,
                beforeConnect: async () => {
                    try {
                        client.connectHeaders = { Authorization: `Bearer ${window.iotAuth.token()}` };
                    } catch (error) {
                        client.connectHeaders = {};
                        handlers.disconnected?.(error.message);
                    }
                },
                onConnect: () => {
                    const topics = [
                        ["/topic/sensor-data", handlers.sensor],
                        ["/topic/devices", handlers.device],
                        ["/topic/availability", handlers.availability],
                        ["/user/queue/commands", handlers.command]
                    ];
                    topics.forEach(([topic, callback]) =>
                        client.subscribe(topic, frame => callback?.(JSON.parse(frame.body))));
                    handlers.connected?.();
                },
                onWebSocketClose: () => handlers.disconnected?.("Mất kết nối cập nhật; đang kết nối lại..."),
                onStompError: () => handlers.disconnected?.("Kênh cập nhật bị từ chối; đang kết nối lại..."),
                onWebSocketError: () => handlers.disconnected?.("Không kết nối được kênh cập nhật.")
            });
            window.addEventListener("iot:logout", () => client.deactivate());
            client.activate();
            window.addEventListener("pagehide", () => client.deactivate());
            return client;
        }
    };
})();
