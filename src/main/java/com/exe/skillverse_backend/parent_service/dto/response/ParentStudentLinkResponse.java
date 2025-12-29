package com.exe.skillverse_backend.parent_service.dto.response;

import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.parent_service.entity.enums.LinkStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParentStudentLinkResponse {
    private Long id;
    private UserDto parent;
    private UserDto student;
    private LinkStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
