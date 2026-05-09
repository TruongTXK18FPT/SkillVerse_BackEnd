package com.exe.skillverse_backend.career_taxonomy_service.dto;

import com.exe.skillverse_backend.career_taxonomy_service.enums.TaxonomyStatus;
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
    private String name;
    private String description;
    private TaxonomyStatus status;
}
