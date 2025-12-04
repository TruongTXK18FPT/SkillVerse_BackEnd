package com.exe.skillverse_backend.mentor_service.repository;

import com.exe.skillverse_backend.mentor_service.entity.FavoriteMentor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteMentorRepository extends JpaRepository<FavoriteMentor, Long> {
    boolean existsByStudentIdAndMentorId(Long studentId, Long mentorId);
    Optional<FavoriteMentor> findByStudentIdAndMentorId(Long studentId, Long mentorId);
    List<FavoriteMentor> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    void deleteByStudentIdAndMentorId(Long studentId, Long mentorId);
}
