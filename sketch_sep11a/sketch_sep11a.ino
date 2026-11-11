#include <Arduino.h>
#include <ArduinoJson.h>
#include <DHT.h>
#include <Preferences.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include <WiFiManager.h>
#include "secrets.h"

constexpr uint8_t DHT_PIN = 27;
constexpr uint8_t LIGHT_AO_PIN = 34;
constexpr uint8_t DHT_TYPE = DHT11;
constexpr unsigned long READ_INTERVAL_MS = 2000;
constexpr unsigned long MQTT_RETRY_INTERVAL_MS = 5000;
constexpr size_t MQTT_BUFFER_SIZE = 512;

const char *CONTROL_TOPIC = "iot/device01/control";
const char *ACK_TOPIC = "iot/device01/ack";
const char *SENSOR_TOPIC = "iot/device01/sensor";
const char *AVAILABILITY_TOPIC = "iot/device01/availability";

struct Lamp {
  const char *key;
  uint8_t pin;
  const char *statusTopic;
};

Lamp lamps[] = {
    {"lamp1", 25, "iot/device01/status/lamp1"},
    {"lamp2", 26, "iot/device01/status/lamp2"},
};
constexpr size_t LAMP_COUNT = sizeof(lamps) / sizeof(lamps[0]);

struct RecentCommand {
  String requestId;
  String deviceKey;
  String action;
  String result;
};
RecentCommand recentCommands[8];
size_t nextRecentCommand = 0;

DHT dht(DHT_PIN, DHT_TYPE);
Preferences lampPreferences;
WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);
unsigned long lastReadMs = 0;
unsigned long lastMqttAttemptMs = 0;

Lamp *findLamp(const char *key) {
  for (Lamp &lamp : lamps) {
    if (strcmp(lamp.key, key) == 0) return &lamp;
  }
  return nullptr;
}

const char *lampState(const Lamp &lamp) {
  return digitalRead(lamp.pin) == HIGH ? "ON" : "OFF";
}

bool publishJson(const char *topic, JsonDocument &doc, bool retained) {
  if (!mqttClient.connected()) return false;
  char payload[256];
  size_t length = serializeJson(doc, payload, sizeof(payload));
  if (length == 0 || length >= sizeof(payload)) {
    Serial.println("MQTT JSON vuot kich thuoc.");
    return false;
  }
  return mqttClient.publish(topic, payload, retained);
}

void publishLampState(const Lamp &lamp, const char *state = nullptr) {
  JsonDocument doc;
  doc["deviceKey"] = lamp.key;
  doc["state"] = state ? state : lampState(lamp);
  if (!publishJson(lamp.statusTopic, doc, true)) {
    Serial.println("Khong gui duoc trang thai den.");
  }
}

void publishAck(const char *requestId, const Lamp &lamp, const char *action,
                const char *result, const char *state) {
  JsonDocument doc;
  doc["requestId"] = requestId;
  doc["deviceKey"] = lamp.key;
  doc["action"] = action;
  doc["result"] = result;
  doc["state"] = state;
  if (!publishJson(ACK_TOPIC, doc, false)) {
    Serial.println("Khong gui duoc xac nhan lenh.");
  }
}

void mqttCallback(char *topic, byte *payload, unsigned int length) {
  if (strcmp(topic, CONTROL_TOPIC) != 0) return;
  if (length == 0 || length >= 256) {
    Serial.println("Lenh MQTT qua dai hoac rong.");
    return;
  }

  char text[256];
  memcpy(text, payload, length);
  text[length] = '\0';
  JsonDocument doc;
  if (deserializeJson(doc, text)) {
    Serial.println("Lenh MQTT khong phai JSON hop le.");
    return;
  }

  const char *requestId = doc["requestId"].as<const char *>();
  const char *deviceKey = doc["deviceKey"].as<const char *>();
  const char *action = doc["action"].as<const char *>();
  if (!requestId || strlen(requestId) != 36 || !deviceKey || !action) {
    Serial.println("Lenh MQTT thieu truong bat buoc.");
    return;
  }

  Lamp *lamp = findLamp(deviceKey);
  if (!lamp) {
    Serial.println("Khong tim thay den.");
    return;
  }
  if (strcmp(action, "ON") != 0 && strcmp(action, "OFF") != 0) {
    publishAck(requestId, *lamp, action, "FAILED", lampState(*lamp));
    return;
  }

  for (const RecentCommand &previous : recentCommands) {
    if (previous.requestId == requestId) {
      if (previous.deviceKey == deviceKey && previous.action == action) {
        const char *state = lampState(*lamp);
        const char *result = strcmp(state, action) == 0
                                 ? previous.result.c_str()
                                 : "FAILED";
        publishLampState(*lamp, state);
        publishAck(requestId, *lamp, action, result, state);
      } else {
        publishAck(requestId, *lamp, action, "FAILED", lampState(*lamp));
      }
      return;
    }
  }

  const bool wasOn = digitalRead(lamp->pin) == HIGH;
  const bool turnOn = strcmp(action, "ON") == 0;
  const char *result = "SUCCESS";
  if (wasOn != turnOn) {
    digitalWrite(lamp->pin, turnOn ? HIGH : LOW);
    if (strcmp(lampState(*lamp), action) != 0) {
      result = "FAILED";
    } else if (lampPreferences.putBool(lamp->key, turnOn) == 0) {
      digitalWrite(lamp->pin, wasOn ? HIGH : LOW);
      Serial.println("Khong luu duoc trang thai den vao NVS.");
      result = "FAILED";
    }
  }
  const char *state = lampState(*lamp);
  recentCommands[nextRecentCommand] = {requestId, deviceKey, action, result};
  nextRecentCommand = (nextRecentCommand + 1) % 8;
  publishLampState(*lamp, state);
  publishAck(requestId, *lamp, action, result, state);
}

