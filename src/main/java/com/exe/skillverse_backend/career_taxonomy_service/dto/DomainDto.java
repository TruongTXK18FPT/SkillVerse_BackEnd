package com.exe.skillverse_backend.career_taxonomy_service.dto;

import com.exe.skillverse_backend.career_taxonomy_service.enums.TaxonomyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainDto {
    private Long id;
    private String code;

    @NotBlank(message = "Tên hiển thị không được để trống")
    @Size(max = 255, message = "Tên hiển thị không được vượt quá 255 ký tự")
    private String name;

    private String description;
    private TaxonomyStatus status;
}

