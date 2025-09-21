package com.exe.skillverse_backend.course_service.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.PurchaseStatus;

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

//   @Column(nullable = false, length = 64)
//   private String paymentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PurchaseStatus status = PurchaseStatus.PENDING;

  @Column(nullable = false)
  private Instant purchasedAt = Instant.now();

  @Column(length = 50)
  private String couponCode;
}
