package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateDTO;
import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateVerificationDTO;
import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningStatusDTO;
import java.util.Optional;

public interface CertificateService {

    CertificateDTO issueCourseCertificate(Long courseId, Long userId, CourseLearningStatusDTO completionStatus);

    CertificateDTO getUserCertificate(Long certificateId, Long userId);

    CertificateVerificationDTO getCertificateVerification(String serial);

    Optional<CertificateDTO> findUserCourseCertificate(Long courseId, Long userId);

    Optional<CertificateDTO> findActiveUserCourseCertificate(Long courseId, Long userId);
}
