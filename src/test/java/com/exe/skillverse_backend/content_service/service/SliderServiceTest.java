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
import java.util.List;
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
                .isLogin(false)
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

    @Test
    void getSliders_PublicNotLoggedIn_ReturnsPublicSliders() {
        // Arrange
        when(sliderRepository.findByIsActiveTrueAndIsLoginOrderByDisplayOrderAsc(false))
                .thenReturn(List.of(slider));

        // Act
        List<SliderResponse> result = sliderService.getSliders(true, false);

        // Assert
        assertEquals(1, result.size());
        verify(sliderRepository).findByIsActiveTrueAndIsLoginOrderByDisplayOrderAsc(false);
    }

    @Test
    void getSliders_PublicLoggedIn_ReturnsLoggedInSliders() {
        // Arrange
        when(sliderRepository.findByIsActiveTrueAndIsLoginOrderByDisplayOrderAsc(true))
                .thenReturn(List.of(slider));

        // Act
        List<SliderResponse> result = sliderService.getSliders(true, true);

        // Assert
        assertEquals(1, result.size());
        verify(sliderRepository).findByIsActiveTrueAndIsLoginOrderByDisplayOrderAsc(true);
    }

    @Test
    void getSliders_Admin_ReturnsAllSliders() {
        // Arrange
        when(sliderRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(slider));

        // Act
        List<SliderResponse> result = sliderService.getSliders(false, null);

        // Assert
        assertEquals(1, result.size());
        verify(sliderRepository).findAllByOrderByDisplayOrderAsc();
    }

    @Test
    void getSlider_Success() {
        // Arrange
        UUID id = slider.getId();
        when(sliderRepository.findById(id)).thenReturn(Optional.of(slider));

        // Act
        SliderResponse result = sliderService.getSlider(id);

        // Assert
        assertNotNull(result);
        assertEquals(slider.getTitle(), result.getTitle());
        verify(sliderRepository).findById(id);
    }

    @Test
    void getSlider_NotFound_ThrowsException() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(sliderRepository.findById(id)).thenReturn(Optional.empty());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            sliderService.getSlider(id);
        });

        assertTrue(exception.getMessage().contains("Slider not found"));
    }

    @Test
    void updateSliderOrder_Success() {
        // Arrange
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<UUID> sliderIds = List.of(id1, id2);

        Slider slider1 = new Slider();
        slider1.setId(id1);
        Slider slider2 = new Slider();
        slider2.setId(id2);

        when(sliderRepository.findById(id1)).thenReturn(Optional.of(slider1));
        when(sliderRepository.findById(id2)).thenReturn(Optional.of(slider2));

        // Act
        sliderService.updateSliderOrder(sliderIds);

        // Assert
        verify(sliderRepository).save(slider1);
        verify(sliderRepository).save(slider2);
        assertEquals(0, slider1.getDisplayOrder());
        assertEquals(1, slider2.getDisplayOrder());
    }

    @Test
    void createSlider_WithIsLogin_Success() throws IOException {
        // Arrange
        SliderRequest request = SliderRequest.builder()
                .title("Login Slider")
                .isLogin(true)
                .image(mockFile)
                .build();

        Map<String, Object> uploadResult = new HashMap<>();
        uploadResult.put("secure_url", "url");
        uploadResult.put("public_id", "id");

        when(cloudinaryService.uploadImageWithOptions(any(), eq("sliders"), anyMap())).thenReturn(uploadResult);
        when(sliderRepository.save(any(Slider.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        SliderResponse result = sliderService.createSlider(request);

        // Assert
        assertTrue(result.getIsLogin());
    }
}
