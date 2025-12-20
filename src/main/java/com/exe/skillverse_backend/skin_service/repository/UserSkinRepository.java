package com.exe.skillverse_backend.skin_service.repository;

import com.exe.skillverse_backend.skin_service.entity.UserSkin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSkinRepository extends JpaRepository<UserSkin, Long> {
    List<UserSkin> findByUserId(Long userId);
    Optional<UserSkin> findByUserIdAndSkinId(Long userId, Long skinId);
    boolean existsByUserIdAndSkinId(Long userId, Long skinId);
}
