package com.exe.skillverse_backend.community_service.repository;

import com.exe.skillverse_backend.community_service.entity.SavedPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SavedPostRepository extends JpaRepository<SavedPost, Long> {
    Optional<SavedPost> findByPost_IdAndUser_Id(Long postId, Long userId);
    org.springframework.data.domain.Page<SavedPost> findByUser_Id(Long userId, org.springframework.data.domain.Pageable pageable);
}
