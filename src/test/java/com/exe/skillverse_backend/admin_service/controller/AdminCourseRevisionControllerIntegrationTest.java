package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDTO;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.service.CourseRevisionService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCourseRevisionController.class)
@AutoConfigureMockMvc
@Import(AdminCourseRevisionControllerIntegrationTest.TestSecurityConfig.class)
class AdminCourseRevisionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    void listRevisionQueue_withAdminRole_returnsOk() throws Exception {
        when(courseRevisionService.listAdminRevisions(eq(CourseRevisionStatus.PENDING), any())).thenReturn(
                PageResponse.<CourseRevisionDTO>builder()
                        .items(java.util.List.of(CourseRevisionDTO.builder()
                                .id(201L)
                                .courseId(99L)
                                .revisionNumber(2)
                                .status(CourseRevisionStatus.PENDING)
                                .build()))
                        .page(0)
                        .size(20)
                        .total(1)
                        .build()
        );

        mockMvc.perform(get("/api/admin/course-revisions")
                        .with(jwt().jwt(j -> {
                                    j.claim("userId", "3");
                                    j.subject("3");
                                })
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(201))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));

        verify(courseRevisionService).listAdminRevisions(eq(CourseRevisionStatus.PENDING), any());
    }

    @Test
    void approveRevision_withAdminRole_returnsOk() throws Exception {
        long revisionId = 101L;
        long adminId = 3L;
        when(courseRevisionService.approveRevision(revisionId, adminId)).thenReturn(CourseRevisionDTO.builder()
                .id(revisionId)
                .courseId(11L)
                .revisionNumber(2)
                .status(CourseRevisionStatus.APPROVED)
                .autoUpgradeOutcome("SKIPPED")
                .autoUpgradeAffectedEnrollments(0)
                .autoUpgradeReasonCode("ITEM_RULE_CHANGED")
                .autoUpgradeReasonDetail("quiz:88/passScore")
                .build());

        mockMvc.perform(post("/api/admin/course-revisions/{revisionId}/approve", revisionId)
                        .with(jwt().jwt(j -> {
                                    j.claim("userId", String.valueOf(adminId));
                                    j.subject(String.valueOf(adminId));
                                })
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.autoUpgradeOutcome").value("SKIPPED"))
                .andExpect(jsonPath("$.autoUpgradeReasonCode").value("ITEM_RULE_CHANGED"))
                .andExpect(jsonPath("$.autoUpgradeReasonDetail").value("quiz:88/passScore"));

        verify(courseRevisionService).approveRevision(revisionId, adminId);
    }

    @Test
    void rejectRevision_withAdminRole_returnsOk() throws Exception {
        long revisionId = 102L;
        long adminId = 3L;
        when(courseRevisionService.rejectRevision(revisionId, adminId, "Need more details")).thenReturn(CourseRevisionDTO.builder()
                .id(revisionId)
                .courseId(11L)
                .revisionNumber(2)
                .status(CourseRevisionStatus.REJECTED)
                .rejectionReason("Need more details")
                .build());

        mockMvc.perform(post("/api/admin/course-revisions/{revisionId}/reject", revisionId)
                        .param("reason", "Need more details")
                        .with(jwt().jwt(j -> {
                                    j.claim("userId", String.valueOf(adminId));
                                    j.subject(String.valueOf(adminId));
                                })
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(102))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(courseRevisionService).rejectRevision(revisionId, adminId, "Need more details");
    }

    @Test
    void approveRevision_withUserRole_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/course-revisions/{revisionId}/approve", 101L)
                .with(jwt().jwt(j -> {
                                    j.claim("userId", "7");
                                    j.subject("7");
                                })
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());

        verify(courseRevisionService, never()).approveRevision(101L, 7L);
    }
}
