package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateRecruitmentSessionRequest;
import com.exe.skillverse_backend.business_service.dto.request.SendRecruitmentMessageRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateRecruitmentStatusRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentMessageResponse;
import com.exe.skillverse_backend.business_service.dto.response.RecruitmentSessionResponse;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentJobContextType;
import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface cho recruitment chat giữa recruiter và candidate
 */
public interface RecruitmentChatService {

    /**
     * Tạo mới một recruitment session
     * Nếu đã tồn tại session với cùng recruiter-candidate-job thì trả về session cũ
     */
    RecruitmentSessionResponse createSession(Long recruiterId, CreateRecruitmentSessionRequest request);

    /**
     * Lấy session by ID
     */
    RecruitmentSessionResponse getSessionById(Long userId, Long sessionId);

    /**
     * Lấy tất cả session của recruiter (với phân trang)
     */
    Page<RecruitmentSessionResponse> getRecruiterSessions(Long recruiterId, Pageable pageable);

    /**
     * Lấy tất cả session của candidate (với phân trang)
     */
    Page<RecruitmentSessionResponse> getCandidateSessions(Long candidateId, Pageable pageable);

    /**
     * Tìm kiếm session của recruiter
     */
    Page<RecruitmentSessionResponse> searchRecruiterSessions(Long recruiterId, String query, Pageable pageable);

    /**
     * Lấy session theo job
     */
    java.util.List<RecruitmentSessionResponse> getSessionsByJob(Long recruiterId, Long jobId, RecruitmentJobContextType jobContextType);

    /**
     * Gửi tin nhắn trong session
     */
    RecruitmentMessageResponse sendMessage(Long userId, SendRecruitmentMessageRequest request);

    /**
     * Lấy tin nhắn của một session (với phân trang)
     */
    Page<RecruitmentMessageResponse> getSessionMessages(Long userId, Long sessionId, Pageable pageable);

    /**
     * Đánh dấu tin nhắn là đã đọc
     */
    void markMessagesAsRead(Long userId, Long sessionId);

    /**
     * Cập nhật trạng thái session
     */
    RecruitmentSessionResponse updateSessionStatus(Long userId, Long sessionId, UpdateRecruitmentStatusRequest request);

    /**
     * Archive/hide session
     */
    void archiveSession(Long userId, Long sessionId);

    /**
     * Xóa session (soft delete - archive)
     */
    void deleteSession(Long userId, Long sessionId);

    /**
     * Đếm số tin nhắn chưa đọc của user
     */
    long getUnreadCount(Long userId);

    /**
     * Kiểm tra và tạo session nếu chưa tồn tại
     * Trả về session có sẵn hoặc mới tạo
     */
    RecruitmentSessionResponse getOrCreateSession(Long recruiterId, Long candidateId, Long jobId,
                                                  RecruitmentSessionSource source,
                                                  RecruitmentJobContextType jobContextType);
}
