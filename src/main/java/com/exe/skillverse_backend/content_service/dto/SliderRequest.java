package com.exe.skillverse_backend.content_service.dto;

import com.exe.skillverse_backend.shared.validation.ValidationGroups;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SliderRequest {
    @NotBlank(message = "Title is required", groups = ValidationGroups.Create.class)
    @Size(min = 3, max = 100, message = "Title must be between 3 and 100 characters")
    private String title;

    private String description;

    // Used for create/update where image might be optional in update
    private MultipartFile image;

    private Integer displayOrder;

    private Boolean isActive;

    @Size(max = 50, message = "CTA Text must not exceed 50 characters")
    private String ctaText;

    private String ctaLink;
}
