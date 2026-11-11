package org.example.back_end_iot.api;

import org.example.back_end_iot.security.JwtService;
import org.example.back_end_iot.config.PasswordConfig;
import org.example.back_end_iot.config.SecurityConfig;
import org.example.back_end_iot.entity.Sensor;
import org.example.back_end_iot.entity.SensorData;
import org.example.back_end_iot.entity.ActionHistory;
import org.example.back_end_iot.entity.Device;
import org.example.back_end_iot.entity.User;
import org.example.back_end_iot.enums.ActionStatus;
import org.example.back_end_iot.enums.DeviceAction;
import org.example.back_end_iot.repository.ActionHistoryRepository;
import org.example.back_end_iot.repository.SensorDataRepository;
import org.example.back_end_iot.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReadController.class)
@Import({SecurityConfig.class, PasswordConfig.class, JwtService.class})
class ReadControllerTests {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwordEncoder;
    @MockitoBean SensorDataRepository readings;
    @MockitoBean ActionHistoryRepository actions;
    @MockitoBean UserRepository users;

    @Test
    void loginPageIsPublicButReadingsRequireLogin() throws Exception {
        mvc.perform(get("/login.html")).andExpect(status().isOk());
        mvc.perform(get("/api/readings")).andExpect(status().isUnauthorized());
    }

    @Test
    void jwtAllowsReadingAndHistoryPages() throws Exception {
        User user = mock(User.class);
        when(user.getUsername()).thenReturn("admin");
        when(user.getPassword()).thenReturn(passwordEncoder.encode("secret"));
        when(users.findByUsername("admin")).thenReturn(Optional.of(user));

        Sensor sensor = mock(Sensor.class);
        when(sensor.getSensorKey()).thenReturn("light");
        when(sensor.getName()).thenReturn("Light");
        when(sensor.getUnit()).thenReturn("ADC");
        SensorData reading = mock(SensorData.class);
        when(reading.getId()).thenReturn(7);
        when(reading.getSensor()).thenReturn(sensor);
        when(reading.getValue()).thenReturn(1200.0);
        when(reading.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 4, 10, 0));
        when(readings.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reading), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/readings").with(jwt().jwt(j -> j.subject("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].sensorKey").value("light"))
                .andExpect(jsonPath("$.items[0].unit").value("ADC"))
                .andExpect(jsonPath("$.items[0].createdAt").isString());

        Device device = mock(Device.class);
        when(device.getDeviceKey()).thenReturn("lamp1");
        when(device.getName()).thenReturn("Đèn 1");
        ActionHistory action = mock(ActionHistory.class);
        when(action.getId()).thenReturn(9);
        when(action.getUser()).thenReturn(user);
        when(action.getDevice()).thenReturn(device);
        when(action.getAction()).thenReturn(DeviceAction.ON);
        when(action.getStatus()).thenReturn(ActionStatus.SUCCESS);
        when(action.getCreatedAt()).thenReturn(LocalDateTime.of(2026, 11, 4, 10, 1));
        when(actions.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(action), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/history").with(jwt().jwt(j -> j.subject("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].deviceKey").value("lamp1"))
                .andExpect(jsonPath("$.items[0].status").value("SUCCESS"));
    }
}
