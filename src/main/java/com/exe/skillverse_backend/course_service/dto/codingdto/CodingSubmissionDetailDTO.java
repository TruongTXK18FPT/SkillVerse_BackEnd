package com.exe.skillverse_backend.course_service.dto.codingdto;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor 
public class CodingSubmissionDetailDTO {
    //Long id, Long userId, String userName, String status, BigDecimal score, String feedback, Instant submittedAt
    private Long id;
    private Long userId;
    private String userName;
    private String status;
    private BigDecimal score;
    private String feedback;
    private Instant submittedAt;
}
