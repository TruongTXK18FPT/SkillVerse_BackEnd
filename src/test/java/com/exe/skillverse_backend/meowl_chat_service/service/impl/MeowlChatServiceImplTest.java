package com.exe.skillverse_backend.meowl_chat_service.service.impl;

import com.exe.skillverse_backend.meowl_chat_service.config.MeowlConfig;
import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatRequest;
import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatResponse;
import com.exe.skillverse_backend.meowl_chat_service.entity.MeowlChatMessage;
import com.exe.skillverse_backend.meowl_chat_service.repository.MeowlChatMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MeowlChatServiceImplTest {

    @Mock
    private MeowlConfig meowlConfig;

    @Mock
    private RestTemplate meowlRestTemplate;

    @Mock
    private MeowlReminderServiceImpl reminderService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MistralAiChatModel mistralAiChatModel;

    @Mock
    private MeowlChatMessageRepository chatMessageRepository;

    @InjectMocks
    private MeowlChatServiceImpl meowlChatService;

    private MeowlChatRequest request;

    @BeforeEach
    void setUp() {
        request = MeowlChatRequest.builder()
                .message("Hello Meowl")
                .language("en")
                .userId(1L)
                .includeReminders(true)
                .build();
    }

    @Test
    void chat_Success_Gemini() {
        // Arrange
        when(meowlConfig.getApiUrl()).thenReturn("https://api.gemini.com");
        when(meowlConfig.getApiKey()).thenReturn("test-key");

        String mockGeminiResponse = "{\"candidates\": [{\"content\": {\"parts\": [{\"text\": \"Meow! Hello!\"}]}}]}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(mockGeminiResponse, HttpStatus.OK);

        when(meowlRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);
        
        when(chatMessageRepository.save(any(MeowlChatMessage.class))).thenReturn(new MeowlChatMessage());

        // Act
        MeowlChatResponse response = meowlChatService.chat(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertTrue(response.getMessage().contains("Meow! Hello!"));
        verify(chatMessageRepository, times(2)).save(any(MeowlChatMessage.class)); // 1 user, 1 assistant
    }

    @Test
    void getChatHistory_Success() {
        // Arrange
        MeowlChatMessage msg1 = MeowlChatMessage.builder().role("user").content("Hi").build();
        MeowlChatMessage msg2 = MeowlChatMessage.builder().role("assistant").content("Hello").build();
        
        when(chatMessageRepository.findTop50ByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(Arrays.asList(msg2, msg1)); // Newest first

        // Act
        List<MeowlChatRequest.ChatMessage> history = meowlChatService.getChatHistory(1L);

        // Assert
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).getRole()); // Oldest first
        assertEquals("assistant", history.get(1).getRole());
    }

    @Test
    void clearChatHistory_Success() {
        // Act
        meowlChatService.clearChatHistory(1L);

        // Assert
        verify(chatMessageRepository, times(1)).deleteByUserId(1L);
    }
}
