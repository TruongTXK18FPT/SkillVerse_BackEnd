package com.exe.skillverse_backend.payment_service.service;

import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingReviewRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service to generate PDF invoices for payments and wallet transactions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {
    
    private final UserProfileRepository userProfileRepository;
    private final BookingReviewRepository bookingReviewRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String COMPANY_NAME = "SkillVerse";
    private static final String COMPANY_ADDRESS = "Đại học FPT Hồ Chí Minh, Việt Nam";
    private static final String COMPANY_EMAIL = "support@skillverse.vn";
    private static final String LOGO_PATH = "c:/WorkSpace/EXE201/SkillVerse_BackEnd/src/assets/skillverse.png";

    /**
     * Generate PDF invoice for PaymentTransaction (Premium, Coin Purchase via PayOS)
     */
    public byte[] generatePaymentInvoice(PaymentTransaction payment) {
        return generatePaymentInvoice(payment, "USER");
    }

    /**
     * Generate payment invoice with role-based template
     */
    public byte[] generatePaymentInvoice(PaymentTransaction payment, String role) {
        String roleUpper = normalizeRole(role);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            addHeader(document);
            addInvoiceInfo(document, payment.getInternalReference(), payment.getCreatedAt());

            String customerName = getUserDisplayName(payment.getUser());
            String customerEmail = payment.getUser() != null ? payment.getUser().getEmail() : "N/A";
            addCustomerInfo(document, customerName, customerEmail);

            PdfPTable table = createTransactionTable();
            addTableRow(table,
                getPaymentTypeDescription(payment.getType()),
                payment.getDescription() != null ? payment.getDescription() : "-",
                payment.getAmount()
            );
            document.add(table);

            addTotal(document, payment.getAmount());

            String status = payment.getStatus() != null ? payment.getStatus().name() : "UNKNOWN";
            String method = payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : "UNKNOWN";
            addPaymentStatus(document, status, method);

            if ("ADMIN".equals(roleUpper)) {
                Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
                Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
                Paragraph adminHeader = new Paragraph("THÔNG TIN QUẢN TRỊ", labelFont);
                document.add(adminHeader);

                PdfPTable adminTable = new PdfPTable(2);
                adminTable.setWidthPercentage(100);
                adminTable.setWidths(new float[]{1, 2});

                addInfoRow(adminTable, "Mã giao dịch:", String.valueOf(payment.getId()), labelFont, valueFont);
                addInfoRow(adminTable, "Mã nội bộ:", payment.getInternalReference(), labelFont, valueFont);
                addInfoRow(adminTable, "Mã cổng thanh toán:", payment.getReferenceId() != null ? payment.getReferenceId() : "-", labelFont, valueFont);
                addInfoRow(adminTable, "User ID:", payment.getUser() != null ? String.valueOf(payment.getUser().getId()) : "-", labelFont, valueFont);
                addInfoRow(adminTable, "Loại:", getPaymentTypeDescription(payment.getType()), labelFont, valueFont);

                document.add(adminTable);
            }

            addFooter(document);
            document.close();
            log.info("Generated PDF invoice for payment: {} (role={})", payment.getInternalReference(), roleUpper);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating PDF invoice for payment {}: {}", payment.getId(), e.getMessage());
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }

    /**
     * Generate PDF invoice for WalletTransaction (Premium, Coin, Course via Wallet)
     */
    public byte[] generateWalletTransactionInvoice(WalletTransaction transaction) {
        return generateWalletTransactionInvoice(transaction, "USER");
    }

    /**
     * Generate wallet transaction invoice with role-based template
     */
    public byte[] generateWalletTransactionInvoice(WalletTransaction transaction, String role) {
        String roleUpper = normalizeRole(role);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            addHeader(document);

            String invoiceNo = "WAL-" + transaction.getTransactionId();
            addInvoiceInfo(document, invoiceNo, transaction.getCreatedAt());

            var user = transaction.getWallet().getUser();
            String customerName = getUserDisplayName(user);
            String customerEmail = user != null ? user.getEmail() : "N/A";
            addCustomerInfo(document, customerName, customerEmail);

            PdfPTable table = createTransactionTable();
            BigDecimal amount = transaction.getCashAmount() != null ? transaction.getCashAmount() : BigDecimal.ZERO;
            String typeDisplay = transaction.getTransactionType() != null ? transaction.getTransactionType().getDisplayName() : "Giao dịch ví";
            addTableRow(table, typeDisplay, transaction.getDescription() != null ? transaction.getDescription() : "-", amount);
            document.add(table);

            addTotal(document, amount);

            addPaymentStatus(document, transaction.getStatus().name(), "Wallet Balance");

            if ("ADMIN".equals(roleUpper) || "MENTOR".equals(roleUpper)) {
                Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
                Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

                Paragraph breakdownHeader = new Paragraph("CHI TIẾT PHÍ/HOA HỒNG", labelFont);
                document.add(breakdownHeader);

                PdfPTable breakdownTable = new PdfPTable(2);
                breakdownTable.setWidthPercentage(100);
                breakdownTable.setWidths(new float[]{1, 2});

                BigDecimal fee = transaction.getFee() != null ? transaction.getFee() : BigDecimal.ZERO;
                BigDecimal netAmount = amount.subtract(fee);

                addInfoRow(breakdownTable, "Giá gốc người dùng:", formatCurrency(amount), labelFont, valueFont);
                addInfoRow(breakdownTable, "Phí hệ thống:", formatCurrency(fee), labelFont, valueFont);
                addInfoRow(breakdownTable, "Thực nhận:", formatCurrency(netAmount), labelFont, valueFont);

                document.add(breakdownTable);
            }

            addFooter(document);
            document.close();
            log.info("Generated PDF invoice for wallet transaction: {} (role={})", invoiceNo, roleUpper);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating PDF invoice for wallet transaction {}: {}", transaction.getTransactionId(), e.getMessage());
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }

    /**
     * Generate PDF invoice for Mentor Booking
     */
    public byte[] generateBookingInvoice(Booking booking) {
        return generateBookingInvoice(booking, "USER");
    }

    /**
     * Generate booking invoice with role-based template
     */
    public byte[] generateBookingInvoice(Booking booking, String role) {
        String roleUpper = normalizeRole(role);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            addHeader(document);

            String invoiceNo = "BOOK-" + booking.getId();
            addInvoiceInfo(document, invoiceNo, booking.getStartTime());

            String customerName = getUserDisplayName(booking.getLearner());
            String customerEmail = booking.getLearner() != null ? booking.getLearner().getEmail() : "N/A";
            addCustomerInfo(document, customerName, customerEmail);

            PdfPTable table = createTransactionTable();
            BigDecimal amount = booking.getPriceVnd() != null ? booking.getPriceVnd() : BigDecimal.ZERO;
            String desc = String.format("Mentoring với %s (%d phút)",
                    getUserDisplayName(booking.getMentor()),
                    booking.getDurationMinutes() != null ? booking.getDurationMinutes() : 0);
            addTableRow(table, "Đặt lịch mentor", desc, amount);
            document.add(table);

            addTotal(document, amount);

            String status = booking.getStatus() != null ? booking.getStatus().name() : "UNKNOWN";
            addPaymentStatus(document, status, "Wallet/Payment");

            Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

            Paragraph sectionHeader = new Paragraph("CHI TIẾT BUỔI MENTORING", labelFont);
            document.add(sectionHeader);

            PdfPTable detailTable = new PdfPTable(2);
            detailTable.setWidthPercentage(100);
            detailTable.setWidths(new float[]{1, 2});

            String mentorName = getUserDisplayName(booking.getMentor());
            String link = booking.getMeetingLink() != null ? booking.getMeetingLink() : "-";
            String duration = (booking.getDurationMinutes() != null ? booking.getDurationMinutes() : 0) + " phút";

            addInfoRow(detailTable, "Mentor:", mentorName, labelFont, valueFont);
            addInfoRow(detailTable, "Link phòng:", link, labelFont, valueFont);
            addInfoRow(detailTable, "Thời lượng:", duration, labelFont, valueFont);

            try {
                var reviewOpt = bookingReviewRepository.findByBookingId(booking.getId());
                if (reviewOpt.isPresent()) {
                    var review = reviewOpt.get();
                    String ratingStr = review.getRating() != null ? review.getRating() + "/5" : "-";
                    addInfoRow(detailTable, "Đánh giá:", ratingStr, labelFont, valueFont);
                    if (review.getComment() != null && !review.getComment().isBlank()) {
                        addInfoRow(detailTable, "Nhận xét:", review.getComment(), labelFont, valueFont);
                    }
                    if (review.getReply() != null && !review.getReply().isBlank()) {
                        addInfoRow(detailTable, "Phản hồi mentor:", review.getReply(), labelFont, valueFont);
                    }
                }
            } catch (Exception e) {
                log.warn("Không thể tải đánh giá cho booking {}: {}", booking.getId(), e.getMessage());
            }

            document.add(detailTable);

            if ("ADMIN".equals(roleUpper) || "MENTOR".equals(roleUpper)) {
                Paragraph breakdownHeader = new Paragraph("CHI TIẾT DOANH THU/HOA HỒNG", labelFont);
                document.add(breakdownHeader);

                PdfPTable breakdownTable = new PdfPTable(2);
                breakdownTable.setWidthPercentage(100);
                breakdownTable.setWidths(new float[]{1, 2});

                BigDecimal original = amount != null ? amount : BigDecimal.ZERO;
                BigDecimal mentorReceive = null;
                try {
                    String refId = "BOOKING_" + booking.getId();
                    var payouts = walletTransactionRepository.findByReferenceTypeAndReferenceId("BOOKING_PAYOUT", refId);
                    if (payouts != null && !payouts.isEmpty()) {
                        for (var tx : payouts) {
                            if (tx.getCurrencyType() == WalletTransaction.CurrencyType.CASH && tx.getCashAmount() != null) {
                                mentorReceive = tx.getCashAmount();
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Không thể tải giao dịch payout cho booking {}: {}", booking.getId(), e.getMessage());
                }
                if (mentorReceive == null) {
                    mentorReceive = original.multiply(new BigDecimal("0.80"));
                }
                BigDecimal platformFee = original.subtract(mentorReceive);

                addInfoRow(breakdownTable, "Giá gốc người dùng:", formatCurrency(original), labelFont, valueFont);
                addInfoRow(breakdownTable, "Phí hệ thống:", formatCurrency(platformFee), labelFont, valueFont);
                addInfoRow(breakdownTable, "Thực nhận mentor:", formatCurrency(mentorReceive), labelFont, valueFont);

                document.add(breakdownTable);
            }

            addFooter(document);
            document.close();
            log.info("Generated PDF invoice for booking: {} (role={})", invoiceNo, roleUpper);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating PDF invoice for booking {}: {}", booking.getId(), e.getMessage());
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }

    private void addHeader(Document document) throws DocumentException {
        Image logo = null;
        try {
            var resource = getClass().getClassLoader().getResource("assets/skillverse.png");
            if (resource != null) {
                logo = Image.getInstance(resource);
            }
        } catch (Exception e) {
            log.warn("Không thể tải logo từ classpath: {}", e.getMessage());
        }

        if (logo == null) {
            try {
                java.io.File file = new java.io.File(LOGO_PATH);
                if (!file.exists()) {
                    file = new java.io.File("src/assets/skillverse.png");
                }
                if (file.exists()) {
                    logo = Image.getInstance(file.getAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("Không thể tải logo từ file hệ thống: {}", e.getMessage());
            }
        }

        if (logo != null) {
            logo.scaleToFit(160, 64);
            logo.setAlignment(Image.ALIGN_CENTER);
            document.add(logo);
        } else {
            log.warn("Logo hóa đơn không tìm thấy. Bỏ qua hiển thị logo.");
        }

        Font subFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.GRAY);
        Paragraph address = new Paragraph(COMPANY_ADDRESS + "\n" + COMPANY_EMAIL, subFont);
        address.setAlignment(Element.ALIGN_CENTER);
        document.add(address);

        document.add(new Paragraph("\n"));

        Font invoiceTitle = new Font(Font.HELVETICA, 18, Font.BOLD);
        Paragraph invoiceHeader = new Paragraph("HÓA ĐƠN THANH TOÁN", invoiceTitle);
        invoiceHeader.setAlignment(Element.ALIGN_CENTER);
        document.add(invoiceHeader);

        document.add(new Paragraph("\n"));
    }

    private void addInvoiceInfo(Document document, String invoiceNo, LocalDateTime date) throws DocumentException {
        Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 2});

        // Convert to Vietnam Time (UTC+7)
        LocalDateTime vietnamTime = date.plusHours(7);

        addInfoRow(infoTable, "Số hóa đơn:", invoiceNo, labelFont, valueFont);
        addInfoRow(infoTable, "Ngày tạo:", vietnamTime.format(DATE_FORMAT), labelFont, valueFont);

        document.add(infoTable);
        document.add(new Paragraph("\n"));
    }

    private void addCustomerInfo(Document document, String name, String email) throws DocumentException {
        Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

        Paragraph customerHeader = new Paragraph("THÔNG TIN KHÁCH HÀNG", labelFont);
        document.add(customerHeader);

        PdfPTable customerTable = new PdfPTable(2);
        customerTable.setWidthPercentage(100);
        customerTable.setWidths(new float[]{1, 2});

        addInfoRow(customerTable, "Họ tên:", name, labelFont, valueFont);
        addInfoRow(customerTable, "Email:", email, labelFont, valueFont);

        document.add(customerTable);
        document.add(new Paragraph("\n"));
    }

    private PdfPTable createTransactionTable() throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 3, 2});

        Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
        Color headerBg = new Color(0, 102, 204);

        addTableHeader(table, "Loại giao dịch", headerFont, headerBg);
        addTableHeader(table, "Mô tả", headerFont, headerBg);
        addTableHeader(table, "Số tiền (VNĐ)", headerFont, headerBg);

        return table;
    }

    private void addTableHeader(PdfPTable table, String text, Font font, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addTableRow(PdfPTable table, String type, String description, BigDecimal amount) {
        Font cellFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

        PdfPCell typeCell = new PdfPCell(new Phrase(type, cellFont));
        typeCell.setPadding(8);
        table.addCell(typeCell);

        PdfPCell descCell = new PdfPCell(new Phrase(description, cellFont));
        descCell.setPadding(8);
        table.addCell(descCell);

        PdfPCell amountCell = new PdfPCell(new Phrase(formatCurrency(amount), cellFont));
        amountCell.setPadding(8);
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(amountCell);
    }

    private void addInfoRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5);
        table.addCell(valueCell);
    }

    private void addTotal(Document document, BigDecimal amount) throws DocumentException {
        document.add(new Paragraph("\n"));

        Font totalLabelFont = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font totalValueFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(0, 102, 204));

        PdfPTable totalTable = new PdfPTable(2);
        totalTable.setWidthPercentage(50);
        totalTable.setHorizontalAlignment(Element.ALIGN_RIGHT);

        PdfPCell labelCell = new PdfPCell(new Phrase("TỔNG CỘNG:", totalLabelFont));
        labelCell.setBorder(Rectangle.TOP);
        labelCell.setPadding(10);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalTable.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(formatCurrency(amount), totalValueFont));
        valueCell.setBorder(Rectangle.TOP);
        valueCell.setPadding(10);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalTable.addCell(valueCell);

        document.add(totalTable);
    }

    private void addPaymentStatus(Document document, String status, String method) throws DocumentException {
        document.add(new Paragraph("\n"));

        Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

        PdfPTable statusTable = new PdfPTable(2);
        statusTable.setWidthPercentage(100);
        statusTable.setWidths(new float[]{1, 2});

        addInfoRow(statusTable, "Trạng thái:", getStatusDisplay(status), labelFont, valueFont);
        addInfoRow(statusTable, "Phương thức:", method, labelFont, valueFont);

        document.add(statusTable);
    }

    private void addFooter(Document document) throws DocumentException {
        document.add(new Paragraph("\n\n"));

        Font footerFont = new Font(Font.HELVETICA, 9, Font.ITALIC, Color.GRAY);
        Paragraph footer = new Paragraph(
            "Cảm ơn bạn đã sử dụng dịch vụ của SkillVerse!\n" +
            "Đây là hóa đơn điện tử được tạo tự động. Vui lòng liên hệ support@skillverse.edu.vn nếu cần hỗ trợ.",
            footerFont
        );
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        return String.format("%,.0f ₫", amount.doubleValue());
    }

    private String getPaymentTypeDescription(PaymentTransaction.PaymentType type) {
        if (type == null) return "Giao dịch";
        return switch (type) {
            case PREMIUM_SUBSCRIPTION -> "Đăng ký Premium";
            case COURSE_PURCHASE -> "Mua khóa học";
            case COIN_PURCHASE -> "Mua SkillCoin";
            case WALLET_TOPUP -> "Nạp tiền vào ví";
            case MENTOR_BOOKING -> "Đặt lịch mentor";
            case REFUND -> "Hoàn tiền";
        };
    }

    private String getStatusDisplay(String status) {
        return switch (status.toUpperCase()) {
            case "COMPLETED" -> "Hoàn thành ✓";
            case "PENDING" -> "Đang xử lý";
            case "FAILED" -> "Thất bại";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "USER";
        String r = role.trim().toUpperCase();
        return switch (r) {
            case "ADMIN", "USER", "MENTOR" -> r;
            default -> "USER";
        };
    }
    
    /**
     * Get display name for user - uses UserProfile.fullName first, falls back to User fields
     */
    private String getUserDisplayName(com.exe.skillverse_backend.auth_service.entity.User user) {
        if (user == null) return "N/A";
        
        // First try to get fullName from UserProfile
        try {
            var profile = userProfileRepository.findByUserId(user.getId());
            if (profile.isPresent() && profile.get().getFullName() != null && !profile.get().getFullName().isBlank()) {
                return profile.get().getFullName();
            }
        } catch (Exception e) {
            log.warn("Could not fetch user profile for user {}: {}", user.getId(), e.getMessage());
        }
        
        // Fall back to firstName + lastName from User entity
        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        
        // If both are null or empty, use email as display name
        if ((firstName == null || firstName.isBlank()) && (lastName == null || lastName.isBlank())) {
            return user.getEmail() != null ? user.getEmail().split("@")[0] : "User";
        }
        
        // Build full name from available parts
        StringBuilder name = new StringBuilder();
        if (firstName != null && !firstName.isBlank()) {
            name.append(firstName);
        }
        if (lastName != null && !lastName.isBlank()) {
            if (name.length() > 0) name.append(" ");
            name.append(lastName);
        }
        
        return name.toString().trim();
    }
}
