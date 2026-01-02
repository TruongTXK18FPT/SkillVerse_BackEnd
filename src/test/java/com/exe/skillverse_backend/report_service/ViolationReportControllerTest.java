package com.exe.skillverse_backend.report_service;

import com.exe.skillverse_backend.report_service.controller.ViolationReportController;
import com.exe.skillverse_backend.report_service.dto.request.CreateViolationReportRequest;
import com.exe.skillverse_backend.report_service.dto.request.UpdateViolationReportRequest;
import com.exe.skillverse_backend.report_service.dto.response.ViolationReportResponse;
import com.exe.skillverse_backend.report_service.dto.response.ViolationReportStatsResponse;
import com.exe.skillverse_backend.report_service.service.ViolationReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for ViolationReportController
 */
@WebMvcTest(ViolationReportController.class)
@AutoConfigureMockMvc
class ViolationReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ViolationReportService reportService;

    @Autowired
    private ObjectMapper objectMapper;

    private ViolationReportResponse mockResponse;
    private CreateViolationReportRequest createRequest;

    @BeforeEach
    void setUp() {
        mockResponse = ViolationReportResponse.builder()
                .id(1L)
                .reportCode("RPT-TEST1234")
                .title("Test Report")
                .reporterId(1L)
                .reporterName("Reporter User")
                .reporterEmail("reporter@test.com")
                .reportedUserId(2L)
                .reportedUserName("Reported User")
                .reportedUserEmail("reported@test.com")
                .reportType("HARASSMENT")
                .severity("HIGH")
                .description("This is a test violation report.")
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        createRequest = CreateViolationReportRequest.builder()
                .title("Test Report")
                .reportedUserId(2L)
                .reportType("HARASSMENT")
                .severity("HIGH")
                .description("This is a test violation report with sufficient detail.")
                .build();
    }

    @Nested
    @DisplayName("User Endpoints Tests")
    class UserEndpointTests {

        @Test
        @DisplayName("Should create report when authenticated")
        @WithMockUser(username = "user@test.com", roles = {"USER"})
        void shouldCreateReportWhenAuthenticated() throws Exception {
            when(reportService.createReport(anyLong(), any(CreateViolationReportRequest.class)))
                    .thenReturn(mockResponse);

            mockMvc.perform(post("/api/v1/reports")
                            .with(csrf())
                            .param("reporterId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reportCode").value("RPT-TEST1234"));
        }

        @Test
        @DisplayName("Should get my reports when authenticated")
        @WithMockUser(username = "user@test.com", roles = {"USER"})
        void shouldGetMyReportsWhenAuthenticated() throws Exception {
            when(reportService.getReportsByReporter(1L))
                    .thenReturn(Arrays.asList(mockResponse));

            mockMvc.perform(get("/api/v1/reports/my")
                            .param("userId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].reportCode").value("RPT-TEST1234"));
        }

        @Test
        @DisplayName("Should get report by ID when authenticated")
        @WithMockUser(username = "user@test.com", roles = {"USER"})
        void shouldGetReportByIdWhenAuthenticated() throws Exception {
            when(reportService.getReportById(1L, 1L)).thenReturn(mockResponse);

            mockMvc.perform(get("/api/v1/reports/1")
                            .param("userId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("Should track report by code (public endpoint)")
        void shouldTrackReportByCode() throws Exception {
            when(reportService.getReportByCode("RPT-TEST1234")).thenReturn(mockResponse);

            mockMvc.perform(get("/api/v1/reports/track/RPT-TEST1234"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reportCode").value("RPT-TEST1234"));
        }

        @Test
        @DisplayName("Should return 401 when creating report without authentication")
        void shouldReturn401WhenCreatingReportWithoutAuthentication() throws Exception {
            mockMvc.perform(post("/api/v1/reports")
                            .with(csrf())
                            .param("reporterId", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Admin Endpoints Tests")
    class AdminEndpointTests {

        @Test
        @DisplayName("Should get all reports when admin")
        @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
        void shouldGetAllReportsWhenAdmin() throws Exception {
            Page<ViolationReportResponse> page = new PageImpl<>(
                    Arrays.asList(mockResponse), PageRequest.of(0, 20), 1);
            when(reportService.getAllReports(any(), any(), any(), any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/reports/admin/all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].reportCode").value("RPT-TEST1234"));
        }

        @Test
        @DisplayName("Should get report by ID when admin")
        @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
        void shouldGetReportByIdWhenAdmin() throws Exception {
            when(reportService.getReportByIdAdmin(1L)).thenReturn(mockResponse);

            mockMvc.perform(get("/api/v1/reports/admin/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("Should investigate report when admin")
        @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
        void shouldInvestigateReportWhenAdmin() throws Exception {
            mockResponse.setStatus("INVESTIGATING");
            when(reportService.investigateReport(1L, 3L)).thenReturn(mockResponse);

            mockMvc.perform(post("/api/v1/reports/admin/1/investigate")
                            .with(csrf())
                            .param("adminId", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INVESTIGATING"));
        }

        @Test
        @DisplayName("Should resolve report when admin")
        @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
        void shouldResolveReportWhenAdmin() throws Exception {
            mockResponse.setStatus("RESOLVED");
            mockResponse.setResolutionAction("WARNING_ISSUED");
            when(reportService.resolveReport(anyLong(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            Map<String, String> body = new HashMap<>();
            body.put("resolutionAction", "WARNING_ISSUED");
            body.put("adminNotes", "User warned");

            mockMvc.perform(post("/api/v1/reports/admin/1/resolve")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("RESOLVED"));
        }

        @Test
        @DisplayName("Should dismiss report when admin")
        @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
        void shouldDismissReportWhenAdmin() throws Exception {
            mockResponse.setStatus("DISMISSED");
            when(reportService.dismissReport(anyLong(), anyString())).thenReturn(mockResponse);

            Map<String, String> body = new HashMap<>();
            body.put("adminNotes", "Not enough evidence");

            mockMvc.perform(post("/api/v1/reports/admin/1/dismiss")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DISMISSED"));
        }

        @Test
        @DisplayName("Should get report statistics when admin")
        @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
        void shouldGetReportStatisticsWhenAdmin() throws Exception {
            ViolationReportStatsResponse stats = ViolationReportStatsResponse.builder()
                    .totalReports(100L)
                    .newReports(10L)
                    .investigatingReports(5L)
                    .resolvedReports(70L)
                    .dismissedReports(15L)
                    .criticalReports(20L)
                    .reportsThisWeek(12L)
                    .responseRate(85.0)
                    .build();
            when(reportService.getReportStats()).thenReturn(stats);

            mockMvc.perform(get("/api/v1/reports/admin/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalReports").value(100))
                    .andExpect(jsonPath("$.responseRate").value(85.0));
        }

        @Test
        @DisplayName("Should return 403 when non-admin tries to access admin endpoint")
        @WithMockUser(username = "user@test.com", roles = {"USER"})
        void shouldReturn403WhenNonAdminTriesToAccessAdminEndpoint() throws Exception {
            mockMvc.perform(get("/api/v1/reports/admin/all"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Should delete report when admin")
        @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
        void shouldDeleteReportWhenAdmin() throws Exception {
            mockMvc.perform(delete("/api/v1/reports/admin/1")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should get critical reports when admin")
        @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
        void shouldGetCriticalReportsWhenAdmin() throws Exception {
            mockResponse.setSeverity("HIGH");
            when(reportService.getPendingCriticalReports())
                    .thenReturn(Arrays.asList(mockResponse));

            mockMvc.perform(get("/api/v1/reports/admin/critical"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].severity").value("HIGH"));
        }

        @Test
        @DisplayName("Should update report when admin")
        @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
        void shouldUpdateReportWhenAdmin() throws Exception {
            mockResponse.setStatus("INVESTIGATING");
            when(reportService.updateReport(anyLong(), any(UpdateViolationReportRequest.class)))
                    .thenReturn(mockResponse);

            UpdateViolationReportRequest updateRequest = UpdateViolationReportRequest.builder()
                    .status("INVESTIGATING")
                    .adminNotes("Under review")
                    .build();

            mockMvc.perform(put("/api/v1/reports/admin/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("INVESTIGATING"));
        }
    }
}
