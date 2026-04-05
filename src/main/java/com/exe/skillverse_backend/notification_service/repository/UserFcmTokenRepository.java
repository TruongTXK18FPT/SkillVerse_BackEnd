package com.exe.skillverse_backend.notification_service.repository;

import com.exe.skillverse_backend.notification_service.entity.UserFcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, Long> {

    List<UserFcmToken> findByUserIdAndActiveTrue(Long userId);

    List<UserFcmToken> findByUserId(Long userId);

    Optional<UserFcmToken> findByDeviceToken(String deviceToken);

    boolean existsByDeviceToken(String deviceToken);

    void deleteByDeviceToken(String deviceToken);

    void deleteByUserId(Long userId);
}
