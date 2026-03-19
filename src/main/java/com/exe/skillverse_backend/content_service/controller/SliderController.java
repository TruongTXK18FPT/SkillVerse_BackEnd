package com.exe.skillverse_backend.content_service.controller;

import com.exe.skillverse_backend.content_service.dto.SliderRequest;
import com.exe.skillverse_backend.content_service.dto.SliderResponse;
import com.exe.skillverse_backend.content_service.service.SliderService;
import com.exe.skillverse_backend.shared.validation.ValidationGroups;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.groups.Default;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Slider Management", description = "Endpoints for managing home page sliders")
public class SliderController {

    private final SliderService sliderService;

    @GetMapping("/public/sliders")
    @Operation(summary = "Get active sliders", description = "Get list of active sliders for home page")
    public ResponseEntity<List<SliderResponse>> getPublicSliders(
            @RequestParam(required = false, defaultValue = "false") Boolean isLogin) {
        return ResponseEntity.ok(sliderService.getSliders(true, isLogin));
    }

    @GetMapping("/admin/sliders")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Get all sliders", description = "Get list of all sliders (admin)")
    public ResponseEntity<List<SliderResponse>> getAllSliders() {
        return ResponseEntity.ok(sliderService.getSliders(false, null));
    }

    @PostMapping(value = "/admin/sliders", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Create slider", description = "Create a new slider")
    public ResponseEntity<SliderResponse> createSlider(
            @Validated({ ValidationGroups.Create.class, Default.class }) @ModelAttribute SliderRequest request)
            throws IOException {
        return ResponseEntity.ok(sliderService.createSlider(request));
    }

    @PutMapping(value = "/admin/sliders/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Update slider", description = "Update an existing slider")
    public ResponseEntity<SliderResponse> updateSlider(
            @PathVariable UUID id,
            @Validated(Default.class) @ModelAttribute SliderRequest request) throws IOException {
        return ResponseEntity.ok(sliderService.updateSlider(id, request));
    }

    @DeleteMapping("/admin/sliders/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Delete slider", description = "Delete a slider")
    public ResponseEntity<Void> deleteSlider(@PathVariable UUID id) throws IOException {
        sliderService.deleteSlider(id);
        return ResponseEntity.noContent().build();
    }
}
