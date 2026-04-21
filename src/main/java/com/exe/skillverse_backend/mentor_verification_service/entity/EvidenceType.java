package com.exe.skillverse_backend.mentor_verification_service.entity;

/**
 * [Nghiệp vụ] Loại bằng chứng mentor nộp kèm yêu cầu xác thực skill.
 * CERTIFICATE: chứng chỉ (link từ portfolio ExternalCertificate)
 * GITHUB: link GitHub repo/profile liên quan
 * PORTFOLIO_LINK: link portfolio/project cá nhân
 * WORK_EXPERIENCE: kinh nghiệm công việc liên quan
 */
public enum EvidenceType {
    CERTIFICATE,
    GITHUB,
    PORTFOLIO_LINK,
    WORK_EXPERIENCE
}
