package com.exe.skillverse_backend.course_service.entity;

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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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

  @Column(name = "recipient_name_snapshot", length = 255)
  private String recipientNameSnapshot;

  @Column(name = "course_title_snapshot", length = 255)
  private String courseTitleSnapshot;

  @Column(name = "instructor_name_snapshot", length = 255)
  private String instructorNameSnapshot;

  @Column(name = "instructor_signature_url_snapshot", length = 1000)
  private String instructorSignatureUrlSnapshot;

  @Column(nullable = false)
  private Instant issuedAt = Instant.now();

  private Instant revokedAt;

  /** criteria: JSON text; nếu dùng Postgres jsonb có thể set columnDefinition="jsonb" */
  @Column(columnDefinition = "TEXT")
  private String criteria;

  @Column(length = 120)
  private String revokeReason;

  private Long revokedBy;
}
