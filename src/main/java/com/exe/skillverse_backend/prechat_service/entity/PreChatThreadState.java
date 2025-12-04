package com.exe.skillverse_backend.prechat_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "prechat_thread_state", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"mentor_id", "learner_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreChatThreadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id", nullable = false)
    private User mentor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learner_id", nullable = false)
    private User learner;

    @Builder.Default
    @Column(name = "hidden_for_mentor", nullable = false)
    private Boolean hiddenForMentor = false;

    @Builder.Default
    @Column(name = "hidden_for_learner", nullable = false)
    private Boolean hiddenForLearner = false;

    @Builder.Default
    @Column(name = "muted_for_mentor", nullable = false)
    private Boolean mutedForMentor = false;

    @Builder.Default
    @Column(name = "muted_for_learner", nullable = false)
    private Boolean mutedForLearner = false;
}
