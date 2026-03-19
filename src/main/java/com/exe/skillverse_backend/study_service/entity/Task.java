package com.exe.skillverse_backend.study_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String fullDescription;

    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    private TaskPriority priority;

    // This field can be used for simple status tracking or synced with column name
    private String status; 

    private Integer userProgress; // 0-100
    private String satisfactionLevel; // e.g., "Satisfied", "Neutral", "Unsatisfied"
    private String userNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "column_id")
    private TaskColumn column;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToMany
    @JoinTable(
        name = "task_study_sessions",
        joinColumns = @JoinColumn(name = "task_id"),
        inverseJoinColumns = @JoinColumn(name = "session_id")
    )
    private List<StudySession> linkedSessions;
}
