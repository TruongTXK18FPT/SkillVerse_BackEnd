package com.exe.skillverse_backend.seminar_service.service.impl;

import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.seminar_service.dto.request.SeminarCreateRequest;
import com.exe.skillverse_backend.seminar_service.dto.request.SeminarUpdateRequest;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarAnalyticsDTO;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarResponse;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarRevenueReportDTO;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarTicketResponse;
import com.exe.skillverse_backend.seminar_service.dto.response.TopSpeakerDTO;
import com.exe.skillverse_backend.seminar_service.entity.Seminar;
import com.exe.skillverse_backend.seminar_service.entity.SeminarStatus;
import com.exe.skillverse_backend.seminar_service.entity.SeminarTicket;
import com.exe.skillverse_backend.seminar_service.repository.SeminarRepository;
import com.exe.skillverse_backend.seminar_service.repository.SeminarTicketRepository;
import com.exe.skillverse_backend.seminar_service.service.SeminarService;
import com.exe.skillverse_backend.seminar_service.validation.SeminarValidator;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataIntegrityViolationException;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeminarServiceImpl implements SeminarService {

    private final SeminarRepository seminarRepository;
    private final SeminarTicketRepository ticketRepository;
    private final WalletService walletService;
    private final UserProfileRepository userProfileRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final WalletTransactionRepository walletTransactionRepository;
    private final SeminarValidator seminarValidator;

    private static final BigDecimal RECRUITER_SHARE_PERCENTAGE = new BigDecimal("0.70");
    private static final BigDecimal PLATFORM_FEE_PERCENTAGE = new BigDecimal("0.30");

    /**
     * Helper method to normalize capacity value.
     * Converts 0 to null (unlimited capacity).
     */
    private Integer normalizeCapacity(Integer capacity) {
        return (capacity != null && capacity == 0) ? null : capacity;
    }

    @Override
    @Transactional
    public SeminarResponse createSeminar(SeminarCreateRequest request, MultipartFile image, String userId) {
        // Comprehensive validation (includes time range, price, capacity checks)
        seminarValidator.validateCreateRequest(request);

        // Handle image upload
        String imageUrl = request.getImageUrl();
        if (image != null && !image.isEmpty()) {
            try {
                Map<String, Object> uploadResult = cloudinaryService.uploadImage(image, "seminars");
                imageUrl = (String) uploadResult.get("secure_url");
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload seminar image", e);
            }
        }

        // Normalize input values
        BigDecimal price = request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO;
        Integer maxCapacity = normalizeCapacity(request.getMaxCapacity());

        Seminar seminar = Seminar.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .imageUrl(imageUrl)
                .meetingLink(request.getMeetingLink())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .price(price)
                .maxCapacity(maxCapacity)
                .ticketsSold(0)
                .status(SeminarStatus.DRAFT) // Default to DRAFT, user must submit later
                .creatorId(userId)
                .build();

        return mapToResponse(seminarRepository.save(seminar), userId);
    }

    @Override
    @Transactional
    public SeminarResponse updateSeminar(Long id, SeminarUpdateRequest request, String userId) {
        Seminar seminar = seminarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seminar not found"));

        if (!seminar.getCreatorId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized to update this seminar");
        }

        if (seminar.getStatus() != SeminarStatus.DRAFT) {
            throw new IllegalStateException(
                    "Cannot edit seminar in " + seminar.getStatus() + " status. Only DRAFT seminars can be edited.");
        }

        // Validate update request (includes time range, price checks)
        seminarValidator.validateUpdateRequest(request, seminar.getTicketsSold());

        // Update basic fields
        if (request.getTitle() != null)
            seminar.setTitle(request.getTitle());
        if (request.getDescription() != null)
            seminar.setDescription(request.getDescription());
        if (request.getImageUrl() != null)
            seminar.setImageUrl(request.getImageUrl());
        if (request.getMeetingLink() != null)
            seminar.setMeetingLink(request.getMeetingLink());
        if (request.getStartTime() != null)
            seminar.setStartTime(request.getStartTime());
        if (request.getEndTime() != null)
            seminar.setEndTime(request.getEndTime());

        // Update price with security check
        if (request.getPrice() != null) {
            // Security: Prevent price change if tickets already sold
            if (ticketRepository.existsBySeminar_Id(id)) {
                throw new IllegalStateException("Không thể thay đổi giá khi đã có người mua vé");
            }
            seminar.setPrice(request.getPrice());
        }

        // Update maxCapacity with validation
        if (request.getMaxCapacity() != null) {
            Integer newMaxCapacity = normalizeCapacity(request.getMaxCapacity());

            // Prevent reducing capacity below tickets already sold
            if (newMaxCapacity != null && seminar.getTicketsSold() > newMaxCapacity) {
                throw new IllegalStateException(
                        "Không thể giảm số lượng vé xuống dưới số vé đã bán (" + seminar.getTicketsSold() + ")");
            }
            seminar.setMaxCapacity(newMaxCapacity);
        }

        return mapToResponse(seminarRepository.save(seminar), userId);
    }

    @Override
    public SeminarResponse getSeminarById(Long id, String userId) {
        log.info("[getSeminarById] id={}, userId={}", id, userId);

        Seminar seminar = seminarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seminar not found"));

        log.info("[getSeminarById] Found seminar: creatorId={}, startTime={}, endTime={}",
                seminar.getCreatorId(), seminar.getStartTime(), seminar.getEndTime());

        // Security: Allow viewing if:
        // 1. Public status (ACCEPTED/OPEN/CLOSED)
        // 2. User is the creator
        // 3. User has bought a ticket
        boolean isCreator = userId != null && seminar.getCreatorId().equals(userId);
        boolean isTicketHolder = userId != null
                && ticketRepository.existsByUserIdAndSeminar_Id(userId, seminar.getId());

        log.info("[getSeminarById] isCreator={}, isTicketHolder={}", isCreator, isTicketHolder);

        boolean isPublicStatus = seminar.getStatus() == SeminarStatus.ACCEPTED
                || seminar.getStatus() == SeminarStatus.OPEN
                || seminar.getStatus() == SeminarStatus.CLOSED;

        if (!isPublicStatus && !isCreator && !isTicketHolder) {
            throw new IllegalArgumentException("Seminar not found");
        }

        return mapToResponse(seminar, userId);
    }

    @Override
    public SeminarResponse getSeminarByIdForAdmin(Long id) {
        Seminar seminar = seminarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seminar not found"));
        // Admin gets full access - use mapToResponseForAdmin
        return mapToResponseForAdmin(seminar);
    }

    @Override
    public Page<SeminarResponse> getAllSeminars(List<SeminarStatus> statuses, Pageable pageable, String userId) {
        Page<Seminar> page = seminarRepository.findByStatusIn(statuses, pageable);
        return page.map(s -> mapToResponse(s, userId));
    }

    @Override
    public Page<SeminarResponse> getMySeminars(String userId, Pageable pageable) {
        Page<Seminar> page = seminarRepository.findByCreatorId(userId, pageable);
        return page.map(s -> mapToResponse(s, userId));
    }

    @Override
    @Transactional
    public void submitSeminar(Long id, String userId) {
        Seminar seminar = seminarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seminar not found"));

        if (!seminar.getCreatorId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized to submit this seminar");
        }

        if (seminar.getStatus() != SeminarStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT seminars can be submitted for approval");
        }
        // Validate submission timing
        seminarValidator.validateSubmission(seminar.getStartTime());
        seminar.setStatus(SeminarStatus.PENDING);
        seminarRepository.save(seminar);
    }

    @Override
    @Transactional
    public void approveSeminar(Long id) {
        Seminar seminar = seminarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seminar not found"));
        if (seminar.getStatus() != SeminarStatus.PENDING) {
            throw new IllegalStateException("Seminar is not pending approval");
        }
        seminar.setStatus(SeminarStatus.ACCEPTED);
        seminarRepository.save(seminar);
    }

    @Override
    @Transactional
    public void rejectSeminar(Long id) {
        Seminar seminar = seminarRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Seminar not found"));
        if (seminar.getStatus() != SeminarStatus.PENDING) {
            throw new IllegalStateException("Seminar is not pending approval");
        }
        seminar.setStatus(SeminarStatus.REJECTED);
        seminarRepository.save(seminar);
    }

    @Override
    @Transactional
    public SeminarTicketResponse buyTicket(Long seminarId, String userId) {
        // Use pessimistic locking to prevent race conditions
        Seminar seminar = seminarRepository.findByIdWithLock(seminarId)
                .orElseThrow(() -> new IllegalArgumentException("Seminar not found"));

        // Validate purchase eligibility
        validateTicketPurchase(seminar, userId, seminarId);

        // Atomic capacity check and increment - this is the key protection against race
        // conditions
        // If capacity is exceeded, this returns 0 and we throw an exception
        int updated = seminarRepository.incrementTicketsSoldIfAvailable(seminarId);
        if (updated == 0) {
            throw new IllegalStateException("Hội thảo đã hết vé. Vui lòng thử lại sau hoặc chọn hội thảo khác.");
        }

        BigDecimal price = seminar.getPrice();
        boolean paymentSuccessful = false;

        try {
            if (price.compareTo(BigDecimal.ZERO) > 0) {
                // Deduct from User
                walletService.deductCash(
                        Long.valueOf(userId),
                        price,
                        "Purchase ticket for seminar: " + seminar.getTitle(),
                        WalletTransaction.TransactionType.SEMINAR_PURCHASE.name(),
                        "SEMINAR_" + seminarId);

                // Credit Recruiter (70%)
                BigDecimal recruiterShare = price.multiply(RECRUITER_SHARE_PERCENTAGE);
                walletService.payRecruiterForSeminar(
                        Long.valueOf(seminar.getCreatorId()),
                        recruiterShare,
                        seminarId);
            }
            paymentSuccessful = true;

            // Create ticket - database unique constraint will catch any duplicates that
            // slip through
            SeminarTicket ticket = SeminarTicket.builder()
                    .seminar(seminar)
                    .userId(userId)
                    .pricePaid(price)
                    .build();

            try {
                return mapToTicketResponse(ticketRepository.save(ticket));
            } catch (DataIntegrityViolationException e) {
                // Unique constraint violation - user already has a ticket
                // Rollback capacity increment
                seminarRepository.decrementTicketsSold(seminarId);
                throw new IllegalStateException("Bạn đã mua vé cho hội thảo này rồi");
            }

        } catch (Exception e) {
            // Rollback capacity increment if payment or ticket creation failed
            if (!paymentSuccessful || e instanceof DataIntegrityViolationException) {
                seminarRepository.decrementTicketsSold(seminarId);
            }
            throw e;
        }
    }

    /**
     * Validate if user can purchase a ticket for the seminar.
     * Performs all business rule validations before payment.
     */
    private void validateTicketPurchase(Seminar seminar, String userId, Long seminarId) {
        // Security: Prevent self-purchase (money laundering prevention)
        if (seminar.getCreatorId().equals(userId)) {
            throw new IllegalArgumentException("Bạn không thể mua vé cho hội thảo của chính mình");
        }

        // Check seminar status
        if (seminar.getStatus() != SeminarStatus.ACCEPTED && seminar.getStatus() != SeminarStatus.OPEN) {
            throw new IllegalStateException("Hội thảo chưa mở bán vé");
        }

        // Check if seminar has ended
        if (seminar.getEndTime().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Hội thảo đã kết thúc. Bạn không thể mua vé nữa.");
        }

        // Check if already bought (application-level check, database constraint is
        // backup)
        if (ticketRepository.findByUserIdAndSeminarId(userId, seminarId).isPresent()) {
            throw new IllegalStateException("Bạn đã mua vé cho hội thảo này rồi");
        }

        // Check capacity (application-level, atomic DB operation is the real
        // protection)
        if (seminar.isSoldOut()) {
            throw new IllegalStateException("Hội thảo đã hết vé");
        }
    }

    @Override
    public Page<SeminarTicketResponse> getMyTickets(String userId, Pageable pageable) {
        Page<SeminarTicket> ticketPage = ticketRepository.findByUserId(userId, pageable);
        return ticketPage.map(this::mapToTicketResponse);
    }

    @Override
    @Transactional
    public void updateExpiredSeminars() {
        // Find all seminars that are OPEN or ACCEPTED and end time has passed
        // This query should be optimized in repository, but for now we can iterate if
        // volume is low,
        // or add a method in repository.
        // Better: add findByStatusAndEndTimeBefore in Repository.

        // Let's add the method to Repository first or use JPQL here.
        // Using Repository is cleaner.

        // Assuming we add findByStatusInAndEndTimeBefore to repository.
        // For now, let's just fetch all OPEN/ACCEPTED and filter (not efficient for
        // large data, but ok for prototype)
        // Or write a custom update query.

        seminarRepository.updateStatusForExpiredSeminars(SeminarStatus.CLOSED, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void updateStartedSeminars() {
        // Chuyển ACCEPTED → OPEN khi đến giờ bắt đầu seminar
        // Điều kiện: startTime <= now AND endTime > now AND status = ACCEPTED
        seminarRepository.updateStatusForStartedSeminars(
                SeminarStatus.OPEN,
                SeminarStatus.ACCEPTED,
                LocalDateTime.now());
    }

    private SeminarResponse mapToResponse(Seminar seminar, String userId) {
        log.info("[mapToResponse] seminarId={}, userId={}, creatorId={}",
                seminar.getId(), userId, seminar.getCreatorId());

        boolean isOwned = false;
        if (userId != null) {
            if (seminar.getCreatorId().equals(userId)) {
                isOwned = true;
                log.info("[mapToResponse] isOwned=true (is creator)");
            } else {
                isOwned = ticketRepository.existsByUserIdAndSeminar_Id(userId, seminar.getId());
                log.info("[mapToResponse] Ticket check result: isOwned={}", isOwned);
            }
        } else {
            log.info("[mapToResponse] userId is null, isOwned=false");
        }

        String creatorName = "Recruiter";
        String creatorAvatar = "";
        try {
            Long creatorIdLong;
            try {
                creatorIdLong = Long.valueOf(seminar.getCreatorId());
            } catch (NumberFormatException e) {
                // creatorId might be an email
                var user = userRepository.findByEmail(seminar.getCreatorId());
                if (user.isPresent()) {
                    creatorIdLong = user.get().getId();
                } else {
                    throw e;
                }
            }

            // Always prioritize RecruiterProfile for company name if available
            var recruiterProfile = recruiterProfileRepository.findByUserId(creatorIdLong);
            if (recruiterProfile.isPresent()) {
                creatorName = recruiterProfile.get().getCompanyName();
                // Use user profile avatar if available, or default
                var profile = userProfileRepository.findByUserId(creatorIdLong);
                if (profile.isPresent() && profile.get().getAvatarMedia() != null) {
                    creatorAvatar = profile.get().getAvatarMedia().getUrl();
                }
            } else {
                // Fallback to UserProfile if RecruiterProfile is missing (should be rare for
                // seminars)
                var profile = userProfileRepository.findByUserId(creatorIdLong);
                if (profile.isPresent()) {
                    creatorName = profile.get().getFullName();
                    if (profile.get().getAvatarMedia() != null) {
                        creatorAvatar = profile.get().getAvatarMedia().getUrl();
                    }
                }
            }
        } catch (Exception e) {
            // Ignore error when fetching profile
        }

        return SeminarResponse.builder()
                .id(seminar.getId())
                .title(seminar.getTitle())
                .description(seminar.getDescription())
                .imageUrl(seminar.getImageUrl())
                .meetingLink(isOwned ? seminar.getMeetingLink() : null) // Security: Only show link to ticket owners
                .startTime(seminar.getStartTime())
                .endTime(seminar.getEndTime())
                .price(seminar.getPrice())
                .status(seminar.getStatus())
                .creatorId(seminar.getCreatorId())
                .creatorName(creatorName)
                .creatorAvatar(creatorAvatar)
                .isOwned(isOwned)
                .createdAt(seminar.getCreatedAt())
                .updatedAt(seminar.getUpdatedAt())
                // Capacity fields
                .maxCapacity(seminar.getMaxCapacity())
                .ticketsSold(seminar.getTicketsSold())
                .remainingCapacity(seminar.getRemainingCapacity())
                .isSoldOut(seminar.isSoldOut())
                .build();
    }

    /**
     * Admin version: Always includes meetingLink and full details
     */
    private SeminarResponse mapToResponseForAdmin(Seminar seminar) {
        String creatorName = "Recruiter";
        String creatorAvatar = "";
        try {
            Long creatorIdLong;
            try {
                creatorIdLong = Long.valueOf(seminar.getCreatorId());
            } catch (NumberFormatException e) {
                var user = userRepository.findByEmail(seminar.getCreatorId());
                if (user.isPresent()) {
                    creatorIdLong = user.get().getId();
                } else {
                    throw e;
                }
            }

            var recruiterProfile = recruiterProfileRepository.findByUserId(creatorIdLong);
            if (recruiterProfile.isPresent()) {
                creatorName = recruiterProfile.get().getCompanyName();
                var profile = userProfileRepository.findByUserId(creatorIdLong);
                if (profile.isPresent() && profile.get().getAvatarMedia() != null) {
                    creatorAvatar = profile.get().getAvatarMedia().getUrl();
                }
            } else {
                var profile = userProfileRepository.findByUserId(creatorIdLong);
                if (profile.isPresent()) {
                    creatorName = profile.get().getFullName();
                    if (profile.get().getAvatarMedia() != null) {
                        creatorAvatar = profile.get().getAvatarMedia().getUrl();
                    }
                }
            }
        } catch (Exception e) {
            // Ignore error
        }

        return SeminarResponse.builder()
                .id(seminar.getId())
                .title(seminar.getTitle())
                .description(seminar.getDescription())
                .imageUrl(seminar.getImageUrl())
                .meetingLink(seminar.getMeetingLink()) // Admin: Always show meetingLink
                .startTime(seminar.getStartTime())
                .endTime(seminar.getEndTime())
                .price(seminar.getPrice())
                .status(seminar.getStatus())
                .creatorId(seminar.getCreatorId())
                .creatorName(creatorName)
                .creatorAvatar(creatorAvatar)
                .isOwned(true) // Admin has full access
                .createdAt(seminar.getCreatedAt())
                .updatedAt(seminar.getUpdatedAt())
                // Capacity fields
                .maxCapacity(seminar.getMaxCapacity())
                .ticketsSold(seminar.getTicketsSold())
                .remainingCapacity(seminar.getRemainingCapacity())
                .isSoldOut(seminar.isSoldOut())
                .build();
    }

    private SeminarTicketResponse mapToTicketResponse(SeminarTicket ticket) {
        var seminar = ticket.getSeminar();
        return SeminarTicketResponse.builder()
                .id(ticket.getId())
                .seminarId(seminar.getId())
                .seminarTitle(seminar.getTitle())
                .seminarImageUrl(seminar.getImageUrl())
                .seminarStatus(seminar.getStatus())
                .seminarStartTime(seminar.getStartTime())
                .seminarEndTime(seminar.getEndTime())
                .meetingLink(seminar.getMeetingLink())
                .userId(ticket.getUserId())
                .pricePaid(ticket.getPricePaid())
                .purchasedAt(ticket.getPurchasedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SeminarRevenueReportDTO getSeminarRevenueReport(Long seminarId, String userId) {
        // Fetch seminar
        Seminar seminar = seminarRepository.findById(seminarId)
                .orElseThrow(() -> new IllegalArgumentException("Seminar not found"));

        // Verify ownership
        if (!seminar.getCreatorId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized: You can only view revenue for your own seminars");
        }

        // Get recruiter name
        String recruiterName = "Unknown";
        try {
            Long userIdLong = Long.parseLong(userId);
            var profile = userProfileRepository.findByUserId(userIdLong);
            if (profile.isPresent()) {
                recruiterName = profile.get().getFullName();
            }
        } catch (Exception e) {
            log.warn("Could not fetch recruiter name for userId: {}", userId);
        }

        // Fetch all tickets for this seminar
        List<SeminarTicket> tickets = ticketRepository.findAllBySeminarId(seminarId);
        Long totalTicketsSold = ticketRepository.countBySeminarId(seminarId);

        // Calculate revenue
        BigDecimal grossRevenue = seminar.getPrice().multiply(BigDecimal.valueOf(totalTicketsSold));
        BigDecimal platformFee = grossRevenue.multiply(PLATFORM_FEE_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netIncome = grossRevenue.multiply(RECRUITER_SHARE_PERCENTAGE).setScale(2, RoundingMode.HALF_UP);

        // Build ticket sale details
        List<SeminarRevenueReportDTO.TicketSaleDetail> ticketSales = new ArrayList<>();
        for (SeminarTicket ticket : tickets) {
            String buyerName = "Unknown User";
            String buyerEmail = "";
            try {
                Long buyerIdLong = Long.parseLong(ticket.getUserId());
                var buyerProfile = userProfileRepository.findByUserId(buyerIdLong);
                if (buyerProfile.isPresent()) {
                    buyerName = buyerProfile.get().getFullName();
                }
                var buyerUser = userRepository.findById(buyerIdLong);
                if (buyerUser.isPresent()) {
                    buyerEmail = buyerUser.get().getEmail();
                }
            } catch (Exception e) {
                log.warn("Could not fetch buyer info for ticket: {}", ticket.getId());
            }

            BigDecimal recruiterEarning = ticket.getPricePaid()
                    .multiply(RECRUITER_SHARE_PERCENTAGE)
                    .setScale(2, RoundingMode.HALF_UP);

            ticketSales.add(SeminarRevenueReportDTO.TicketSaleDetail.builder()
                    .ticketId(ticket.getId())
                    .buyerName(buyerName)
                    .buyerEmail(buyerEmail)
                    .purchasedAt(ticket.getPurchasedAt())
                    .pricePaid(ticket.getPricePaid())
                    .recruiterEarning(recruiterEarning)
                    .build());
        }

        // Fetch payout transactions
        List<WalletTransaction> payoutTransactions = walletTransactionRepository
                .findSeminarPayoutsBySeminarId(seminarId);
        List<SeminarRevenueReportDTO.PayoutDetail> payouts = new ArrayList<>();
        for (WalletTransaction tx : payoutTransactions) {
            payouts.add(SeminarRevenueReportDTO.PayoutDetail.builder()
                    .transactionId(tx.getTransactionId())
                    .payoutDate(tx.getCreatedAt())
                    .amount(tx.getCashAmount())
                    .transactionStatus(tx.getStatus().name())
                    .build());
        }

        // Build and return report
        return SeminarRevenueReportDTO.builder()
                .seminarId(seminar.getId())
                .seminarTitle(seminar.getTitle())
                .seminarImageUrl(seminar.getImageUrl())
                .ticketPrice(seminar.getPrice())
                .startTime(seminar.getStartTime())
                .endTime(seminar.getEndTime())
                .status(seminar.getStatus().name())
                .totalTicketsSold(totalTicketsSold.intValue())
                .grossRevenue(grossRevenue)
                .platformFee(platformFee)
                .netIncome(netIncome)
                .ticketSales(ticketSales)
                .payouts(payouts)
                .recruiterName(recruiterName)
                .reportGeneratedAt(LocalDateTime.now())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateSeminarRevenueInvoicePdf(Long seminarId, String userId) {
        SeminarRevenueReportDTO report = getSeminarRevenueReport(seminarId, userId);
        return generatePdfFromReport(report);
    }

    private byte[] generatePdfFromReport(SeminarRevenueReportDTO report) {
        try {
            Document document = new Document(PageSize.A4);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, baos);

            document.open();

            // Fonts
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

            // Title
            Paragraph title = new Paragraph("SEMINAR REVENUE REPORT", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Seminar Info
            document.add(new Paragraph("Seminar Information", headerFont));
            document.add(new Paragraph("Title: " + report.getSeminarTitle(), normalFont));
            document.add(new Paragraph("Price per ticket: " + report.getTicketPrice() + " VND", normalFont));
            document.add(new Paragraph("Start: " + formatDateTime(report.getStartTime()), normalFont));
            document.add(new Paragraph("End: " + formatDateTime(report.getEndTime()), normalFont));
            document.add(new Paragraph("Status: " + report.getStatus(), normalFont));
            document.add(new Paragraph(" ", normalFont));

            // Revenue Summary
            document.add(new Paragraph("Revenue Summary", headerFont));
            document.add(new Paragraph("Total Tickets Sold: " + report.getTotalTicketsSold(), normalFont));
            document.add(new Paragraph("Gross Revenue: " + report.getGrossRevenue() + " VND", normalFont));
            document.add(new Paragraph("Platform Fee (30%): " + report.getPlatformFee() + " VND", normalFont));
            document.add(new Paragraph("Net Income (70%): " + report.getNetIncome() + " VND", normalFont));
            document.add(new Paragraph(" ", normalFont));

            // Ticket Sales Table
            document.add(new Paragraph("Ticket Sales Details", headerFont));
            PdfPTable ticketTable = new PdfPTable(4);
            ticketTable.setWidthPercentage(100);
            ticketTable.setSpacingBefore(10);
            ticketTable.setSpacingAfter(10);

            ticketTable.addCell(new Phrase("Buyer", headerFont));
            ticketTable.addCell(new Phrase("Purchase Date", headerFont));
            ticketTable.addCell(new Phrase("Price Paid", headerFont));
            ticketTable.addCell(new Phrase("Your Earning", headerFont));

            for (SeminarRevenueReportDTO.TicketSaleDetail sale : report.getTicketSales()) {
                ticketTable.addCell(new Phrase(sale.getBuyerName(), normalFont));
                ticketTable.addCell(new Phrase(formatDateTime(sale.getPurchasedAt()), normalFont));
                ticketTable.addCell(new Phrase(sale.getPricePaid() + " VND", normalFont));
                ticketTable.addCell(new Phrase(sale.getRecruiterEarning() + " VND", normalFont));
            }
            document.add(ticketTable);

            // Payout History
            if (!report.getPayouts().isEmpty()) {
                document.add(new Paragraph("Payout History", headerFont));
                PdfPTable payoutTable = new PdfPTable(4);
                payoutTable.setWidthPercentage(100);
                payoutTable.setSpacingBefore(10);
                payoutTable.setSpacingAfter(10);

                payoutTable.addCell(new Phrase("Transaction ID", headerFont));
                payoutTable.addCell(new Phrase("Payout Date", headerFont));
                payoutTable.addCell(new Phrase("Amount", headerFont));
                payoutTable.addCell(new Phrase("Status", headerFont));

                for (SeminarRevenueReportDTO.PayoutDetail payout : report.getPayouts()) {
                    payoutTable.addCell(new Phrase(String.valueOf(payout.getTransactionId()), normalFont));
                    payoutTable.addCell(new Phrase(formatDateTime(payout.getPayoutDate()), normalFont));
                    payoutTable.addCell(new Phrase(payout.getAmount() + " VND", normalFont));
                    payoutTable.addCell(new Phrase(payout.getTransactionStatus(), normalFont));
                }
                document.add(payoutTable);
            }

            // Footer
            document.add(new Paragraph(" ", normalFont));
            document.add(new Paragraph("Recruiter: " + report.getRecruiterName(), normalFont));
            document.add(
                    new Paragraph("Report Generated: " + formatDateTime(report.getReportGeneratedAt()), normalFont));

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generating PDF for seminar revenue report", e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null)
            return "N/A";
        return dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    /**
     * Get public seminar analytics
     * Returns aggregate statistics (total, active, completed) and top 4 speakers
     * No authentication required - public endpoint for sidebar display
     */
    @Override
    @Transactional(readOnly = true)
    public SeminarAnalyticsDTO getAnalytics() {
        long startTime = System.currentTimeMillis();

        try {
            // Count seminars by status groups
            List<SeminarStatus> channelStatuses = List.of(
                    SeminarStatus.ACCEPTED,
                    SeminarStatus.OPEN,
                    SeminarStatus.CLOSED);
            Long totalSeminars = seminarRepository.countByStatusIn(channelStatuses);
            if (totalSeminars == null)
                totalSeminars = 0L;

            List<SeminarStatus> activeStatuses = List.of(
                    SeminarStatus.ACCEPTED,
                    SeminarStatus.OPEN);
            Long activeSeminars = seminarRepository.countByStatusIn(activeStatuses);
            if (activeSeminars == null)
                activeSeminars = 0L;

            Long completedSeminars = seminarRepository.countByStatusIn(
                    List.of(SeminarStatus.CLOSED));
            if (completedSeminars == null)
                completedSeminars = 0L;

            // Get top 4 speakers by tickets sold
            Pageable topFour = PageRequest.of(0, 4);
            List<Object[]> speakerStats = seminarRepository.findTopSpeakersByTicketsSold(topFour);

            List<TopSpeakerDTO> topSpeakers = speakerStats.stream()
                    .map(row -> {
                        // Handle potential null values from native query
                        // row[0] = creator_id (String), row[1] = ticket_count (Number)
                        String creatorIdStr = row[0] != null ? row[0].toString() : null;
                        Long ticketCount = row[1] != null ? ((Number) row[1]).longValue() : 0L;

                        if (creatorIdStr == null || creatorIdStr.isBlank()) {
                            log.warn("Found null or empty creatorId in speaker stats, skipping");
                            return null;
                        }

                        // Convert String creator_id to Long for RecruiterProfile lookup
                        Long creatorId;
                        try {
                            creatorId = Long.parseLong(creatorIdStr);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid creatorId format: {}, skipping", creatorIdStr);
                            return null;
                        }

                        // Get company name from RecruiterProfile with defensive null check
                        String companyName = "Unknown Company"; // Default fallback
                        try {
                            RecruiterProfile profile = recruiterProfileRepository.findByUserId(creatorId)
                                    .orElse(null);
                            if (profile != null && profile.getCompanyName() != null
                                    && !profile.getCompanyName().isBlank()) {
                                companyName = profile.getCompanyName();
                            }
                        } catch (Exception e) {
                            log.warn("Failed to fetch RecruiterProfile for creator {}: {}", creatorId, e.getMessage());
                        }

                        return TopSpeakerDTO.builder()
                                .creatorId(creatorIdStr)
                                .companyName(companyName)
                                .totalTicketsSold(ticketCount)
                                .build();
                    })
                    .filter(dto -> dto != null) // Remove nulls
                    .collect(Collectors.toList());

            long duration = System.currentTimeMillis() - startTime;
            if (duration > 500) {
                log.warn("⚠️ Slow analytics query detected: {}ms", duration);
            }

            return SeminarAnalyticsDTO.builder()
                    .totalSeminars(totalSeminars.intValue())
                    .activeSeminars(activeSeminars.intValue())
                    .completedSeminars(completedSeminars.intValue())
                    .topSpeakers(topSpeakers)
                    .build();

        } catch (Exception e) {
            log.error("Failed to fetch seminar analytics", e);
            throw new RuntimeException("Không thể tải thống kê seminar", e);
        }
    }
}