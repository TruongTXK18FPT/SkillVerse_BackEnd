package com.exe.skillverse_backend.parent_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinkStudentRequest {
    private String inviteCode; // Or student email if inviting directly
    private String studentEmail;
}
