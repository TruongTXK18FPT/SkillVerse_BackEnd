package com.exe.skillverse_backend.course_service.policy;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CourseDeletionPolicyProperties.class)
public class CourseDeletionPolicyConfig {
}
