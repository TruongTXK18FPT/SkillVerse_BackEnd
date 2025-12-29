package com.exe.skillverse_backend.parent_service.dto.request;

import com.exe.skillverse_backend.parent_service.entity.enums.LinkStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLinkStatusRequest {
    private LinkStatus status;
}
