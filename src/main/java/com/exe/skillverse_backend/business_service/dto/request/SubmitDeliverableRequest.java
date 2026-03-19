package com.exe.skillverse_backend.business_service.dto.request;

import com.exe.skillverse_backend.business_service.entity.enums.DeliverableType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request để submit deliverables (bàn giao công việc)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitDeliverableRequest {

    @NotNull(message = "Application ID is required")
    private Long applicationId;

    private Long milestoneId;

    @Size(max = 5000, message = "Work note must not exceed 5000 characters")
    private String workNote;

    private List<DeliverableItem> deliverables;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliverableItem {
        
        @NotNull(message = "Deliverable type is required")
        private DeliverableType type;
        
        @NotBlank(message = "File name is required")
        @Size(max = 255, message = "File name must not exceed 255 characters")
        private String fileName;
        
        @NotBlank(message = "File URL is required")
        private String fileUrl;
        
        private Long fileSize;
        
        @Size(max = 100, message = "MIME type must not exceed 100 characters")
        private String mimeType;
        
        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        private String description;
    }
}
