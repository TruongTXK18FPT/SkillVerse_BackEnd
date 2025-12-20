package com.exe.skillverse_backend.skin_service.repository;

import com.exe.skillverse_backend.skin_service.entity.MeowlSkin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MeowlSkinRepository extends JpaRepository<MeowlSkin, Long> {
    Optional<MeowlSkin> findBySkinCode(String skinCode);
    boolean existsBySkinCode(String skinCode);
}
