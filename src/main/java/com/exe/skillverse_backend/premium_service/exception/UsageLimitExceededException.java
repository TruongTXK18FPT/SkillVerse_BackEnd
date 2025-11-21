package com.exe.skillverse_backend.premium_service.exception;

import com.exe.skillverse_backend.premium_service.dto.response.UsageCheckResult;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import lombok.Getter;

/**
 * Exception thrown when user exceeds their usage limit for a feature
 */
@Getter
public class UsageLimitExceededException extends RuntimeException {

    private final FeatureType featureType;
    private final UsageCheckResult checkResult;

    public UsageLimitExceededException(String message, FeatureType featureType, UsageCheckResult checkResult) {
        super(message);
        this.featureType = featureType;
        this.checkResult = checkResult;
    }

    public UsageLimitExceededException(String message, FeatureType featureType) {
        super(message);
        this.featureType = featureType;
        this.checkResult = null;
    }

    /**
     * Create exception with Vietnamese message from check result
     */
    public static UsageLimitExceededException fromCheckResult(FeatureType featureType, UsageCheckResult checkResult) {
        return new UsageLimitExceededException(checkResult.getReasonVi(), featureType, checkResult);
    }
}
