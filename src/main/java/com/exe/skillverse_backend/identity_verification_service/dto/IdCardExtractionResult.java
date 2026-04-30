package com.exe.skillverse_backend.identity_verification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdCardExtractionResult {
    private String idNumber;
    private String fullName;
    private String dob;
    private String sex;
    private String nationality;
    private String placeOfOrigin;
    private String placeOfResidence;
    private String expiryDate;
    
    // For back side
    private String issueDate;
    private String issueLoc;
    
    // Extracted type: "front" or "back"
    private String cardType;
    
    // Raw JSON from FPT.AI to store in DB for admin to cross-check if needed
    private String rawJson;
    
    // Status
    private boolean success;
    private String errorMessage;
}
