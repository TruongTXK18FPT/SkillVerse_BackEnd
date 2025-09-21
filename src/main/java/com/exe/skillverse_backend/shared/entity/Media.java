package com.exe.skillverse_backend.shared.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
// import com.exe.skillverse_backend.course_service.entity.Certificate; //

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "media")
@Data
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Đường dẫn tuyệt đối (CDN/S3/…) */
    @Column(nullable = false)
    private String url;

    /** IMAGE, VIDEO, DOCUMENT, AUDIO, ... */
    @Column(nullable = false)
    private String type;

    @Column(name = "file_name")
    private String fileName;

    /** bytes */
    @Column(name = "file_size")
    private Long fileSize;

    /** FK người upload (users.id) – giữ ở dạng scalar để tách bounded context) */
    @Column(name = "uploaded_by")
    private Long uploadedBy;

    /** Quan hệ đến User (để dễ JOIN khi cần) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", insertable = false, updatable = false)
    private User uploadedByUser;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    /* =====================
       QUAN HỆ NGƯỢC (BACKREF)
       ===================== */

    /** Media được dùng làm thumbnail cho các Course */
    @OneToMany(mappedBy = "thumbnail", fetch = FetchType.LAZY)
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private Set<Course> coursesAsThumbnail = new HashSet<>();

    /** Media được dùng làm video cho các Lesson */
    @OneToMany(mappedBy = "videoMedia", fetch = FetchType.LAZY)
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private Set<Lesson> lessonsAsVideo = new HashSet<>();

    /** Media được dùng làm file nộp bài Assignment */
    @OneToMany(mappedBy = "fileMedia", fetch = FetchType.LAZY)
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private Set<AssignmentSubmission> assignmentSubmissions = new HashSet<>();

    // Nếu bạn có field certificateMedia trong Certificate:
    // @OneToMany(mappedBy = "certificateMedia", fetch = FetchType.LAZY)
    // @ToString.Exclude @EqualsAndHashCode.Exclude
    // private Set<Certificate> certificates = new HashSet<>();
}
