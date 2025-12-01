package com.exe.skillverse_backend.community_service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class PostControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setup() {
        User u = User.builder().email("it@example.com").status(com.exe.skillverse_backend.auth_service.entity.UserStatus.ACTIVE).build();
        userId = userRepository.save(u).getId();
    }

    @Test
    void create_list_detail_update_comment_like_save() throws Exception {
        String payload = "{\"title\":\"A\",\"content\":\"B\",\"status\":\"PUBLISHED\"}";

        String created = mockMvc.perform(post("/api/posts")
                .with(jwt().jwt(j -> { j.claim("userId", String.valueOf(userId)); j.subject(String.valueOf(userId)); }))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long postId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/posts")).andExpect(status().isOk());
        mockMvc.perform(get("/api/posts/" + postId)).andExpect(status().isOk());

        String upd = "{\"title\":\"C\",\"content\":\"D\",\"status\":\"DRAFT\"}";
        mockMvc.perform(put("/api/posts/" + postId)
                .with(jwt().jwt(j -> { j.claim("userId", String.valueOf(userId)); j.subject(String.valueOf(userId)); }))
                .contentType(MediaType.APPLICATION_JSON)
                .content(upd))
                .andExpect(status().isOk());

        String commentPayload = "{\"content\":\"Hi\"}";
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                .with(jwt().jwt(j -> { j.claim("userId", String.valueOf(userId)); j.subject(String.valueOf(userId)); }))
                .contentType(MediaType.APPLICATION_JSON)
                .content(commentPayload))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/posts/" + postId + "/comments")).andExpect(status().isOk());

        mockMvc.perform(post("/api/posts/" + postId + "/like")
                .with(jwt().jwt(j -> { j.claim("userId", String.valueOf(userId)); j.subject(String.valueOf(userId)); })))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/posts/" + postId + "/save")
                .with(jwt().jwt(j -> { j.claim("userId", String.valueOf(userId)); j.subject(String.valueOf(userId)); })))
                .andExpect(status().isCreated());
    }
}
