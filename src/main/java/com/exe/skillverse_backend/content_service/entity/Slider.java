package com.exe.skillverse_backend.content_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sliders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Slider {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String imageUrl;

    private String publicId; // For Cloudinary deletion

    private String ctaText;
    private String ctaLink;

    @Builder.Default
    private Boolean isActive = true;

    @Builder.Default
    @Column(name = "is_login")
    private Boolean isLogin = false;

    private Integer displayOrder;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
