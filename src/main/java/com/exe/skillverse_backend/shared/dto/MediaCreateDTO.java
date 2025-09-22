package com.exe.skillverse_backend.shared.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaCreateDTO {
    private String url;
    private String type;
    private String fileName;
    private Long fileSize;
    private Long uploadedBy;
}

