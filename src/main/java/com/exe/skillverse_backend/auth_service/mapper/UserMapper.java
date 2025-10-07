package com.exe.skillverse_backend.auth_service.mapper;
import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.auth_service.entity.Role;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.*;

import java.util.Collection;
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

    default Set<String> toRoleNames(Set<Role> roles) {
        if (roles == null) return null;
        return roles.stream()
                .filter(Objects::nonNull)
                .map(Role::getName)     // đổi thành getCode()/getRoleName() nếu bạn đặt tên khác
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}

