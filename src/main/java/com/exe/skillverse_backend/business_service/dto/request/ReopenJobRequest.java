package com.exe.skillverse_backend.business_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReopenJobRequest {
    private LocalDate deadline;
    private Boolean clearApplications = true; // Default to clearing applications
}
