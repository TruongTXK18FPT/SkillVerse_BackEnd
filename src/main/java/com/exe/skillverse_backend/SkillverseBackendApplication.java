package com.exe.skillverse_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SkillverseBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillverseBackendApplication.class, args);
    }
}
