package com.exe.skillverse_backend.skin_service.service.impl;

import com.cloudinary.Transformation;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.skin_service.dto.request.MeowlSkinRequest;
import com.exe.skillverse_backend.skin_service.dto.response.MeowlSkinResponse;
import com.exe.skillverse_backend.skin_service.entity.MeowlSkin;
import com.exe.skillverse_backend.skin_service.entity.UserSkin;
import com.exe.skillverse_backend.skin_service.repository.MeowlSkinRepository;
import com.exe.skillverse_backend.skin_service.repository.UserSkinRepository;
import com.exe.skillverse_backend.skin_service.service.SkinService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

    @Override
    @Transactional
    public MeowlSkinResponse uploadSkin(MeowlSkinRequest request, MultipartFile file) throws IOException {
        if (skinRepository.existsBySkinCode(request.getSkinCode())) {
            throw new IllegalArgumentException("Skin code already exists: " + request.getSkinCode());
        }

        // 1. Resize Image
        BufferedImage originalImage = ImageIO.read(file.getInputStream());
        BufferedImage resizedImage = resizeImage(originalImage, 268, 418);

        // 2. Remove Background (Mock/Placeholder) -> Now handled by Cloudinary
        // BufferedImage processedImage = removeBackground(resizedImage);

        // 3. Convert back to MultipartFile or byte array for Cloudinary
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "png", os);
        byte[] imageBytes = os.toByteArray();
        
        MultipartFile processedFile = new CustomMultipartFile(imageBytes, file.getOriginalFilename(), file.getContentType());

        // Use Cloudinary AI Background Removal (Only for new uploads as requested)
        Map<String, Object> options = new HashMap<>();
        
        // Use Transformation to enforce background removal on the main asset
        // 'effect' -> 'bgremoval' (or 'background_removal' depending on add-on)
        // quality: auto, fetch_format: auto are standard optimizations
        options.put("transformation", new Transformation()
                .effect("bgremoval")
                .quality("auto")
                .fetchFormat("auto"));
        
        // Also set the flag just in case
        options.put("background_removal", "cloudinary_ai");

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
        if (skin.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
            // Deduct balance
            walletService.deductCash(userId, skin.getPrice(), 
                    "Purchase skin: " + skin.getName(), 
                    "SKIN_PURCHASE", 
                    "SKIN_" + skin.getId());
        } else {
             // Free skin, no deduction
        }

        // If Premium only? 
        if (skin.isPremium()) {
             // Check if user is premium? Or is it just a tag?
             // User entity has `PrimaryRole` but maybe not "Premium" status directly visible here.
             // Assuming purchase is allowed if they have money, or if "isPremium" means it costs money/premium currency?
             // The requirement says "mua premium là sở hữu được".
             // If this means "Buying the Premium Plan gives you this skin", that's different.
             // But here I am implementing "Purchase Skin".
             // I will assume if it has a price, they pay.
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

    private BufferedImage removeBackground(BufferedImage image) {
        // TODO: Implement background removal logic (e.g. using external API or AI library)
        // For now, return original image as placeholder
        return image;
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
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                fos.write(imgContent);
            }
        }
    }
}