void connectMqtt() {
  if (mqttClient.connected() || millis() - lastMqttAttemptMs < MQTT_RETRY_INTERVAL_MS) {
    return;
  }
  lastMqttAttemptMs = millis();

  String clientId = "esp32-led-" + String(static_cast<uint32_t>(ESP.getEfuseMac()), HEX);
  if (!mqttClient.connect(clientId.c_str(), MQTT_USERNAME, MQTT_PASSWORD,
                          AVAILABILITY_TOPIC, 0, true, "OFFLINE")) {
    Serial.print("MQTT khong ket noi, ma loi = ");
    Serial.println(mqttClient.state());
    return;
  }

  mqttClient.subscribe(CONTROL_TOPIC, 0);
  mqttClient.publish(AVAILABILITY_TOPIC, "ONLINE", true);
  for (const Lamp &lamp : lamps) publishLampState(lamp);
  Serial.println("MQTT da ket noi va dong bo 2 den.");
}

void readAndPublishSensors() {
  float humidity = dht.readHumidity();
  float temperature = dht.readTemperature();
  int light = 4095 - analogRead(LIGHT_AO_PIN);

  if (isnan(humidity) || isnan(temperature)) {
    Serial.println("DHT11 khong doc duoc; bo qua lan do nay.");
    return;
  }

  char payload[128];
  int length = snprintf(payload, sizeof(payload),
                        "{\"temperature\":%.1f,\"humidity\":%.1f,\"light\":%d}",
                        temperature, humidity, light);
  if (length <= 0 || length >= static_cast<int>(sizeof(payload))) return;
  if (mqttClient.connected()) {
    mqttClient.publish(SENSOR_TOPIC, payload, false);
  }
  Serial.println(payload);
}

void setup() {
  Serial.begin(115200);
  const bool storageReady = lampPreferences.begin("lamp-state", false);
  if (!storageReady) {
    Serial.println("Khong mo duoc NVS de khoi phuc trang thai den.");
  }
  for (const Lamp &lamp : lamps) {
    const bool savedOn = storageReady && lampPreferences.isKey(lamp.key) &&
                         lampPreferences.getBool(lamp.key, false);
    pinMode(lamp.pin, OUTPUT);
    digitalWrite(lamp.pin, savedOn ? HIGH : LOW);
  }
  dht.begin();
  analogReadResolution(12);

  WiFiManager wifiManager;
  wifiManager.setConfigPortalTimeout(180);
  if (!wifiManager.autoConnect("ESP32-LED-Control")) {
    ESP.restart();
  }
  Serial.print("Wi-Fi IP cua ESP32: ");
  Serial.println(WiFi.localIP());
  Serial.print("MQTT broker: ");
  Serial.print(MQTT_HOST);
  Serial.print(":");
  Serial.println(MQTT_PORT);

  mqttClient.setServer(MQTT_HOST, MQTT_PORT);
  mqttClient.setCallback(mqttCallback);
  if (!mqttClient.setBufferSize(MQTT_BUFFER_SIZE)) {
    Serial.println("Khong cap phat duoc bo dem MQTT.");
  }
  mqttClient.setSocketTimeout(2);
  lastReadMs = millis();
}

void loop() {
  mqttClient.loop();
  unsigned long now = millis();
  if (now - lastReadMs >= READ_INTERVAL_MS) {
    lastReadMs = now;
    readAndPublishSensors();
  }
  connectMqtt();
}
