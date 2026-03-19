package com.exe.skillverse_backend.meowl_chat_service.repository;

import com.exe.skillverse_backend.meowl_chat_service.entity.MeowlUserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MeowlUserPreferenceRepository extends JpaRepository<MeowlUserPreference, Long> {
}
