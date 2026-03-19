package com.exe.skillverse_backend.seminar_service.entity;

import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
