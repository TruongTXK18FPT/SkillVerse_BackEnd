package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningRevisionInfoDTO;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseLearningController.class)
@AutoConfigureMockMvc
@Import(CourseLearningControllerIntegrationTest.TestSecurityConfig.class)
class CourseLearningControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseLearningProgressService courseLearningProgressService;

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
    void upgradeToActiveRevision_withAuthenticatedUser_returnsOkAndDelegatesUserIdFromJwt() throws Exception {
        long courseId = 12L;
        long userId = 7L;

        when(courseLearningProgressService.upgradeToActiveRevision(courseId, userId))
                .thenReturn(CourseLearningRevisionInfoDTO.builder()
                        .courseId(courseId)
                        .userId(userId)
                        .learningRevisionId(302L)
                        .activeRevisionId(302L)
                        .latestRevisionId(302L)
                        .upgradePolicy("MANUAL")
                        .hasNewerRevision(false)
                        .build());

        mockMvc.perform(post("/api/course-learning/courses/{courseId}/upgrade-to-active", courseId)
                        .with(jwtWithRole(userId, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value(12))
                .andExpect(jsonPath("$.userId").value(7))
                .andExpect(jsonPath("$.learningRevisionId").value(302))
                .andExpect(jsonPath("$.hasNewerRevision").value(false));

        verify(courseLearningProgressService).upgradeToActiveRevision(courseId, userId);
    }

    @Test
    void upgradeToActiveRevision_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/course-learning/courses/{courseId}/upgrade-to-active", 12L))
                .andExpect(status().isUnauthorized());

        verify(courseLearningProgressService, never()).upgradeToActiveRevision(anyLong(), anyLong());
    }

    private RequestPostProcessor jwtWithRole(Long userId, String role) {
        return jwt().jwt(j -> {
                    j.claim("userId", String.valueOf(userId));
                    j.subject(String.valueOf(userId));
                })
                .authorities(new SimpleGrantedAuthority(role));
    }
}

