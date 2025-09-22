package com.exe.skillverse_backend.course_service.mapper;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.mapper.UserMapper;
import com.exe.skillverse_backend.course_service.dto.purchasedto.*;
import com.exe.skillverse_backend.course_service.entity.*;
import com.exe.skillverse_backend.shared.config.CustomMapperConfig;
import org.mapstruct.*;

@Mapper(config = CustomMapperConfig.class, uses = {UserMapper.class, CourseMapper.class})
public interface PurchaseMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "courseId", source = "course.id")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "paymentId", constant = "")  // Since paymentId is commented out in entity
    @Mapping(target = "purchasedAt", source = "purchasedAt")
    @Mapping(target = "couponCode", source = "couponCode")
    CoursePurchaseDTO toDto(CoursePurchase purchase);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "course", source = "course")
    @Mapping(target = "price", source = "price")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "purchasedAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "couponCode", source = "couponCode")
    CoursePurchase toEntity(CoursePurchaseRequestDTO purchaseRequest, User user, Course course);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "course", ignore = true)
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "currency", ignore = true)
    @Mapping(target = "purchasedAt", ignore = true)
    @Mapping(target = "couponCode", ignore = true)
    @Mapping(target = "status", source = "status")
    void updatePurchaseStatus(@MappingTarget CoursePurchase purchase, CoursePurchaseDTO purchaseDto);
}