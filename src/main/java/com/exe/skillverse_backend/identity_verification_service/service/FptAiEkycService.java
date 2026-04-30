package com.exe.skillverse_backend.identity_verification_service.service;

import com.exe.skillverse_backend.identity_verification_service.dto.IdCardExtractionResult;
import org.springframework.web.multipart.MultipartFile;

public interface FptAiEkycService {
    /**
     * Extracts information from a Vietnamese ID Card (CCCD) image using FPT.AI eKYC API
     * @param image The ID card image (front or back)
     * @return Extracted information including raw JSON
     */
    IdCardExtractionResult extractIdCardInfo(MultipartFile image);
    
    /**
     * Extracts information from a Vietnamese ID Card (CCCD) image using FPT.AI eKYC API
     * @param imageBytes The ID card image bytes
     * @param filename The original filename
     * @return Extracted information including raw JSON
     */
    IdCardExtractionResult extractIdCardInfo(byte[] imageBytes, String filename);
}
