package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.service.AdminUserService;
import com.exe.skillverse_backend.admin_service.dto.response.AdminUserResponse;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.UserStatus;
import com.exe.skillverse_backend.payment_service.dto.response.PaymentTransactionResponse;
import com.exe.skillverse_backend.payment_service.service.PaymentService;
import com.exe.skillverse_backend.wallet_service.dto.response.WalletTransactionResponse;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.Image;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin CSV Reports: Users and Transactions
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Reports", description = "Download CSV reports for users and transactions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReportController {

    private final AdminUserService adminUserService;
    private final PaymentService paymentService;
    private final WalletService walletService;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String LOGO_PATH = "c:/WorkSpace/EXE201/SkillVerse_BackEnd/src/assets/skillverse.png";

    @GetMapping(value = "/users", produces = "text/csv;charset=UTF-8")
    @Operation(summary = "Tải báo cáo người dùng (CSV)", description = "Xuất danh sách người dùng kèm thống kê dưới dạng CSV, hỗ trợ tiếng Việt")
    public ResponseEntity<byte[]> downloadUsersReport(
            @RequestParam(required = false) PrimaryRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search
    ) {
        log.info("Admin downloading users CSV report - role: {}, status: {}, search: {}", role, status, search);

        var response = adminUserService.getAllUsers(role, status, search);
        List<AdminUserResponse> users = response.getUsers();

        StringBuilder sb = new StringBuilder();
        // BOM for Excel UTF-8
        sb.append('\uFEFF');
        // Header (Vietnamese)
        sb.append("ID,Họ tên,Email,Vai trò,Trạng thái,Số khóa học tạo,Số khóa học tham gia,Chứng chỉ,Ngày tạo,Lần hoạt động cuối\n");

        for (AdminUserResponse u : users) {
            sb.append(csv(u.getId()))
              .append(',').append(csv(u.getFullName()))
              .append(',').append(csv(u.getEmail()))
              .append(',').append(csv(u.getPrimaryRole() != null ? u.getPrimaryRole().name() : ""))
              .append(',').append(csv(u.getStatus() != null ? u.getStatus().name() : ""))
              .append(',').append(csv(String.valueOf(u.getCoursesCreated())))
              .append(',').append(csv(String.valueOf(u.getCoursesEnrolled())))
              .append(',').append(csv(String.valueOf(u.getCertificatesEarned())))
              .append(',').append(csv(u.getCreatedAt()))
              .append(',').append(csv(u.getLastActive()))
              .append('\n');
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "bao-cao-nguoi-dung.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping(value = "/users/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Tải báo cáo người dùng (PDF)", description = "Xuất PDF thiết kế đẹp cho danh sách người dùng")
    public ResponseEntity<byte[]> downloadUsersReportPdf(
            @RequestParam(required = false) PrimaryRole role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search
    ) {
        var response = adminUserService.getAllUsers(role, status, search);
        List<AdminUserResponse> users = response.getUsers();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            try {
                Image logo = Image.getInstance(LOGO_PATH);
                logo.scaleToFit(160, 64);
                logo.setAlignment(Image.ALIGN_CENTER);
                document.add(logo);
            } catch (Exception ignored) {}

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, Color.BLACK);
            Font subtitleFont = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.DARK_GRAY);
            Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
            Font cellFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);

            Paragraph title = new Paragraph("BÁO CÁO NGƯỜI DÙNG", titleFont);
            title.setAlignment(Paragraph.ALIGN_CENTER);
            document.add(title);

            

            PdfPTable brand = new PdfPTable(1);
            brand.setWidthPercentage(100);
            PdfPCell brandCell = new PdfPCell(new Paragraph("Hành trình học tập và nghề nghiệp", new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE)));
            brandCell.setBackgroundColor(new Color(99, 102, 241));
            brandCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
            brandCell.setPadding(8f);
            brand.addCell(brandCell);
            document.add(brand);

            PdfPTable summary = new PdfPTable(4);
            summary.setWidthPercentage(100);
            addSummaryCell(summary, "Tổng", String.valueOf(response.getTotalUsers()));
            addSummaryCell(summary, "Mentor", String.valueOf(response.getTotalMentors()));
            addSummaryCell(summary, "Doanh nghiệp", String.valueOf(response.getTotalRecruiters()));
            addSummaryCell(summary, "Hoạt động", String.valueOf(response.getTotalActiveUsers()));
            document.add(summary);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{18f, 28f, 14f, 12f, 14f, 14f});
            addHeaderCell(table, "Họ tên", headerFont, new Color(16, 185, 129));
            addHeaderCell(table, "Email", headerFont, new Color(16, 185, 129));
            addHeaderCell(table, "Vai trò", headerFont, new Color(16, 185, 129));
            addHeaderCell(table, "Trạng thái", headerFont, new Color(16, 185, 129));
            addHeaderCell(table, "Ngày tạo", headerFont, new Color(16, 185, 129));
            addHeaderCell(table, "Hoạt động cuối", headerFont, new Color(16, 185, 129));

            for (AdminUserResponse u : users) {
                addCell(table, safe(u.getFullName()), cellFont);
                addCell(table, safe(u.getEmail()), cellFont);
                addCell(table, safe(u.getPrimaryRole() != null ? u.getPrimaryRole().name() : ""), cellFont);
                addCell(table, safe(u.getStatus() != null ? u.getStatus().name() : ""), cellFont);
                addCell(table, safe(u.getCreatedAt() != null ? DATE_FORMAT.format(u.getCreatedAt()) : ""), cellFont);
                addCell(table, safe(u.getLastActive() != null ? DATE_FORMAT.format(u.getLastActive()) : ""), cellFont);
            }
            document.add(table);

            document.close();
            byte[] bytes = baos.toByteArray();
            String filename = "bao-cao-nguoi-dung.pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate users PDF", e);
        }
    }

    @GetMapping(value = "/transactions", produces = "text/csv;charset=UTF-8")
    @Operation(summary = "Tải báo cáo giao dịch (CSV)", description = "Xuất danh sách giao dịch thanh toán và ví dưới dạng CSV, hỗ trợ tiếng Việt")
    public ResponseEntity<byte[]> downloadTransactionsReport(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String walletType
    ) {
        log.info("Admin downloading transactions CSV report - status: {}, userId: {}", status, userId);

        LocalDateTime start = null;
        LocalDateTime end = null;
        try {
            if (startDate != null && !startDate.isEmpty()) {
                start = LocalDateTime.parse(startDate);
            }
            if (endDate != null && !endDate.isEmpty()) {
                end = LocalDateTime.parse(endDate);
            }
        } catch (Exception ignored) {}

        var payments = paymentService.getAllTransactionsAdmin(
                status, userId, start, end, Pageable.unpaged());
        var walletPage = walletService.getAllTransactionsAdmin(
                walletType, Pageable.unpaged());

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("Mã giao dịch,Loại,Mô tả,Số tiền,Trạng thái,Phương thức,Người dùng,Email,Thời gian\n");

        for (PaymentTransactionResponse p : payments.getContent()) {
            sb.append(csv(String.valueOf(p.getId())))
              .append(',').append(csv(p.getType() != null ? p.getType().name() : "PAYMENT"))
              .append(',').append(csv(p.getDescription()))
              .append(',').append(csv(p.getAmount() != null ? p.getAmount().toString() : "0"))
              .append(',').append(csv(p.getStatus() != null ? p.getStatus().name() : ""))
              .append(',').append(csv(p.getPaymentMethod() != null ? p.getPaymentMethod().name() : ""))
              .append(',').append(csv(p.getUserName()))
              .append(',').append(csv(p.getUserEmail()))
              .append(',').append(csv(p.getCreatedAt() != null ? DATE_FORMAT.format(p.getCreatedAt()) : ""))
              .append('\n');
        }

        for (WalletTransactionResponse w : walletPage.getContent()) {
            String amount = w.getCashAmount() != null ? w.getCashAmount().toString() :
                            (w.getCoinAmount() != null ? w.getCoinAmount().toString() : "0");
            sb.append(csv(String.valueOf(w.getTransactionId())))
              .append(',').append(csv(w.getTransactionTypeName() != null ? w.getTransactionTypeName() : w.getTransactionType()))
              .append(',').append(csv(w.getDescription()))
              .append(',').append(csv(amount))
              .append(',').append(csv(w.getStatus()))
              .append(',').append(csv(w.getCurrencyType()))
              .append(',').append(csv(w.getUserName()))
              .append(',').append(csv(w.getUserEmail()))
              .append(',').append(csv(w.getCreatedAt()))
              .append('\n');
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "bao-cao-giao-dich.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(bytes);
    }

    private static String csv(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        s = s.replace("\r", " ").replace("\n", " ");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            s = '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    @GetMapping(value = "/transactions/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Tải báo cáo giao dịch (PDF)", description = "Xuất PDF thiết kế đẹp cho danh sách giao dịch")
    public ResponseEntity<byte[]> downloadTransactionsReportPdf(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String walletType
    ) {
        LocalDateTime start = null;
        LocalDateTime end = null;
        try {
            if (startDate != null && !startDate.isEmpty()) {
                try { start = LocalDateTime.parse(startDate); } catch (Exception e1) {
                    try { start = java.time.LocalDate.parse(startDate).atStartOfDay(); } catch (Exception e2) {}
                }
            }
            if (endDate != null && !endDate.isEmpty()) {
                try { end = LocalDateTime.parse(endDate); } catch (Exception e1) {
                    try { end = java.time.LocalDate.parse(endDate).atTime(23,59,59); } catch (Exception e2) {}
                }
            }
        } catch (Exception ignored) {}

        var payments = paymentService.getAllTransactionsAdmin(
                status, userId, start, end, Pageable.unpaged());
        var walletPage = walletService.getAllTransactionsAdmin(
                walletType, Pageable.unpaged());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            try {
                Image logo = Image.getInstance(LOGO_PATH);
                logo.scaleToFit(160, 64);
                logo.setAlignment(Image.ALIGN_CENTER);
                document.add(logo);
            } catch (Exception ignored) {}

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD, Color.BLACK);
            Font subtitleFont = new Font(Font.HELVETICA, 12, Font.NORMAL, Color.DARK_GRAY);
            Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.WHITE);
            Font cellFont = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.BLACK);

            Paragraph title = new Paragraph("BÁO CÁO GIAO DỊCH", titleFont);
            title.setAlignment(Paragraph.ALIGN_CENTER);
            document.add(title);

            

            if (start != null || end != null) {
                String rangeText = "Khoảng thời gian: " +
                        (start != null ? DATE_FORMAT.format(start) : "...") +
                        " đến " +
                        (end != null ? DATE_FORMAT.format(end) : "...");
                Paragraph range = new Paragraph(rangeText, subtitleFont);
                range.setAlignment(Paragraph.ALIGN_CENTER);
                document.add(range);
            }

            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{12f, 12f, 26f, 12f, 12f, 12f, 16f, 20f, 16f});
            Color headerBg = new Color(59, 130, 246);
            addHeaderCell(table, "Mã", headerFont, headerBg);
            addHeaderCell(table, "Loại", headerFont, headerBg);
            addHeaderCell(table, "Mô tả", headerFont, headerBg);
            addHeaderCell(table, "Số tiền", headerFont, headerBg);
            addHeaderCell(table, "Trạng thái", headerFont, headerBg);
            addHeaderCell(table, "Phương thức", headerFont, headerBg);
            addHeaderCell(table, "Người dùng", headerFont, headerBg);
            addHeaderCell(table, "Email", headerFont, headerBg);
            addHeaderCell(table, "Thời gian", headerFont, headerBg);

            for (PaymentTransactionResponse p : payments.getContent()) {
                addCell(table, safe(String.valueOf(p.getId())), cellFont);
                addCell(table, safe(p.getType() != null ? p.getType().name() : "PAYMENT"), cellFont);
                addCell(table, safe(p.getDescription()), cellFont);
                addCell(table, safe(p.getAmount() != null ? p.getAmount().toString() : "0"), cellFont);
                addCell(table, safe(p.getStatus() != null ? p.getStatus().name() : ""), cellFont);
                addCell(table, safe(p.getPaymentMethod() != null ? p.getPaymentMethod().name() : ""), cellFont);
                addCell(table, safe(p.getUserName()), cellFont);
                addCell(table, safe(p.getUserEmail()), cellFont);
                addCell(table, safe(p.getCreatedAt() != null ? DATE_FORMAT.format(p.getCreatedAt()) : ""), cellFont);
            }

            for (WalletTransactionResponse w : walletPage.getContent()) {
                String amount = w.getCashAmount() != null ? w.getCashAmount().toString() :
                        (w.getCoinAmount() != null ? w.getCoinAmount().toString() : "0");
                addCell(table, safe(String.valueOf(w.getTransactionId())), cellFont);
                addCell(table, safe(w.getTransactionTypeName() != null ? w.getTransactionTypeName() : w.getTransactionType()), cellFont);
                addCell(table, safe(w.getDescription()), cellFont);
                addCell(table, safe(amount), cellFont);
                addCell(table, safe(w.getStatus()), cellFont);
                addCell(table, safe(w.getCurrencyType()), cellFont);
                addCell(table, safe(w.getUserName()), cellFont);
                addCell(table, safe(w.getUserEmail()), cellFont);
                addCell(table, safe(w.getCreatedAt() != null ? DATE_FORMAT.format(w.getCreatedAt()) : ""), cellFont);
            }

            document.add(table);
            document.close();
            byte[] bytes = baos.toByteArray();
            String filename = "bao-cao-giao-dich.pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate transactions PDF", e);
        }
    }

    private void addHeaderCell(PdfPTable table, String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        cell.setPadding(6f);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPadding(5f);
        table.addCell(cell);
    }

    private void addSummaryCell(PdfPTable table, String label, String value) {
        Font labelFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
        Font valueFont = new Font(Font.HELVETICA, 14, Font.BOLD, Color.WHITE);
        PdfPTable inner = new PdfPTable(1);
        inner.setWidthPercentage(100);
        PdfPCell c1 = new PdfPCell(new Paragraph(label, labelFont));
        c1.setBackgroundColor(new Color(16, 185, 129));
        c1.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        c1.setPadding(4f);
        inner.addCell(c1);
        PdfPCell c2 = new PdfPCell(new Paragraph(value, valueFont));
        c2.setBackgroundColor(new Color(20, 184, 166));
        c2.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        c2.setPadding(6f);
        inner.addCell(c2);
        PdfPCell wrap = new PdfPCell(inner);
        wrap.setPadding(4f);
        table.addCell(wrap);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
