package org.example.back_end_iot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackEndIotApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackEndIotApplication.class, args);
    }

}
