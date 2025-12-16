package com.exe.skillverse_backend.mentor_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BadgeInfo {
    private String code;
    private String name;
    private String description;
    private int progressCurrent;
    private int progressTarget;
    private boolean earned;
}
