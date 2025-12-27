package com.exe.skillverse_backend.content_service.repository;

import com.exe.skillverse_backend.content_service.entity.Slider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SliderRepository extends JpaRepository<Slider, UUID> {
    List<Slider> findByIsActiveTrueOrderByDisplayOrderAsc();

    List<Slider> findByIsActiveTrueAndIsLoginOrderByDisplayOrderAsc(Boolean isLogin);

    List<Slider> findAllByOrderByDisplayOrderAsc();

    boolean existsByDisplayOrder(Integer displayOrder);
}
