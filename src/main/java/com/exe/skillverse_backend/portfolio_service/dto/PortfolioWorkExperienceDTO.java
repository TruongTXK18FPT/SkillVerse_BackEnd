package com.exe.skillverse_backend.portfolio_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
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
    @NotBlank(message = "Ngày bắt đầu là bắt buộc.")
    private String startDate;
    private String endDate;
    private Boolean currentJob;
    private String description;

    @AssertTrue(message = "Ít nhất tên công ty hoặc vị trí là bắt buộc.")
    public boolean hasCompanyOrPosition() {
        return (companyName != null && !companyName.trim().isEmpty()) ||
               (position != null && !position.trim().isEmpty());
    }

    @AssertTrue(message = "Ngày kết thúc là bắt buộc khi không đánh dấu 'Đang làm việc tại đây'.")
    public boolean hasEndDateWhenNotCurrent() {
        if (currentJob != null && currentJob) {
            return true;
        }
        return endDate != null && !endDate.trim().isEmpty();
    }
}
