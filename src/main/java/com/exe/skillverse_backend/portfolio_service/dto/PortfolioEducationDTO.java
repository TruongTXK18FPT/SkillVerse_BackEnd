package com.exe.skillverse_backend.portfolio_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
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
public class PortfolioEducationDTO {
    private String id;
    @NotBlank(message = "Tên trường/tổ chức là bắt buộc.")
    private String institution;
    @NotBlank(message = "Bằng cấp/chương trình là bắt buộc.")
    private String degree;
    private String fieldOfStudy;
    private String location;
    @NotBlank(message = "Năm bắt đầu là bắt buộc.")
    private String startDate;
    private String endDate;
    private String status;
    private String description;
    private String gpa;
    private List<String> relevantCourses;

    @AssertTrue(message = "Năm kết thúc là bắt buộc khi trạng thái là 'Đã tốt nghiệp'.")
    public boolean hasEndDateWhenGraduated() {
        if ("GRADUATED".equals(status)) {
            return endDate != null && !endDate.trim().isEmpty();
        }
        return true;
    }
}
