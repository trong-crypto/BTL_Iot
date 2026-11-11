package org.example.back_end_iot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.example.back_end_iot.mqtt.MqttGateway;

@SpringBootTest
@ActiveProfiles("test")
class BackEndIotApplicationTests {
    @MockitoBean MqttGateway gateway;

    @Test
    void contextLoads() {
    }

}
