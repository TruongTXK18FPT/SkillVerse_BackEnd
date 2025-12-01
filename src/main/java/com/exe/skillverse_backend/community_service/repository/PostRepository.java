package com.exe.skillverse_backend.community_service.repository;

import com.exe.skillverse_backend.community_service.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    @Query(value = "SELECT p.* FROM posts p WHERE (:status IS NULL OR p.status = :status) AND (:authorId IS NULL OR p.user_id = :authorId) AND (:search IS NULL OR p.title ILIKE CONCAT('%', :search, '%') OR CAST(p.content AS TEXT) ILIKE CONCAT('%', :search, '%'))",
           countQuery = "SELECT COUNT(*) FROM posts p WHERE (:status IS NULL OR p.status = :status) AND (:authorId IS NULL OR p.user_id = :authorId) AND (:search IS NULL OR p.title ILIKE CONCAT('%', :search, '%') OR CAST(p.content AS TEXT) ILIKE CONCAT('%', :search, '%'))",
           nativeQuery = true)
    Page<Post> search(@Param("status") String status, @Param("authorId") Long authorId, @Param("search") String search, Pageable pageable);

    @Query(value = "SELECT COALESCE(SUM(p.like_count), 0) FROM posts p", nativeQuery = true)
    Long sumLikes();

    @Query(value = "SELECT COALESCE(SUM(p.comment_count), 0) FROM posts p", nativeQuery = true)
    Long sumComments();

    @Query("SELECT p.title FROM Post p")
    java.util.List<String> findAllTitles();

    @Query("SELECT p.content FROM Post p")
    java.util.List<String> findAllContents();

    @Query("SELECT p.tags FROM Post p")
    java.util.List<String> findAllTags();
}
