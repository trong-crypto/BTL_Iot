package org.example.back_end_iot.config;

import org.example.back_end_iot.entity.Device;
import org.example.back_end_iot.entity.Sensor;
import org.example.back_end_iot.entity.User;
import org.example.back_end_iot.enums.DeviceStatus;
import org.example.back_end_iot.repository.DeviceRepository;
import org.example.back_end_iot.repository.SensorRepository;
import org.example.back_end_iot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataInitializer implements CommandLineRunner {


    private final UserRepository userRepository;
    private final SensorRepository sensorRepository;
    private final PasswordEncoder passwordEncoder ;
    private final DeviceRepository deviceRepository;
    @Value("${IOT_ADMIN_USERNAME:admin}")
    private String adminUsername;

    @Value("${IOT_ADMIN_PASSWORD:}")
    private String adminPassword;

    public DataInitializer(
            UserRepository userRepository,
            SensorRepository sensorRepository,
            PasswordEncoder passwordEncoder,
            DeviceRepository deviceRepository ){

        this.userRepository = userRepository;
        this.sensorRepository = sensorRepository;
        this.passwordEncoder = passwordEncoder;
        this.deviceRepository = deviceRepository ;
    }

    @Override
    @Transactional
    public void run(String... args) {
        createInitialUser();

        createSensorIfMissing("temperature", "Temperature", "°C");
        createSensorIfMissing("humidity", "Humidity", "%");
        createSensorIfMissing("light", "Light", "ADC");

        createDeviceIfMissing("lamp1", "Đèn 1");
        createDeviceIfMissing("lamp2", "Đèn 2");
    }

    private void createInitialUser() {
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }

        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Hãy cấu hình IOT_ADMIN_PASSWORD để tạo tài khoản ban đầu."
            );
        }

        User user = new User();
        user.setUsername(adminUsername);
        user.setPassword(passwordEncoder.encode(adminPassword));
        user.setFullName("Quản trị viên");

        userRepository.save(user);
    }

    private void createSensorIfMissing(String key, String name, String unit) {
        if (sensorRepository.existsByName(name)) {
            return;
        }

        Sensor sensor = new Sensor();
        sensor.setName(name);
        sensor.setSensorKey(key);
        sensor.setUnit(unit);

        sensorRepository.save(sensor);
    }
    private void createDeviceIfMissing(String key, String name) {
        if (deviceRepository.existsByDeviceKey(key)) {
            return;
        }
        Device device = new Device();
        device.setName(name);
        device.setDeviceKey(key);
        device.setStatus(DeviceStatus.OFF);
        deviceRepository.save(device);
    }
}
