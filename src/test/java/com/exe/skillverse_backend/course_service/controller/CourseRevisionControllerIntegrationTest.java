package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDTO;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.service.CourseRevisionService;
import com.exe.skillverse_backend.course_service.service.CourseService;
import com.exe.skillverse_backend.course_service.service.impl.CourseRevisionDiffService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({CourseController.class, CourseRevisionController.class})
@AutoConfigureMockMvc
@Import(CourseRevisionControllerIntegrationTest.TestSecurityConfig.class)
class CourseRevisionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseService courseService;

    @MockBean
    private CourseRevisionService courseRevisionService;

        @MockBean
        private CourseRevisionDiffService courseRevisionDiffService;

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
    void createRevision_withMentorRole_returnsOk() throws Exception {
        long courseId = 10L;
        long actorId = 7L;

        when(courseRevisionService.createRevision(courseId, actorId)).thenReturn(CourseRevisionDTO.builder()
                .id(100L)
                .courseId(courseId)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .build());

        mockMvc.perform(post("/api/courses/{courseId}/revisions", courseId)
                        .with(jwtWithRole(actorId, "ROLE_MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(courseRevisionService).createRevision(courseId, actorId);
    }

    @Test
    void createRevision_withUserRole_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/courses/{courseId}/revisions", 10L)
                .with(jwtWithRole(7L, "ROLE_USER")))
                .andExpect(status().isForbidden());

        verify(courseRevisionService, never()).createRevision(10L, 7L);
    }

    @Test
    void listCourseRevisions_withMentorRole_returnsOk() throws Exception {
        long courseId = 10L;
        long actorId = 7L;

        when(courseRevisionService.listCourseRevisions(eq(courseId), eq(actorId), isNull(), any()))
                .thenReturn(PageResponse.<CourseRevisionDTO>builder()
                        .items(List.of(CourseRevisionDTO.builder()
                                .id(100L)
                                .courseId(courseId)
                                .revisionNumber(2)
                                .status(CourseRevisionStatus.DRAFT)
                                .build()))
                        .page(0)
                        .size(20)
                        .total(1)
                        .build());

        mockMvc.perform(get("/api/courses/{courseId}/revisions", courseId)
                        .with(jwtWithRole(actorId, "ROLE_MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(100))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"));

        verify(courseRevisionService).listCourseRevisions(eq(courseId), eq(actorId), isNull(), any());
    }

    @Test
    void getRevision_withMentorRole_returnsOk() throws Exception {
        long revisionId = 201L;
        long actorId = 7L;

        when(courseRevisionService.getRevision(revisionId, actorId)).thenReturn(CourseRevisionDTO.builder()
                .id(revisionId)
                .courseId(10L)
                .revisionNumber(2)
                .status(CourseRevisionStatus.DRAFT)
                .build());

        mockMvc.perform(get("/api/course-revisions/{revisionId}", revisionId)
                        .with(jwtWithRole(actorId, "ROLE_MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(201))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(courseRevisionService).getRevision(revisionId, actorId);
    }

    @Test
    void submitRevision_withMentorRole_returnsOk() throws Exception {
        long revisionId = 200L;
        long actorId = 7L;

        when(courseRevisionService.submitRevision(revisionId, actorId)).thenReturn(CourseRevisionDTO.builder()
                .id(revisionId)
                .courseId(10L)
                .revisionNumber(2)
                .status(CourseRevisionStatus.PENDING)
                .build());

        mockMvc.perform(post("/api/course-revisions/{revisionId}/submit", revisionId)
                        .with(jwtWithRole(actorId, "ROLE_MENTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(200))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(courseRevisionService).submitRevision(revisionId, actorId);
    }

    @Test
    void updateRevision_withMentorRole_returnsOk() throws Exception {
        long revisionId = 300L;
        long actorId = 7L;

        when(courseRevisionService.updateRevision(org.mockito.ArgumentMatchers.eq(revisionId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(actorId), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(CourseRevisionDTO.builder()
                        .id(revisionId)
                        .courseId(10L)
                        .revisionNumber(2)
                        .status(CourseRevisionStatus.DRAFT)
                        .title("Updated title")
                        .build());

        mockMvc.perform(put("/api/course-revisions/{revisionId}", revisionId)
                        .with(jwtWithRole(actorId, "ROLE_MENTOR"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\",\"description\":\"New desc\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(300))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.title").value("Updated title"));

        verify(courseRevisionService).updateRevision(org.mockito.ArgumentMatchers.eq(revisionId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(actorId), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void updateRevision_withUserRole_returnsForbidden() throws Exception {
        mockMvc.perform(put("/api/course-revisions/{revisionId}", 300L)
                        .with(jwtWithRole(7L, "ROLE_USER"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated title\"}"))
                .andExpect(status().isForbidden());

        verify(courseRevisionService, never()).updateRevision(org.mockito.ArgumentMatchers.eq(300L), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.isNull());
    }

    private RequestPostProcessor jwtWithRole(Long userId, String role) {
        return jwt().jwt(j -> {
                    j.claim("userId", String.valueOf(userId));
                    j.subject(String.valueOf(userId));
                })
                .authorities(new SimpleGrantedAuthority(role));
    }
}
