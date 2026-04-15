package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.service.CourseService;
import com.exe.skillverse_backend.course_service.service.CourseRevisionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
@AutoConfigureMockMvc
@Import(CourseControllerDeleteIntegrationTest.TestSecurityConfig.class)
class CourseControllerDeleteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseService courseService;

    @MockBean
    private CourseRevisionService courseRevisionService;

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(org.springframework.security.config.Customizer.withDefaults());
            return http.build();
        }
    }

    @Test
    void deleteCourse_withMentorRole_returnsNoContent() throws Exception {
        long courseId = 100L;
        long actorId = 7L;

        mockMvc.perform(delete("/api/courses/{courseId}", courseId)
                        .with(jwt().jwt(jwt -> jwt.claim("userId", String.valueOf(actorId)))
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_MENTOR")))))
                .andExpect(status().isNoContent());

        verify(courseService).deleteCourse(courseId, actorId);
    }

    @Test
    void deleteCourse_withUserRole_returnsForbidden() throws Exception {
        long actorId = 7L;

        mockMvc.perform(delete("/api/courses/{courseId}", 100L)
                        .with(jwt().jwt(jwt -> jwt.claim("userId", String.valueOf(actorId)))
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                .andExpect(status().isForbidden());

        verify(courseService, never()).deleteCourse(100L, 7L);
    }
}