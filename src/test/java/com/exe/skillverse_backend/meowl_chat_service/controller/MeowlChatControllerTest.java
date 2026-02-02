package com.exe.skillverse_backend.meowl_chat_service.controller;

import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatRequest;
import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatResponse;
import com.exe.skillverse_backend.meowl_chat_service.service.MeowlChatService;
import com.exe.skillverse_backend.meowl_chat_service.service.MeowlReminderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MeowlChatController.class)
@AutoConfigureMockMvc(addFilters = false) // Disable security filters for simple unit test
public class MeowlChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeowlChatService meowlChatService;

    @MockBean
    private MeowlReminderService meowlReminderService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void chat_Success() throws Exception {
        MeowlChatRequest request = MeowlChatRequest.builder()
                .message("Hello")
                .userId(1L)
                .build();
        
        MeowlChatResponse response = MeowlChatResponse.builder()
                .message("Meow! Hi!")
                .success(true)
                .build();
        
        when(meowlChatService.chat(any(MeowlChatRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/meowl/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Meow! Hi!"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getChatHistory_Success() throws Exception {
        MeowlChatRequest.ChatMessage msg = new MeowlChatRequest.ChatMessage("user", "Hi");
        when(meowlChatService.getChatHistory(anyLong())).thenReturn(Collections.singletonList(msg));

        mockMvc.perform(get("/api/v1/meowl/history/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Hi"));
    }

    @Test
    void clearChatHistory_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/meowl/history/1"))
                .andExpect(status().isOk());
    }
}
