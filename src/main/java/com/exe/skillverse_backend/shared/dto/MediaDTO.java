package com.exe.skillverse_backend.shared.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaDTO {
    private Long id;
    private String url;
    private String type;       // IMAGE, VIDEO, DOCUMENT, AUDIO...
    private String fileName;
    private Long fileSize;     // bytes
    private Long uploadedBy;   // chỉ expose id của user
    private String uploadedByName; // tuỳ chọn: lấy firstName + lastName
    private LocalDateTime uploadedAt;
}

