package com.exe.skillverse_backend.course_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.enums.CertificateType;

@Entity @Table(name = "certificates",
  uniqueConstraints = @UniqueConstraint(columnNames = "serial"),
  indexes = @Index(columnList = "user_id, course_id"))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Certificate {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "course_id", nullable = false)
  private Course course;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private CertificateType type = CertificateType.COURSE;

  @Column(nullable = false, length = 64)
  private String serial;

  @Column(nullable = false)
  private Instant issuedAt = Instant.now();

  private Instant revokedAt;

  /** criteria: JSON text; nếu dùng Postgres jsonb có thể set columnDefinition="jsonb" */
  @Lob
  private String criteria;

  @Column(length = 120)
  private String revokeReason;
}
