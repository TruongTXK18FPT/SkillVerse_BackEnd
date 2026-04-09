package com.exe.skillverse_backend.community_service.controller;

import com.exe.skillverse_backend.community_service.dto.request.CommentCreateRequest;
import com.exe.skillverse_backend.community_service.dto.request.PostCreateRequest;
import com.exe.skillverse_backend.community_service.dto.request.PostUpdateRequest;
import com.exe.skillverse_backend.community_service.dto.response.CommentResponse;
import com.exe.skillverse_backend.community_service.dto.response.PostResponse;
import com.exe.skillverse_backend.community_service.entity.PostStatus;
import com.exe.skillverse_backend.community_service.service.PostService;
import com.exe.skillverse_backend.community_service.service.RateLimiterService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {
    private final PostService postService;
    private final RateLimiterService rateLimiterService;

    @PostMapping
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody PostCreateRequest req, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        boolean allowed = rateLimiterService.tryConsume("create:" + userId, 30, 60);
        if (!allowed)
            return ResponseEntity.status(429).build();
        PostResponse res = postService.createPost(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping
    public ResponseEntity<Page<PostResponse>> listPosts(
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        Long userId = auth != null ? Long.parseLong(auth.getName()) : null;
        Page<PostResponse> res = postService.listPosts(status, authorId, search, pageable, userId);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/saved")
    public ResponseEntity<Page<PostResponse>> listSavedPosts(
            Authentication auth,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long userId = Long.parseLong(auth.getName());
        Page<PostResponse> res = postService.listSavedPosts(userId, pageable);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(postService.getStats());
    }

    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> trends() {
        return ResponseEntity.ok(postService.getTrends());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long id, Authentication auth) {
        Long userId = auth != null ? Long.parseLong(auth.getName()) : null;
        return ResponseEntity.ok(postService.getPost(id, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(@PathVariable Long id, @Valid @RequestBody PostUpdateRequest req,
            Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        PostResponse res = postService.updatePost(id, userId, req);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        postService.deletePost(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<PostResponse> likePost(@PathVariable Long id, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        boolean allowed = rateLimiterService.tryConsume("like:" + userId, 100, 60);
        if (!allowed)
            return ResponseEntity.status(429).build();
        return ResponseEntity.ok(postService.likePost(id, userId));
    }

    @PostMapping("/{id}/dislike")
    public ResponseEntity<PostResponse> dislikePost(@PathVariable Long id, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        boolean allowed = rateLimiterService.tryConsume("dislike:" + userId, 100, 60);
        if (!allowed)
            return ResponseEntity.status(429).build();
        return ResponseEntity.ok(postService.dislikePost(id, userId));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id,
            @Valid @RequestBody CommentCreateRequest req, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        boolean allowed = rateLimiterService.tryConsume("comment:" + userId, 100, 60);
        if (!allowed)
            return ResponseEntity.status(429).build();
        CommentResponse res = postService.addComment(id, userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<Page<CommentResponse>> listComments(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "false") boolean includeHidden,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(postService.listComments(id, includeHidden, pageable));
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long postId, @PathVariable Long commentId,
            Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        postService.deleteComment(postId, commentId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{postId}/comments/{commentId}/hide")
    public ResponseEntity<Void> hideComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        String note = body != null ? body.getOrDefault("note", null) : null;
        postService.hideComment(postId, commentId, userId, note);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{postId}/comments/{commentId}/unhide")
    public ResponseEntity<Void> unhideComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        postService.unhideComment(postId, commentId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{postId}/comments/{commentId}/report")
    public ResponseEntity<Void> reportComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        String reason = body != null ? body.getOrDefault("reason", "") : "";
        boolean allowed = rateLimiterService.tryConsume("report:" + userId, 200, 60);
        if (!allowed)
            return ResponseEntity.status(429).build();
        postService.reportComment(postId, commentId, userId, reason);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<Void> savePost(@PathVariable Long id, Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        boolean allowed = rateLimiterService.tryConsume("save:" + userId, 200, 60);
        if (!allowed)
            return ResponseEntity.status(429).build();
        postService.savePost(id, userId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
