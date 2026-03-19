package com.exe.skillverse_backend.skin_service.service;

import com.exe.skillverse_backend.skin_service.dto.request.MeowlSkinRequest;
import com.exe.skillverse_backend.skin_service.dto.response.MeowlSkinResponse;
import com.exe.skillverse_backend.skin_service.entity.MeowlSkin;
import java.io.IOException;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface SkinService {
    MeowlSkinResponse uploadSkin(MeowlSkinRequest request, MultipartFile file) throws IOException;
    MeowlSkinResponse updateSkin(Long id, MeowlSkinRequest request);
    void deleteSkin(Long id);
    void purchaseSkin(Long userId, String skinCode);
    List<MeowlSkinResponse> getAllSkins(Long userId); // userId to check ownership
    List<MeowlSkinResponse> getMySkins(Long userId);
    List<MeowlSkinResponse> getSkinLeaderboard(Long userId);
    void selectSkin(Long userId, String skinCode);
    MeowlSkin getSkinByCode(String skinCode);
}
