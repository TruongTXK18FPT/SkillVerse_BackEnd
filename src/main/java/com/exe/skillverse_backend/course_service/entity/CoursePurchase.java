package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.PurchaseStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "course_purchase",
  indexes = { @Index(columnList = "user_id, course_id"), @Index(columnList = "status") })
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CoursePurchase {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;        

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(nullable = false, length = 10)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private PurchaseStatus status = PurchaseStatus.PENDING;

  @Column(nullable = false)
  @Builder.Default
  private Instant purchasedAt = Instant.now();

  @Column(length = 50)
  private String couponCode;
}
