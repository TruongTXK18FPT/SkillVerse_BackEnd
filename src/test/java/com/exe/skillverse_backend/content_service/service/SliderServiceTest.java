package com.exe.skillverse_backend.content_service.service;

import com.exe.skillverse_backend.content_service.dto.SliderRequest;
import com.exe.skillverse_backend.content_service.dto.SliderResponse;
import com.exe.skillverse_backend.content_service.entity.Slider;
import com.exe.skillverse_backend.content_service.repository.SliderRepository;
import com.exe.skillverse_backend.content_service.service.impl.SliderServiceImpl;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SliderServiceTest {

    @Mock
    private SliderRepository sliderRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private SliderServiceImpl sliderService;

    private Slider slider;
    private MultipartFile mockFile;

    @BeforeEach
    void setUp() {
        slider = Slider.builder()
                .id(UUID.randomUUID())
                .title("Test Slider")
                .description("Test Description")
                .imageUrl("http://example.com/image.jpg")
                .publicId("test_public_id")
                .displayOrder(1)
                .isActive(true)
                .build();

        mockFile = mock(MultipartFile.class);
    }

    @Test
    void createSlider_Success() throws IOException {
        // Arrange
        SliderRequest request = SliderRequest.builder()
                .title("New Slider")
                .description("Description")
                .image(mockFile)
                .displayOrder(2)
                .ctaText("Click Me")
                .ctaLink("/link")
                .build();

        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "http://cloudinary.com/new_image.jpg");
        uploadResult.put("public_id", "new_public_id");

        when(cloudinaryService.uploadImageWithOptions(any(), eq("sliders"), anyMap())).thenReturn(uploadResult);
        when(sliderRepository.save(any(Slider.class))).thenAnswer(invocation -> {
            Slider s = invocation.getArgument(0);
            s.setId(UUID.randomUUID()); // Simulate ID generation
            return s;
        });

        // Act
        SliderResponse result = sliderService.createSlider(request);

        // Assert
        assertNotNull(result);
        assertEquals(request.getTitle(), result.getTitle());
        assertEquals("http://cloudinary.com/new_image.jpg", result.getImageUrl());
        assertEquals(request.getCtaText(), result.getCtaText());
        assertEquals(request.getCtaLink(), result.getCtaLink());
        verify(cloudinaryService).uploadImageWithOptions(any(), eq("sliders"), anyMap());
    }

    @Test
    void createSlider_DuplicateOrder_ThrowsException() {
        // Arrange
        SliderRequest request = SliderRequest.builder()
                .title("New Slider")
                .description("desc")
                .image(mockFile)
                .displayOrder(1)
                .ctaText("cta")
                .ctaLink("link")
                .build();

        when(sliderRepository.existsByDisplayOrder(request.getDisplayOrder())).thenReturn(true);

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            sliderService.createSlider(request);
        });

        assertTrue(exception.getMessage().contains("already exists"));
        verify(sliderRepository, never()).save(any(Slider.class));
    }

    @Test
    void createSlider_NegativeOrder_ThrowsException() {
        // Arrange
        SliderRequest request = SliderRequest.builder()
                .title("New Slider")
                .description("desc")
                .image(mockFile)
                .displayOrder(-1)
                .ctaText("cta")
                .ctaLink("link")
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            sliderService.createSlider(request);
        });

        assertEquals("Display order cannot be negative.", exception.getMessage());
        verify(sliderRepository, never()).save(any(Slider.class));
    }

    @Test
    void updateSlider_Success() throws IOException {
        // Arrange
        UUID id = slider.getId();
        when(sliderRepository.findById(id)).thenReturn(Optional.of(slider));
        when(sliderRepository.save(any(Slider.class))).thenReturn(slider);

        SliderRequest request = SliderRequest.builder()
                .title("Updated Title")
                .build();

        // Act
        SliderResponse result = sliderService.updateSlider(id, request);

        // Assert
        assertEquals(request.getTitle(), result.getTitle());
        verify(cloudinaryService, never()).deleteFile(anyString(), anyString());
    }

    @Test
    void updateSlider_DuplicateOrder_ThrowsException() {
        // Arrange
        UUID id = slider.getId();
        Integer newOrder = 2;
        when(sliderRepository.findById(id)).thenReturn(Optional.of(slider));
        when(sliderRepository.existsByDisplayOrder(newOrder)).thenReturn(true);

        SliderRequest request = SliderRequest.builder()
                .displayOrder(newOrder)
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            sliderService.updateSlider(id, request);
        });

        assertTrue(exception.getMessage().contains("already exists"));
        verify(sliderRepository, never()).save(any(Slider.class));
    }

    @Test
    void updateSlider_NegativeOrder_ThrowsException() {
        // Arrange
        UUID id = slider.getId();
        Integer newOrder = -1;
        when(sliderRepository.findById(id)).thenReturn(Optional.of(slider));

        SliderRequest request = SliderRequest.builder()
                .displayOrder(newOrder)
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            sliderService.updateSlider(id, request);
        });

        assertEquals("Display order cannot be negative.", exception.getMessage());
        verify(sliderRepository, never()).save(any(Slider.class));
    }

    @Test
    void deleteSlider_Success() throws IOException {
        // Arrange
        UUID id = slider.getId();
        when(sliderRepository.findById(id)).thenReturn(Optional.of(slider));

        // Act
        sliderService.deleteSlider(id);

        // Assert
        verify(cloudinaryService).deleteFile(eq("test_public_id"), eq("image"));
        verify(sliderRepository).delete(slider);
    }
}
