package com.exe.skillverse_backend.community_service.repository;

import com.exe.skillverse_backend.community_service.entity.PostDislike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PostDislikeRepository extends JpaRepository<PostDislike, Long> {
    Optional<PostDislike> findByPost_IdAndUser_Id(Long postId, Long userId);
    long countByPost_Id(Long postId);
}
