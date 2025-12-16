package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.admin_service.dto.request.ApplicationActionRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminApprovalResponse;
import com.exe.skillverse_backend.admin_service.dto.response.ApplicationsResponse;

public interface AdminApprovalService {
    
    AdminApprovalResponse approveMentor(Long userId, Long adminId);
    
    AdminApprovalResponse approveRecruiter(Long userId, Long adminId);
    
    AdminApprovalResponse rejectMentor(Long userId, String rejectionReason, Long adminId);
    
    AdminApprovalResponse rejectRecruiter(Long userId, String rejectionReason, Long adminId);
    
    AdminApprovalResponse processApplication(ApplicationActionRequest request, Long adminId);
    
    ApplicationsResponse getApplications(String status);
}
