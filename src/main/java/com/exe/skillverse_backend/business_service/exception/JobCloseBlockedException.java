package com.exe.skillverse_backend.business_service.exception;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when a recruiter tries to close a job but there are still unresolved applicants
 * or pending contracts. Carries a list of {@link JobFlowBlockingItem} so the client can
 * display exactly what needs to be done before closing.
 */
public class JobCloseBlockedException extends RuntimeException {

    private final List<JobFlowBlockingItem> blockingItems;

    public JobCloseBlockedException(String message, List<JobFlowBlockingItem> blockingItems) {
        super(message);
        this.blockingItems = blockingItems == null ? Collections.emptyList() : List.copyOf(blockingItems);
    }

    public List<JobFlowBlockingItem> getBlockingItems() {
        return blockingItems;
    }
}
