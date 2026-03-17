package com.exe.skillverse_backend.skin_service.service.impl;

import com.cloudinary.Transformation;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.skin_service.dto.request.MeowlSkinRequest;
import com.exe.skillverse_backend.skin_service.dto.response.MeowlSkinResponse;
import com.exe.skillverse_backend.skin_service.entity.MeowlSkin;
import com.exe.skillverse_backend.skin_service.entity.UserSkin;
import com.exe.skillverse_backend.skin_service.repository.MeowlSkinRepository;
import com.exe.skillverse_backend.skin_service.repository.UserSkinRepository;
import com.exe.skillverse_backend.skin_service.service.SkinService;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkinServiceImpl implements SkinService {

    private final MeowlSkinRepository skinRepository;
    private final UserSkinRepository userSkinRepository;
    private final CloudinaryService cloudinaryService;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final PremiumService premiumService;

    @Value("${removebg.api-key}")
    private String removeBgApiKey;

    @Override
    @Transactional
    public MeowlSkinResponse uploadSkin(MeowlSkinRequest request, MultipartFile file) throws IOException {
        if (skinRepository.existsBySkinCode(request.getSkinCode())) {
            throw new IllegalArgumentException("Skin code already exists: " + request.getSkinCode());
        }

        // 1. Remove Background
        byte[] originalBytes = file.getBytes();
        byte[] bgRemovedBytes = removeBackground(originalBytes);

        // 2. Resize Image
        ByteArrayInputStream bais = new ByteArrayInputStream(bgRemovedBytes);
        BufferedImage bgRemovedImage = ImageIO.read(bais);
        if (bgRemovedImage == null) {
            bgRemovedImage = ImageIO.read(file.getInputStream());
        }
        BufferedImage resizedImage = resizeImage(bgRemovedImage, 268, 418);

        // 3. Convert back to MultipartFile or byte array for Cloudinary
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "png", os);
        byte[] imageBytes = os.toByteArray();
        
        MultipartFile processedFile = new CustomMultipartFile(imageBytes, file.getOriginalFilename(), file.getContentType());

        // Standard upload without background removal
        Map<String, Object> options = new HashMap<>();
        options.put("quality", "auto");
        options.put("fetch_format", "auto");

        Map<String, Object> uploadResult = cloudinaryService.uploadImageWithOptions(processedFile, "meowl-skin", options);
        String imageUrl = (String) uploadResult.get("secure_url");
        
        MeowlSkin skin = MeowlSkin.builder()
                .skinCode(request.getSkinCode())
                .name(request.getName())
                .nameVi(request.getNameVi())
                .isPremium(request.getIsPremium())
                .price(request.getPrice())
                .imageUrl(imageUrl)
                .build();

        skin = skinRepository.save(skin);
        return mapToResponse(skin, false, false);
    }

    @Override
    @Transactional
    public MeowlSkinResponse updateSkin(Long id, MeowlSkinRequest request) {
        MeowlSkin skin = skinRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Skin not found"));
        
        skin.setName(request.getName());
        skin.setNameVi(request.getNameVi());
        skin.setPrice(request.getPrice());
        skin.setPremium(request.getIsPremium());
        // Image update is separate usually, or handled if file is provided
        
        skin = skinRepository.save(skin);
        return mapToResponse(skin, false, false);
    }

    @Override
    @Transactional
    public void deleteSkin(Long id) {
        if (!skinRepository.existsById(id)) {
            throw new IllegalArgumentException("Skin not found");
        }
        // Check if anyone owns it? Maybe allow deleting but keep user ownership records?
        // Or cascade delete? For now, we'll just delete the skin definition.
        // If there are foreign keys, this might fail.
        // Usually we should soft delete or check constraints.
        // Assuming strict delete for now as requested.
        skinRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void purchaseSkin(Long userId, String skinCode) {
        MeowlSkin skin = skinRepository.findBySkinCode(skinCode)
                .orElseThrow(() -> new IllegalArgumentException("Skin not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (userSkinRepository.findByUserIdAndSkinId(userId, skin.getId()).isPresent()) {
            throw new IllegalStateException("User already owns this skin");
        }

        // Check if free
        if (skin.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            // Deduct coins
            walletService.deductCoins(userId, skin.getPrice().longValue(), 
                    WalletTransaction.TransactionType.SPEND_COINS,
                    "Purchase skin: " + skin.getName(), 
                    "SKIN_PURCHASE", 
                    "SKIN_" + skin.getId());
        } else {
             // Free skin, no deduction
        }

        // If Premium only
        if (skin.isPremium()) {
             if (!premiumService.hasActivePremiumSubscription(userId)) {
                 throw new IllegalStateException("This skin is reserved for Premium members only.");
             }
        }

        UserSkin userSkin = UserSkin.builder()
                .user(user)
                .skin(skin)
                .isActive(false) // Purchased but not equipped by default
                .build();

        userSkinRepository.save(userSkin);
    }

    @Override
    public List<MeowlSkinResponse> getAllSkins(Long userId) {
        List<MeowlSkin> skins = skinRepository.findAll();
        List<UserSkin> userSkins = userSkinRepository.findByUserId(userId);
        
        // Map skinId -> UserSkin
        Map<Long, UserSkin> userSkinMap = userSkins.stream()
            .collect(Collectors.toMap(us -> us.getSkin().getId(), us -> us));

        return skins.stream()
                .map(skin -> {
                    UserSkin us = userSkinMap.get(skin.getId());
                    boolean isOwned = us != null;
                    boolean isSelected = us != null && us.isActive();
                    return mapToResponse(skin, isOwned, isSelected);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<MeowlSkinResponse> getMySkins(Long userId) {
        return userSkinRepository.findByUserId(userId).stream()
                .map(us -> mapToResponse(us.getSkin(), true, us.isActive()))
                .collect(Collectors.toList());
    }

    @Override
    public List<MeowlSkinResponse> getSkinLeaderboard(Long userId) {
        List<Object[]> results = skinRepository.findSkinsWithSalesCount();
        
        Map<Long, UserSkin> userSkinMap = new HashMap<>();
        if (userId != null) {
            List<UserSkin> userSkins = userSkinRepository.findByUserId(userId);
            userSkinMap = userSkins.stream()
                .collect(Collectors.toMap(us -> us.getSkin().getId(), us -> us));
        }

        final Map<Long, UserSkin> finalUserSkinMap = userSkinMap;

        return results.stream()
                .map(record -> {
                    MeowlSkin skin = (MeowlSkin) record[0];
                    Long count = (Long) record[1];
                    Long used = record[2] != null ? ((Number) record[2]).longValue() : 0L;
                    
                    UserSkin us = finalUserSkinMap.get(skin.getId());
                    boolean isOwned = us != null;
                    boolean isSelected = us != null && us.isActive();
                    
                    return MeowlSkinResponse.builder()
                            .id(skin.getId())
                            .skinCode(skin.getSkinCode())
                            .name(skin.getName())
                            .nameVi(skin.getNameVi())
                            .imageUrl(skin.getImageUrl())
                            .price(skin.getPrice())
                            .isPremium(skin.isPremium())
                            .isOwned(isOwned)
                            .isSelected(isSelected)
                            .purchasedCount(count)
                            .usedCount(used)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void selectSkin(Long userId, String skinCode) {
        if ("default".equals(skinCode)) {
             List<UserSkin> userSkins = userSkinRepository.findByUserId(userId);
             for (UserSkin us : userSkins) {
                 if (us.isActive()) {
                     us.setActive(false);
                     userSkinRepository.save(us);
                 }
             }
             return;
        }

        MeowlSkin skin = skinRepository.findBySkinCode(skinCode)
                .orElseThrow(() -> new IllegalArgumentException("Skin not found"));
        
        List<UserSkin> userSkins = userSkinRepository.findByUserId(userId);
        
        boolean found = false;
        for (UserSkin us : userSkins) {
            if (us.getSkin().getId().equals(skin.getId())) {
                us.setActive(true);
                found = true;
            } else {
                if (us.isActive()) {
                    us.setActive(false);
                }
            }
        }
        
        if (!found) {
             throw new IllegalStateException("User does not own this skin");
        }
        
        userSkinRepository.saveAll(userSkins);
    }

    @Override
    public MeowlSkin getSkinByCode(String skinCode) {
         return skinRepository.findBySkinCode(skinCode)
                .orElseThrow(() -> new IllegalArgumentException("Skin not found"));
    }

    private MeowlSkinResponse mapToResponse(MeowlSkin skin, boolean isOwned, boolean isSelected) {
        return MeowlSkinResponse.builder()
                .id(skin.getId())
                .skinCode(skin.getSkinCode())
                .name(skin.getName())
                .nameVi(skin.getNameVi())
                .imageUrl(skin.getImageUrl())
                .price(skin.getPrice())
                .isPremium(skin.isPremium())
                .isOwned(isOwned)
                .isSelected(isSelected)
                .build();
    }

    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_DEFAULT);
        BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
        return outputImage;
    }

    private byte[] removeBackground(byte[] imageBytes) {
        if (removeBgApiKey == null || removeBgApiKey.isBlank()) {
            log.warn("Remove.bg API key is missing. Skipping background removal.");
            return imageBytes;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Api-Key", removeBgApiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("image_file", new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "image.png";
                }
            });
            body.add("size", "auto");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<byte[]> response = restTemplate.postForEntity(
                    "https://api.remove.bg/v1.0/removebg",
                    requestEntity,
                    byte[].class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Background removed successfully via Remove.bg");
                return response.getBody();
            } else {
                log.error("Remove.bg API failed with status: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to remove background via Remove.bg", e);
        }
        return imageBytes; // Fallback to original
    }

    // Helper class for MultipartFile
    private static class CustomMultipartFile implements MultipartFile {
        private final byte[] imgContent;
        private final String originalFilename;
        private final String contentType;

        public CustomMultipartFile(byte[] imgContent, String originalFilename, String contentType) {
            this.imgContent = imgContent;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
        }

        @Override
        public String getName() { return "file"; }
        @Override
        public String getOriginalFilename() { return originalFilename; }
        @Override
        public String getContentType() { return contentType; }
        @Override
        public boolean isEmpty() { return imgContent == null || imgContent.length == 0; }
        @Override
        public long getSize() { return imgContent.length; }
        @Override
        public byte[] getBytes() throws IOException { return imgContent; }
        @Override
        public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(imgContent); }
        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(imgContent);
            }
        }
    }
}
