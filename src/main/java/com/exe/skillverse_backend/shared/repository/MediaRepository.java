package com.exe.skillverse_backend.shared.repository;

import com.exe.skillverse_backend.shared.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaRepository extends JpaRepository<Media, Long> {

}
