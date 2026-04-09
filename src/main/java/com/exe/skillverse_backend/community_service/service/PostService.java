package com.exe.skillverse_backend.community_service.service;

import com.exe.skillverse_backend.community_service.dto.request.CommentCreateRequest;
import com.exe.skillverse_backend.community_service.dto.request.PostCreateRequest;
import com.exe.skillverse_backend.community_service.dto.request.PostUpdateRequest;
import com.exe.skillverse_backend.community_service.dto.response.CommentResponse;
import com.exe.skillverse_backend.community_service.dto.response.PostResponse;
import com.exe.skillverse_backend.community_service.entity.PostStatus;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PostService {
    PostResponse createPost(Long userId, PostCreateRequest req);

    Page<PostResponse> listPosts(PostStatus status, Long authorId, String search, Pageable pageable, Long currentUserId);

    Page<PostResponse> listSavedPosts(Long userId, Pageable pageable);

    Map<String, Object> getStats();

    Map<String, Object> getTrends();

    PostResponse getPost(Long id, Long currentUserId);

    PostResponse updatePost(Long id, Long userId, PostUpdateRequest req);

    void deletePost(Long id, Long userId);

    PostResponse likePost(Long id, Long userId);

    PostResponse dislikePost(Long id, Long userId);

    CommentResponse addComment(Long postId, Long userId, CommentCreateRequest req);

    Page<CommentResponse> listComments(Long postId, boolean includeHidden, Pageable pageable);

    void savePost(Long postId, Long userId);

    void deleteComment(Long postId, Long commentId, Long userId);

    void hideComment(Long postId, Long commentId, Long userId, String note);

    void unhideComment(Long postId, Long commentId, Long userId);

    void reportComment(Long postId, Long commentId, Long userId, String reason);
}
