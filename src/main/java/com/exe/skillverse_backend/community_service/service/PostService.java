package com.exe.skillverse_backend.community_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.community_service.dto.request.CommentCreateRequest;
import com.exe.skillverse_backend.community_service.dto.request.PostCreateRequest;
import com.exe.skillverse_backend.community_service.dto.request.PostUpdateRequest;
import com.exe.skillverse_backend.community_service.dto.response.CommentResponse;
import com.exe.skillverse_backend.community_service.dto.response.PostResponse;
import com.exe.skillverse_backend.community_service.entity.Comment;
import com.exe.skillverse_backend.community_service.entity.Post;
import com.exe.skillverse_backend.community_service.entity.PostLike;
import com.exe.skillverse_backend.community_service.entity.PostStatus;
import com.exe.skillverse_backend.community_service.entity.SavedPost;
import com.exe.skillverse_backend.community_service.repository.CommentRepository;
import com.exe.skillverse_backend.community_service.repository.PostLikeRepository;
import com.exe.skillverse_backend.community_service.repository.PostDislikeRepository;
import com.exe.skillverse_backend.community_service.repository.PostRepository;
import com.exe.skillverse_backend.community_service.repository.SavedPostRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostDislikeRepository postDislikeRepository;
    private final CommentRepository commentRepository;
    private final SavedPostRepository savedPostRepository;
    private final UserRepository userRepository;

    @Transactional
    public PostResponse createPost(Long userId, PostCreateRequest req) {
        User user = userRepository.findById(userId).orElseThrow();
        String title = HtmlUtils.htmlEscape(req.getTitle());
        String content = HtmlUtils.htmlEscape(req.getContent());
        String tags = req.getTags() != null ? String.join(",", req.getTags()) : null;
        PostStatus status = Optional.ofNullable(req.getStatus()).orElse(PostStatus.DRAFT);
        Post post = Post.builder()
                .user(user)
                .title(title)
                .content(content)
                .status(status)
                .thumbnailUrl(req.getThumbnailUrl())
                .category(req.getCategory())
                .tags(tags)
                .build();
        Post saved = postRepository.save(post);
        return toResponse(saved);
    }

    public Page<PostResponse> listPosts(PostStatus status, Long authorId, String search, Pageable pageable) {
        // Remap sort properties for Native Query
        Sort sort = pageable.getSort();
        Sort newSort = Sort.unsorted();
        for (Sort.Order order : sort) {
            String property = order.getProperty();
            if ("createdAt".equals(property)) property = "created_at";
            else if ("updatedAt".equals(property)) property = "updated_at";
            else if ("userId".equals(property)) property = "user_id";
            else if ("likeCount".equals(property)) property = "like_count";
            else if ("viewCount".equals(property)) property = "view_count";
            else if ("commentCount".equals(property)) property = "comment_count";
            
            newSort = newSort.and(Sort.by(order.getDirection(), property));
        }
        
        Pageable nativePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), newSort);

        String statusName = status != null ? status.name() : null;
        Page<Post> page = postRepository.search(statusName, authorId, search, nativePageable);
        return page.map(this::toResponse);
    }

    public Page<PostResponse> listSavedPosts(Long userId, Pageable pageable) {
        Page<SavedPost> saved = savedPostRepository.findByUser_Id(userId, pageable);
        return saved.map(sp -> toResponse(sp.getPost()));
    }

    public java.util.Map<String, Object> getStats() {
        Long totalPosts = postRepository.count();
        Long totalUsers = userRepository.count();
        Long totalLikes = Optional.ofNullable(postRepository.sumLikes()).orElse(0L);
        Long totalComments = Optional.ofNullable(postRepository.sumComments()).orElse(0L);
        Long signal = totalLikes + totalComments;

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("totalPosts", totalPosts);
        stats.put("totalUsers", totalUsers);
        stats.put("totalLikes", totalLikes);
        stats.put("totalComments", totalComments);
        stats.put("signal", signal);
        return stats;
    }

    public java.util.Map<String, Object> getTrends() {
        java.util.Map<String, Integer> tagCounts = new java.util.HashMap<>();
        
        // 1. Tags from 'tags' column
        java.util.List<String> allTags = postRepository.findAllTags();
        for (String t : allTags) {
            if (t == null || t.isEmpty()) continue;
            for (String tag : t.split(",")) {
                String normalized = tag.trim().toLowerCase();
                if (!normalized.isEmpty()) {
                    tagCounts.put(normalized, tagCounts.getOrDefault(normalized, 0) + 1);
                }
            }
        }

        // 2. Tags from content (legacy support)
        java.util.List<String> contents = postRepository.findAllContents();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("#([A-Za-z0-9_]+)");
        for (String c : contents) {
            if (c == null) continue;
            java.util.regex.Matcher m = p.matcher(c);
            while (m.find()) {
                String tag = m.group(1).toLowerCase();
                tagCounts.put(tag, tagCounts.getOrDefault(tag, 0) + 1);
            }
        }

        java.util.List<java.util.Map<String, Object>> top = tagCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> {
                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                    m.put("topic", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .toList();
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        res.put("trends", top);
        return res;
    }

    public PostResponse getPost(Long id) {
        Post post = postRepository.findById(id).orElseThrow();
        post.setViewCount(post.getViewCount() + 1);
        postRepository.save(post);
        return toResponse(post);
    }

    @Transactional
    public PostResponse updatePost(Long id, Long userId, PostUpdateRequest req) {
        Post post = postRepository.findById(id).orElseThrow();
        if (!post.getUser().getId().equals(userId)) throw new RuntimeException("Forbidden");
        if (req.getTitle() != null) post.setTitle(HtmlUtils.htmlEscape(req.getTitle()));
        if (req.getContent() != null) post.setContent(HtmlUtils.htmlEscape(req.getContent()));
        if (req.getThumbnailUrl() != null) post.setThumbnailUrl(req.getThumbnailUrl());
        if (req.getCategory() != null) post.setCategory(req.getCategory());
        if (req.getTags() != null) post.setTags(String.join(",", req.getTags()));
        if (req.getStatus() != null) post.setStatus(req.getStatus());
        Post saved = postRepository.save(post);
        return toResponse(saved);
    }

    @Transactional
    public void deletePost(Long id, Long userId) {
        Post post = postRepository.findById(id).orElseThrow();
        if (!post.getUser().getId().equals(userId)) throw new RuntimeException("Forbidden");
        postRepository.delete(post);
    }

    @Transactional
    public PostResponse likePost(Long id, Long userId) {
        Post post = postRepository.findById(id).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        Optional<PostLike> existing = postLikeRepository.findByPost_IdAndUser_Id(id, userId);
        if (existing.isPresent()) {
            postLikeRepository.delete(existing.get());
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        } else {
            Optional<com.exe.skillverse_backend.community_service.entity.PostDislike> exDis =
                    postDislikeRepository.findByPost_IdAndUser_Id(id, userId);
            if (exDis.isPresent()) {
                postDislikeRepository.delete(exDis.get());
                post.setDislikeCount(Math.max(0, post.getDislikeCount() - 1));
            }
            PostLike like = PostLike.builder().post(post).user(user).build();
            postLikeRepository.save(like);
            post.setLikeCount(post.getLikeCount() + 1);
        }
        postRepository.save(post);
        return toResponse(post);
    }

    @Transactional
    public PostResponse dislikePost(Long id, Long userId) {
        Post post = postRepository.findById(id).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        Optional<com.exe.skillverse_backend.community_service.entity.PostDislike> existing =
                postDislikeRepository.findByPost_IdAndUser_Id(id, userId);
        if (existing.isPresent()) {
            postDislikeRepository.delete(existing.get());
            post.setDislikeCount(Math.max(0, post.getDislikeCount() - 1));
        } else {
            Optional<PostLike> exLike = postLikeRepository.findByPost_IdAndUser_Id(id, userId);
            if (exLike.isPresent()) {
                postLikeRepository.delete(exLike.get());
                post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            }
            com.exe.skillverse_backend.community_service.entity.PostDislike dislike =
                    com.exe.skillverse_backend.community_service.entity.PostDislike.builder()
                            .post(post)
                            .user(user)
                            .build();
            postDislikeRepository.save(dislike);
            post.setDislikeCount(post.getDislikeCount() + 1);
        }
        postRepository.save(post);
        return toResponse(post);
    }

    @Transactional
    public CommentResponse addComment(Long postId, Long userId, CommentCreateRequest req) {
        Post post = postRepository.findById(postId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        Comment parent = null;
        if (req.getParentId() != null) parent = commentRepository.findById(req.getParentId()).orElse(null);
        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content(HtmlUtils.htmlEscape(req.getContent()))
                .parent(parent)
                .build();
        Comment saved = commentRepository.save(comment);
        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);
        return toResponse(saved);
    }

    public Page<CommentResponse> listComments(Long postId, boolean includeHidden, Pageable pageable) {
        Page<Comment> page = includeHidden
                ? commentRepository.findByPost_IdOrderByCreatedAtAsc(postId, pageable)
                : commentRepository.findByPost_IdAndHiddenFalseOrderByCreatedAtAsc(postId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional
    public void savePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId).orElseThrow();
        User user = userRepository.findById(userId).orElseThrow();
        Optional<SavedPost> existing = savedPostRepository.findByPost_IdAndUser_Id(postId, userId);
        if (existing.isEmpty()) {
            SavedPost sp = SavedPost.builder().post(post).user(user).build();
            savedPostRepository.save(sp);
        }
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow();
        if (!comment.getPost().getId().equals(postId)) throw new RuntimeException("Not Found");
        Post post = comment.getPost();
        User user = userRepository.findById(userId).orElseThrow();

        boolean isOwner = comment.getUser().getId().equals(userId);
        boolean isPostAuthor = post.getUser().getId().equals(userId);
        boolean isAdmin = user.getPrimaryRole() == PrimaryRole.ADMIN;
        if (!(isOwner || isPostAuthor || isAdmin)) throw new RuntimeException("Forbidden");

        int removed = removeCommentWithChildren(commentId);
        post.setCommentCount(Math.max(0, post.getCommentCount() - removed));
        postRepository.save(post);
    }

    private int removeCommentWithChildren(Long rootCommentId) {
        int count = 0;
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootCommentId);
        while (!stack.isEmpty()) {
            Long currentId = stack.pop();
            List<Comment> children = commentRepository.findByParent_Id(currentId);
            for (Comment child : children) {
                stack.push(child.getId());
            }
            commentRepository.deleteById(currentId);
            count++;
        }
        return count;
    }

    @Transactional
    public void hideComment(Long postId, Long commentId, Long userId, String note) {
        Comment comment = commentRepository.findById(commentId).orElseThrow();
        if (!comment.getPost().getId().equals(postId)) throw new RuntimeException("Not Found");
        Post post = comment.getPost();
        User user = userRepository.findById(userId).orElseThrow();

        boolean isOwner = comment.getUser().getId().equals(userId);
        boolean isPostAuthor = post.getUser().getId().equals(userId);
        boolean isAdmin = user.getPrimaryRole() == PrimaryRole.ADMIN;
        if (!(isOwner || isPostAuthor || isAdmin)) throw new RuntimeException("Forbidden");

        comment.setHidden(true);
        if (note != null && !note.isBlank()) comment.setModerationNote(HtmlUtils.htmlEscape(note));
        commentRepository.save(comment);
    }

    @Transactional
    public void unhideComment(Long postId, Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId).orElseThrow();
        if (!comment.getPost().getId().equals(postId)) throw new RuntimeException("Not Found");
        Post post = comment.getPost();
        User user = userRepository.findById(userId).orElseThrow();

        boolean isOwner = comment.getUser().getId().equals(userId);
        boolean isPostAuthor = post.getUser().getId().equals(userId);
        boolean isAdmin = user.getPrimaryRole() == PrimaryRole.ADMIN;
        if (!(isOwner || isPostAuthor || isAdmin)) throw new RuntimeException("Forbidden");

        comment.setHidden(false);
        commentRepository.save(comment);
    }

    @Transactional
    public void reportComment(Long postId, Long commentId, Long userId, String reason) {
        Comment comment = commentRepository.findById(commentId).orElseThrow();
        if (!comment.getPost().getId().equals(postId)) throw new RuntimeException("Not Found");
        userRepository.findById(userId).orElseThrow();
        if (reason != null && !reason.isBlank()) comment.setLastReportReason(HtmlUtils.htmlEscape(reason));
        comment.setReportCount(comment.getReportCount() + 1);
        commentRepository.save(comment);
    }

    private PostResponse toResponse(Post p) {
        java.util.List<String> tags = p.getTags() != null && !p.getTags().isEmpty() 
                ? java.util.Arrays.asList(p.getTags().split(",")) 
                : new java.util.ArrayList<>();
        
        String fullName = (p.getUser().getFirstName() != null ? p.getUser().getFirstName() : "") + 
                          (p.getUser().getLastName() != null ? " " + p.getUser().getLastName() : "");
        fullName = fullName.trim();
        if (fullName.isEmpty()) fullName = "User #" + p.getUser().getId();

        return PostResponse.builder()
                .id(p.getId())
                .userId(p.getUser().getId())
                .userFullName(fullName)
                .userAvatar(p.getUser().getAvatarUrl())
                .title(p.getTitle())
                .content(p.getContent())
                .thumbnailUrl(p.getThumbnailUrl())
                .category(p.getCategory())
                .tags(tags)
                .status(p.getStatus())
                .likeCount(p.getLikeCount())
                .dislikeCount(p.getDislikeCount())
                .commentCount(p.getCommentCount())
                .viewCount(p.getViewCount())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private CommentResponse toResponse(Comment c) {
        String fullName = (c.getUser().getFirstName() != null ? c.getUser().getFirstName() : "") + 
                          (c.getUser().getLastName() != null ? " " + c.getUser().getLastName() : "");
        fullName = fullName.trim();
        if (fullName.isEmpty()) fullName = "User #" + c.getUser().getId();

        return CommentResponse.builder()
                .id(c.getId())
                .postId(c.getPost().getId())
                .userId(c.getUser().getId())
                .userFullName(fullName)
                .userAvatar(c.getUser().getAvatarUrl())
                .content(c.getContent())
                .parentId(c.getParent() != null ? c.getParent().getId() : null)
                .createdAt(c.getCreatedAt())
                .hidden(c.isHidden())
                .reportCount(c.getReportCount())
                .moderationNote(c.getModerationNote())
                .build();
    }
}
