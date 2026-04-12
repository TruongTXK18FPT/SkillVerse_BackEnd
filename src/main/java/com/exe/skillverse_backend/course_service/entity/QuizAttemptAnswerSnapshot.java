package com.exe.skillverse_backend.course_service.entity;

import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false, length = 20)
    private QuestionType questionType;

    @Column(name = "submitted_answer_text", columnDefinition = "TEXT")
    private String submittedAnswerText;

    @Column(name = "correct_answer_text", columnDefinition = "TEXT")
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
