package com.exe.skillverse_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.exe.skillverse_backend"})
@EnableScheduling
public class SkillverseBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillverseBackendApplication.class, args);
    }
}
