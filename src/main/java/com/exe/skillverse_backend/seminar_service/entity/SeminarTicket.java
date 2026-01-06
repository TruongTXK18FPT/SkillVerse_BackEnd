package com.exe.skillverse_backend.seminar_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seminar_tickets", uniqueConstraints = {
        @UniqueConstraint(name = "uk_seminar_ticket_user_seminar", columnNames = { "user_id", "seminar_id" })
}, indexes = {
        @Index(name = "idx_seminar_tickets_user_seminar", columnList = "user_id, seminar_id"),
        @Index(name = "idx_seminar_tickets_seminar_id", columnList = "seminar_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeminarTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seminar_id", nullable = false)
    private Seminar seminar;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private BigDecimal pricePaid;

    @CreationTimestamp
    private LocalDateTime purchasedAt;
}
