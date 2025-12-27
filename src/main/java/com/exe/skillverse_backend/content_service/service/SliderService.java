package com.exe.skillverse_backend.content_service.service;

import com.exe.skillverse_backend.content_service.dto.SliderRequest;
import com.exe.skillverse_backend.content_service.dto.SliderResponse;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

public interface SliderService {
    SliderResponse createSlider(SliderRequest request) throws IOException;

    SliderResponse updateSlider(UUID id, SliderRequest request) throws IOException;

    void deleteSlider(UUID id) throws IOException;

    SliderResponse getSlider(UUID id);

    List<SliderResponse> getAllSliders(boolean onlyActive);

    void updateSliderOrder(List<UUID> sliderIds);
}
