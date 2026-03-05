package com.exe.skillverse_backend.business_service.repository;

import com.exe.skillverse_backend.business_service.entity.RevisionNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RevisionNoteRepository extends JpaRepository<RevisionNote, Long> {

    List<RevisionNote> findByApplicationIdOrderByRequestedAtDesc(Long applicationId);

    long countByApplicationId(Long applicationId);

    // Find unresolved revision notes
    List<RevisionNote> findByApplicationIdAndResolvedAtIsNull(Long applicationId);
}
