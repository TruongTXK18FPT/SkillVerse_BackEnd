package com.exe.skillverse_backend.auth_service.mapper;
import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import org.hibernate.Hibernate;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", config = CustomMapperConfig.class)
public interface UserMapper {

    @Mappings({
        @Mapping(target = "firstName", source = "firstName"),
        @Mapping(target = "lastName", source = "lastName"),
        @Mapping(target = "fullName",
                 expression = "java(concatName(user.getFirstName(), user.getLastName()))"),
        @Mapping(target = "roles",
                 expression = "java(toRoleNames(user.getRoles()))")
    })
    UserDto toDto(User user);

    // Map list/collection tiện cho service
    List<UserDto> toDtos(Collection<User> users);

    // ===== Helpers =====
    default String concatName(String first, String last) {
        String f = first == null ? "" : first.trim();
        String l = last  == null ? "" : last.trim();
        String full = (f + " " + l).trim();
        return full.isEmpty() ? null : full;
    }

    /**
     * Convert Role entities to role names.
     * Handles LAZY loading safely - returns empty set if roles not initialized.
     */
    default Set<String> toRoleNames(Set<Role> roles) {
        if (roles == null) {
            return Collections.emptySet();
        }
        // ✅ SAFE: Check if LAZY collection is initialized before accessing
        if (!Hibernate.isInitialized(roles)) {
            // Return empty set instead of throwing LazyInitializationException
            return Collections.emptySet();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(Role::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}

