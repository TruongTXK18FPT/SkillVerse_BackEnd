package com.exe.skillverse_backend.user_service.repository;

import com.exe.skillverse_backend.user_service.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    @Query("SELECT up FROM UserProfile up LEFT JOIN FETCH up.avatarMedia WHERE up.userId = :userId")
    Optional<UserProfile> findByUserId(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);

    @Query("SELECT up FROM UserProfile up WHERE up.region = :region")
    List<UserProfile> findByRegion(@Param("region") String region);

    @Query("SELECT up FROM UserProfile up WHERE up.companyId = :companyId")
    List<UserProfile> findByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT up FROM UserProfile up WHERE up.fullName LIKE %:name%")
    List<UserProfile> findByFullNameContaining(@Param("name") String name);

    @Query("SELECT up FROM UserProfile up LEFT JOIN FETCH up.avatarMedia WHERE up.user.email = :email")
    Optional<UserProfile> findByUserEmail(@Param("email") String email);
}
