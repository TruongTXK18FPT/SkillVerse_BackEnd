package com.exe.skillverse_backend.community_service.repository;

import com.exe.skillverse_backend.community_service.entity.PostLike;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    Optional<PostLike> findByPost_IdAndUser_Id(Long postId, Long userId);
    long countByPost_Id(Long postId);
}
