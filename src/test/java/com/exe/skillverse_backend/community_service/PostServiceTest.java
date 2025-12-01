package com.exe.skillverse_backend.community_service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.community_service.dto.request.CommentCreateRequest;
import com.exe.skillverse_backend.community_service.dto.request.PostCreateRequest;
import com.exe.skillverse_backend.community_service.dto.request.PostUpdateRequest;
import com.exe.skillverse_backend.community_service.dto.response.CommentResponse;
import com.exe.skillverse_backend.community_service.dto.response.PostResponse;
import com.exe.skillverse_backend.community_service.entity.PostStatus;
import com.exe.skillverse_backend.community_service.service.PostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class PostServiceTest {

    @Autowired
    private PostService postService;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setup() {
        User u = User.builder().email("test@example.com").status(com.exe.skillverse_backend.auth_service.entity.UserStatus.ACTIVE).build();
        userId = userRepository.save(u).getId();
    }

    @Test
    void create_update_like_comment_flow() {
        PostCreateRequest create = new PostCreateRequest();
        create.setTitle("Hello");
        create.setContent("World");
        create.setStatus(PostStatus.PUBLISHED);
        PostResponse created = postService.createPost(userId, create);
        assertNotNull(created.getId());
        assertEquals(0, created.getLikeCount());
        assertEquals(0, created.getCommentCount());

        PostUpdateRequest update = new PostUpdateRequest();
        update.setTitle("New");
        update.setContent("Content");
        update.setStatus(PostStatus.DRAFT);
        PostResponse updated = postService.updatePost(created.getId(), userId, update);
        assertEquals("New", updated.getTitle());

        PostResponse liked = postService.likePost(created.getId(), userId);
        assertEquals(1, liked.getLikeCount());

        CommentCreateRequest commentReq = new CommentCreateRequest();
        commentReq.setContent("Nice");
        CommentResponse comment = postService.addComment(created.getId(), userId, commentReq);
        assertNotNull(comment.getId());
        PostResponse afterComment = postService.getPost(created.getId());
        assertEquals(1, afterComment.getCommentCount());
    }
}
