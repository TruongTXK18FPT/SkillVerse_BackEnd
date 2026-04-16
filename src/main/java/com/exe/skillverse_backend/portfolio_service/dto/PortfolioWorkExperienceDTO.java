package com.exe.skillverse_backend.portfolio_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class PortfolioWorkExperienceDTO {
    private String id;
    private String companyName;
    private String position;
    private String location;
    private String startDate;
    private String endDate;
    private Boolean currentJob;
    private String description;
}
