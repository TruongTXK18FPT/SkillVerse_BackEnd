package com.exe.skillverse_backend.skin_service.repository;

import com.exe.skillverse_backend.skin_service.entity.MeowlSkin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

@Repository
public interface MeowlSkinRepository extends JpaRepository<MeowlSkin, Long> {
    Optional<MeowlSkin> findBySkinCode(String skinCode);
    boolean existsBySkinCode(String skinCode);

    @Query("SELECT s, COUNT(us), SUM(CASE WHEN us.isActive = true THEN 1 ELSE 0 END) FROM MeowlSkin s LEFT JOIN UserSkin us ON s.id = us.skin.id GROUP BY s ORDER BY COUNT(us) DESC")
    List<Object[]> findSkinsWithSalesCount();
}
