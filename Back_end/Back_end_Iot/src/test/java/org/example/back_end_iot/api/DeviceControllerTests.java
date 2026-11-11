package org.example.back_end_iot.api;

import org.example.back_end_iot.security.JwtService;
import org.example.back_end_iot.config.PasswordConfig;
import org.example.back_end_iot.config.SecurityConfig;
import org.example.back_end_iot.entity.Device;
import org.example.back_end_iot.entity.User;
import org.example.back_end_iot.enums.ActionStatus;
import org.example.back_end_iot.enums.DeviceAction;
import org.example.back_end_iot.mqtt.DeviceControlService;
import org.example.back_end_iot.mqtt.MqttEventHandler;
import org.example.back_end_iot.repository.ActionHistoryRepository;
import org.example.back_end_iot.repository.DeviceRepository;
import org.example.back_end_iot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtService.class})
class DeviceControllerTests {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean DeviceRepository devices;
    @MockitoBean UserRepository users;
    @MockitoBean ActionHistoryRepository actions;
    @MockitoBean DeviceControlService control;
    @MockitoBean MqttEventHandler mqttEvents;

    @Test
    void requiresBearerInsteadOfBasicOrSession() throws Exception {
        mvc.perform(get("/api/devices")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/devices").with(httpBasic("admin", "secret")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/devices/lamp1/commands")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"ON\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/devices").with(jwt().jwt(j -> j.subject("admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void authenticatedControlCreatesTrackedCommand() throws Exception {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("admin");
        when(user.getPassword()).thenReturn(passwordEncoder.encode("secret"));
        when(user.getId()).thenReturn(7);
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));
        Device device = mock(Device.class);
        when(device.getId()).thenReturn(1);
        when(devices.findByDeviceKey("lamp1")).thenReturn(Optional.of(device));
        when(control.requestControl(7, 1, DeviceAction.ON)).thenReturn(
                new DeviceControlService.CommandReceipt(12, "550e8400-e29b-41d4-a716-446655440000",
                        "lamp1", DeviceAction.ON, ActionStatus.PENDING, 5));

        mvc.perform(post("/api/devices/lamp1/commands")
                        .with(jwt().jwt(j -> j.subject("admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ON\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value("550e8400-e29b-41d4-a716-446655440000"));
        verify(control).requestControl(7, 1, DeviceAction.ON);
    }
}
