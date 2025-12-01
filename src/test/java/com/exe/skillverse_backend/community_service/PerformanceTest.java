package com.exe.skillverse_backend.community_service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.community_service.dto.request.PostCreateRequest;
import com.exe.skillverse_backend.community_service.dto.response.PostResponse;
import com.exe.skillverse_backend.community_service.entity.PostStatus;
import com.exe.skillverse_backend.community_service.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class PerformanceTest {
    @Autowired
    private PostService postService;
    @Autowired
    private UserRepository userRepository;

    @Test
    void seed_many_posts() {
        User u = User.builder().email("perf@example.com").status(com.exe.skillverse_backend.auth_service.entity.UserStatus.ACTIVE).build();
        Long userId = userRepository.save(u).getId();
        int n = 500;
        for (int i = 0; i < n; i++) {
            PostCreateRequest req = new PostCreateRequest();
            req.setTitle("T" + i);
            req.setContent("C" + i);
            req.setStatus(PostStatus.PUBLISHED);
            PostResponse r = postService.createPost(userId, req);
            assertNotNull(r.getId());
        }
    }
}
