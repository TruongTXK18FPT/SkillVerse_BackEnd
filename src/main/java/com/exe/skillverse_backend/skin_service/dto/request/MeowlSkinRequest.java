package com.exe.skillverse_backend.skin_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class MeowlSkinRequest {
    @NotBlank
    private String skinCode;

    @NotBlank
    private String name;

    @NotBlank
    private String nameVi;

    @NotNull
    private Boolean isPremium;

    @NotNull
    private BigDecimal price;
    
    // File will be handled separately in controller as RequestPart or ModelAttribute
    // But usually for DTO we might not include MultipartFile if using @RequestBody, 
    // but with @ModelAttribute we can.
    // I will exclude it here and pass it as a separate argument in the controller or include it here if using ModelAttribute.
    // Let's assume ModelAttribute.
    private MultipartFile file;
}
