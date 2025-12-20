package com.exe.skillverse_backend.skin_service.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class MeowlSkinResponse {
    private Long id;
    private String skinCode;
    private String name;
    private String nameVi;
    private String imageUrl;
    private boolean isPremium;
    private BigDecimal price;
    private boolean isOwned;
    private boolean isSelected;
}
