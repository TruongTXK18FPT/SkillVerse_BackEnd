package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.quizdto.*;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizSummaryDTO;
import java.util.List;

public interface QuizService {

    QuizDetailDTO createQuiz(Long moduleId, QuizCreateDTO dto, Long actorId);

    QuizDetailDTO updateQuiz(Long quizId, QuizUpdateDTO dto, Long actorId);

    void deleteQuiz(Long quizId, Long actorId);

    QuizQuestionDetailDTO addQuestion(Long quizId, QuizQuestionCreateDTO dto, Long actorId);

    QuizQuestionDetailDTO updateQuestion(Long questionId, QuizQuestionUpdateDTO dto, Long actorId);

    void deleteQuestion(Long questionId, Long actorId);

    QuizOptionDetailDTO addOption(Long questionId, QuizOptionCreateDTO dto, Long actorId);

    QuizOptionDetailDTO updateOption(Long optionId, QuizOptionUpdateDTO dto, Long actorId);

    void deleteOption(Long optionId, Long actorId);

    // ========== Quiz Query Operations ==========

    QuizDetailDTO getQuiz(Long quizId);

    List<QuizSummaryDTO> listQuizzesByModule(Long moduleId);

    // ========== Quiz Attempt & Submission ==========

    QuizAttemptDTO submitQuiz(Long quizId, SubmitQuizDTO submitData, Long userId);

    List<QuizAttemptDTO> getUserAttempts(Long quizId, Long userId);
}