package com.exe.skillverse_backend.community_service.repository;

import com.exe.skillverse_backend.community_service.entity.Comment;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    Page<Comment> findByPost_IdOrderByCreatedAtAsc(Long postId, Pageable pageable);
    Page<Comment> findByPost_IdAndHiddenFalseOrderByCreatedAtAsc(Long postId, Pageable pageable);
    long countByPost_Id(Long postId);
    List<Comment> findByParent_Id(Long parentId);
}
