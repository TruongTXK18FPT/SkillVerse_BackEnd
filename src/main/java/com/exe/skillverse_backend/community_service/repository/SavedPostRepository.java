package com.exe.skillverse_backend.community_service.repository;

import com.exe.skillverse_backend.community_service.entity.SavedPost;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface SavedPostRepository extends JpaRepository<SavedPost, Long> {
    Optional<SavedPost> findByPost_IdAndUser_Id(Long postId, Long userId);
    Page<SavedPost> findByUser_Id(Long userId, Pageable pageable);
}
