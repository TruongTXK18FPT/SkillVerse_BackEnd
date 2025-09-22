package com.exe.skillverse_backend.shared.dto;

import lombok.*;
import java.time.LocalDateTime;

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

