package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.business_service.entity.JobContract;
import com.exe.skillverse_backend.business_service.enums.ContractStatus;
import com.exe.skillverse_backend.business_service.repository.JobContractRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContractExpirationScheduler {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int SIGNING_WINDOW_HOURS = 72;

    private final JobContractRepository contractRepository;
    private final NotificationService notificationService;

    /**
     * Runs every 5 minutes to expire contracts that have been waiting
     * for signature longer than 72 hours.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void expireStaleContracts() {
        LocalDateTime cutoff = LocalDateTime.now(VN_ZONE).minusHours(SIGNING_WINDOW_HOURS);
        List<ContractStatus> pendingStatuses = Arrays.asList(
                ContractStatus.PENDING_SIGNER,
                ContractStatus.PENDING_EMPLOYER
        );

        List<JobContract> expiredContracts = contractRepository
                .findByStatusInAndUpdatedAtBefore(pendingStatuses, cutoff);

        if (expiredContracts.isEmpty()) {
            return;
        }

        log.info("Expiring {} stale contracts", expiredContracts.size());

        for (JobContract contract : expiredContracts) {
            contract.setStatus(ContractStatus.CANCELLED);
            contractRepository.save(contract);

            // Notify both parties
            notificationService.createNotification(
                    contract.getEmployerId(),
                    "Hợp đồng đã hết hạn ký",
                    "Hợp đồng '" + contract.getJobTitle() + "' đã hết hạn ký (72 giờ) và không còn hiệu lực.",
                    NotificationType.CONTRACT_EXPIRED,
                    contract.getId().toString()
            );
            notificationService.createNotification(
                    contract.getCandidateId(),
                    "Hợp đồng đã hết hạn ký",
                    "Hợp đồng '" + contract.getJobTitle() + "' đã hết hạn ký (72 giờ) và không còn hiệu lực.",
                    NotificationType.CONTRACT_EXPIRED,
                    contract.getId().toString()
            );
        }
    }
}
