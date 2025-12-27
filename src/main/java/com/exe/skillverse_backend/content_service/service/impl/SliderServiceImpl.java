package com.exe.skillverse_backend.content_service.service.impl;

import com.cloudinary.Transformation;
import com.exe.skillverse_backend.content_service.dto.SliderRequest;
import com.exe.skillverse_backend.content_service.dto.SliderResponse;
import com.exe.skillverse_backend.content_service.entity.Slider;
import com.exe.skillverse_backend.content_service.repository.SliderRepository;
import com.exe.skillverse_backend.content_service.service.SliderService;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SliderServiceImpl implements SliderService {

    private final SliderRepository sliderRepository;
    private final CloudinaryService cloudinaryService;

    @Override
    @Transactional
    public SliderResponse createSlider(SliderRequest request) throws IOException {
        log.info("Creating new slider: {}", request.getTitle());

        Integer displayOrder = request.getDisplayOrder();
        if (displayOrder != null) {
            if (displayOrder < 0) {
                throw new IllegalArgumentException("Display order cannot be negative.");
            }
            if (sliderRepository.existsByDisplayOrder(displayOrder)) {
                throw new IllegalArgumentException(
                        "Display order " + displayOrder + " already exists. Please choose another order.");
            }
        }

        // Upload image with transformation
        Map<String, Object> options = new HashMap<>();
        options.put("transformation", new Transformation()
                .width(1920).height(800).crop("fill").gravity("auto")
                .quality("auto").fetchFormat("auto"));

        Map<String, Object> uploadResult = cloudinaryService.uploadImageWithOptions(request.getImage(), "sliders",
                options);

        String imageUrl = (String) uploadResult.get("secure_url");
        String publicId = (String) uploadResult.get("public_id");

        Slider slider = Slider.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .imageUrl(imageUrl)
                .publicId(publicId)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .ctaText(request.getCtaText())
                .ctaLink(request.getCtaLink())
                .isActive(true)
                .build();

        Slider savedSlider = sliderRepository.save(slider);
        return mapToResponse(savedSlider);
    }

    @Override
    @Transactional
    public SliderResponse updateSlider(UUID id, SliderRequest request) throws IOException {
        log.info("Updating slider: {}", id);
        Slider slider = sliderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Slider not found with id: " + id));

        if (request.getTitle() != null)
            slider.setTitle(request.getTitle());
        if (request.getDescription() != null)
            slider.setDescription(request.getDescription());

        Integer displayOrder = request.getDisplayOrder();
        if (displayOrder != null) {
            if (displayOrder < 0) {
                throw new IllegalArgumentException("Display order cannot be negative.");
            }
            if (!displayOrder.equals(slider.getDisplayOrder()) && sliderRepository.existsByDisplayOrder(displayOrder)) {
                throw new IllegalArgumentException(
                        "Display order " + displayOrder + " already exists. Please choose another order.");
            }
            slider.setDisplayOrder(displayOrder);
        }
        if (request.getIsActive() != null)
            slider.setIsActive(request.getIsActive());
        if (request.getCtaText() != null)
            slider.setCtaText(request.getCtaText());
        if (request.getCtaLink() != null)
            slider.setCtaLink(request.getCtaLink());

        if (request.getImage() != null && !request.getImage().isEmpty()) {
            // Delete old image
            if (slider.getPublicId() != null) {
                cloudinaryService.deleteFile(slider.getPublicId(), "image");
            }

            // Upload new image
            Map<String, Object> options = new HashMap<>();
            options.put("transformation", new Transformation()
                    .width(1920).height(800).crop("fill").gravity("auto")
                    .quality("auto").fetchFormat("auto"));

            Map<String, Object> uploadResult = cloudinaryService.uploadImageWithOptions(request.getImage(), "sliders",
                    options);
            slider.setImageUrl((String) uploadResult.get("secure_url"));
            slider.setPublicId((String) uploadResult.get("public_id"));
        }

        Slider savedSlider = sliderRepository.save(slider);
        return mapToResponse(savedSlider);
    }

    @Override
    @Transactional
    public void deleteSlider(UUID id) throws IOException {
        Slider slider = sliderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Slider not found with id: " + id));
        if (slider.getPublicId() != null) {
            cloudinaryService.deleteFile(slider.getPublicId(), "image");
        }
        sliderRepository.delete(slider);
    }

    @Override
    public SliderResponse getSlider(UUID id) {
        Slider slider = sliderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Slider not found with id: " + id));
        return mapToResponse(slider);
    }

    @Override
    public List<SliderResponse> getAllSliders(boolean onlyActive) {
        List<Slider> sliders;
        if (onlyActive) {
            sliders = sliderRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        } else {
            sliders = sliderRepository.findAllByOrderByDisplayOrderAsc();
        }
        return sliders.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateSliderOrder(List<UUID> sliderIds) {
        for (int i = 0; i < sliderIds.size(); i++) {
            UUID id = sliderIds.get(i);
            Slider slider = sliderRepository.findById(id).orElse(null);
            if (slider != null) {
                slider.setDisplayOrder(i);
                sliderRepository.save(slider);
            }
        }
    }

    private SliderResponse mapToResponse(Slider slider) {
        return SliderResponse.builder()
                .id(slider.getId())
                .title(slider.getTitle())
                .description(slider.getDescription())
                .imageUrl(slider.getImageUrl())
                .ctaText(slider.getCtaText())
                .ctaLink(slider.getCtaLink())
                .isActive(slider.getIsActive())
                .displayOrder(slider.getDisplayOrder())
                .createdAt(slider.getCreatedAt())
                .updatedAt(slider.getUpdatedAt())
                .build();
    }
}
