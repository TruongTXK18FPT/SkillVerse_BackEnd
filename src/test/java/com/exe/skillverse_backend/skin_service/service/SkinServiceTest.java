package com.exe.skillverse_backend.skin_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.skin_service.entity.MeowlSkin;
import com.exe.skillverse_backend.skin_service.entity.UserSkin;
import com.exe.skillverse_backend.skin_service.repository.MeowlSkinRepository;
import com.exe.skillverse_backend.skin_service.repository.UserSkinRepository;
import com.exe.skillverse_backend.skin_service.service.impl.SkinServiceImpl;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkinServiceTest {

    @Mock
    private MeowlSkinRepository skinRepository;

    @Mock
    private UserSkinRepository userSkinRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @Mock
    private WalletService walletService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PremiumService premiumService;

    @InjectMocks
    private SkinServiceImpl skinService;

    private User user;
    private MeowlSkin regularSkin;
    private MeowlSkin premiumSkin;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .build();

        regularSkin = MeowlSkin.builder()
                .id(1L)
                .skinCode("regular_skin")
                .name("Regular Skin")
                .price(BigDecimal.valueOf(100))
                .isPremium(false)
                .build();

        premiumSkin = MeowlSkin.builder()
                .id(2L)
                .skinCode("premium_skin")
                .name("Premium Skin")
                .price(BigDecimal.valueOf(200))
                .isPremium(true)
                .build();
    }

    @Test
    void purchaseSkin_RegularSkin_Success() {
        when(skinRepository.findBySkinCode("regular_skin")).thenReturn(Optional.of(regularSkin));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSkinRepository.findByUserIdAndSkinId(1L, 1L)).thenReturn(Optional.empty());
        // walletService.deductCash is void, so no when needed if not checking return

        skinService.purchaseSkin(1L, "regular_skin");

        verify(walletService).deductCash(eq(1L), eq(BigDecimal.valueOf(100)), anyString(), anyString(), anyString());
        verify(userSkinRepository).save(any(UserSkin.class));
    }

    @Test
    void purchaseSkin_PremiumSkin_WithPremiumSubscription_Success() {
        when(skinRepository.findBySkinCode("premium_skin")).thenReturn(Optional.of(premiumSkin));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSkinRepository.findByUserIdAndSkinId(1L, 2L)).thenReturn(Optional.empty());
        when(premiumService.hasActivePremiumSubscription(1L)).thenReturn(true);

        skinService.purchaseSkin(1L, "premium_skin");

        verify(walletService).deductCash(eq(1L), eq(BigDecimal.valueOf(200)), anyString(), anyString(), anyString());
        verify(userSkinRepository).save(any(UserSkin.class));
    }

    @Test
    void purchaseSkin_PremiumSkin_WithoutPremiumSubscription_ThrowsException() {
        when(skinRepository.findBySkinCode("premium_skin")).thenReturn(Optional.of(premiumSkin));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userSkinRepository.findByUserIdAndSkinId(1L, 2L)).thenReturn(Optional.empty());
        when(premiumService.hasActivePremiumSubscription(1L)).thenReturn(false);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            skinService.purchaseSkin(1L, "premium_skin");
        });

        assertEquals("This skin is reserved for Premium members only.", exception.getMessage());
        verify(userSkinRepository, never()).save(any(UserSkin.class));
    }
}
