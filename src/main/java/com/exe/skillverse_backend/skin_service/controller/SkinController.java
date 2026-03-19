package com.exe.skillverse_backend.skin_service.controller;

import com.exe.skillverse_backend.skin_service.dto.request.MeowlSkinRequest;
import com.exe.skillverse_backend.skin_service.dto.response.MeowlSkinResponse;
import com.exe.skillverse_backend.skin_service.service.SkinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skins")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Meowl Skin Management", description = "APIs for managing Meowl Skins (Admin Upload & User Purchase)")
public class SkinController {

    private final SkinService skinService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Upload new skin (Admin)", description = "Uploads a new skin, resizes it to 268x418, and removes background.")
    public ResponseEntity<MeowlSkinResponse> uploadSkin(
            @Parameter(description = "Skin image file") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Skin code (unique)") @RequestParam("skinCode") String skinCode,
            @Parameter(description = "Name (English)") @RequestParam("name") String name,
            @Parameter(description = "Name (Vietnamese)") @RequestParam("nameVi") String nameVi,
            @Parameter(description = "Is Premium?") @RequestParam(value = "isPremium", defaultValue = "false") Boolean isPremium,
            @Parameter(description = "Price (0 for free)") @RequestParam("price") BigDecimal price
    ) throws IOException {
        
        MeowlSkinRequest request = new MeowlSkinRequest();
        request.setSkinCode(skinCode);
        request.setName(name);
        request.setNameVi(nameVi);
        request.setIsPremium(isPremium);
        request.setPrice(price);
        
        return ResponseEntity.ok(skinService.uploadSkin(request, file));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Update skin details", description = "Update skin name, price, etc.")
    public ResponseEntity<MeowlSkinResponse> updateSkin(
            @PathVariable Long id,
            @RequestBody MeowlSkinRequest request
    ) {
        return ResponseEntity.ok(skinService.updateSkin(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Delete skin", description = "Delete a skin by ID")
    public ResponseEntity<Void> deleteSkin(@PathVariable Long id) {
        skinService.deleteSkin(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{skinCode}/purchase")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Purchase skin", description = "Purchase a skin using wallet balance")
    public ResponseEntity<String> purchaseSkin(
            @PathVariable String skinCode,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        skinService.purchaseSkin(userId, skinCode);
        return ResponseEntity.ok("Skin purchased successfully");
    }

    @PostMapping("/{skinCode}/select")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Select skin", description = "Select a skin to be equipped")
    public ResponseEntity<String> selectSkin(
            @PathVariable String skinCode,
            Authentication authentication
    ) {
        Long userId = Long.parseLong(authentication.getName());
        skinService.selectSkin(userId, skinCode);
        return ResponseEntity.ok("Skin selected successfully");
    }

    @GetMapping
    @Operation(summary = "Get all skins", description = "Get all available skins with ownership status (public access, optional auth)")
    public ResponseEntity<List<MeowlSkinResponse>> getAllSkins(Authentication authentication) {
        Long userId = authentication != null ? Long.parseLong(authentication.getName()) : null;
        return ResponseEntity.ok(skinService.getAllSkins(userId));
    }

    @GetMapping("/my-skins")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my skins", description = "Get only skins owned by the user")
    public ResponseEntity<List<MeowlSkinResponse>> getMySkins(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(skinService.getMySkins(userId));
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Get skin leaderboard", description = "Get skins sorted by purchase count (public access, optional auth)")
    public ResponseEntity<List<MeowlSkinResponse>> getSkinLeaderboard(Authentication authentication) {
        Long userId = authentication != null ? Long.parseLong(authentication.getName()) : null;
        return ResponseEntity.ok(skinService.getSkinLeaderboard(userId));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Get skin stats", description = "Get all skins with purchase counts for admin")
    public ResponseEntity<List<MeowlSkinResponse>> getSkinStats() {
        return ResponseEntity.ok(skinService.getSkinLeaderboard(null));
    }
}
