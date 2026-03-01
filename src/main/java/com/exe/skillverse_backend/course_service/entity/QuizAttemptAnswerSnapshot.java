package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "quiz_attempt_answer_snapshots")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptAnswerSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "question_order_index")
    private Integer questionOrderIndex;

    @Lob
    @Column(name = "question_text", nullable = false)
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType questionType;

    @Lob
    @Column(name = "submitted_answer_text")
    private String submittedAnswerText;

    @Lob
    @Column(name = "correct_answer_text")
    private String correctAnswerText;

    @Column(name = "submitted_answer_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String submittedAnswerJson;

    @Column(name = "options_snapshot_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String optionsSnapshotJson;

    @Column(name = "answered", nullable = false)
    private Boolean answered;

    @Column(name = "is_correct", nullable = false)
    private Boolean correct;

    @Column(name = "score_earned", nullable = false)
    private Integer scoreEarned;

    @Column(name = "max_score", nullable = false)
    private Integer maxScore;
}
