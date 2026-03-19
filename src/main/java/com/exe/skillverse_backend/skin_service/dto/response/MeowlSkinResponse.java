package com.exe.skillverse_backend.skin_service.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MeowlSkinResponse {
    private Long id;
    private String skinCode;
    private String name;
    private String nameVi;
    private String imageUrl;
    
    @JsonProperty("isPremium")
    private boolean isPremium;
    
    private BigDecimal price;
    
    @JsonProperty("isOwned")
    private boolean isOwned;
    
    @JsonProperty("isSelected")
    private boolean isSelected;
    
    private long purchasedCount;
    private long usedCount;
}
