package com.exe.skillverse_backend.admin_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for admin user list response with statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserListResponse {
    private List<AdminUserResponse> users;
    private Long totalUsers;
    private Long totalMentors;
    private Long totalRecruiters;
    private Long totalRegularUsers;
    private Long totalActiveUsers;
    private Long totalInactiveUsers;
}
