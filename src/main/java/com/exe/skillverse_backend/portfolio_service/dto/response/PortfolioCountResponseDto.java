package com.exe.skillverse_backend.portfolio_service.dto.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioCountResponseDto {
    private long totalCount;
    private long projectsCount;
    private long productsCount;
    private long certificatesCount;
}