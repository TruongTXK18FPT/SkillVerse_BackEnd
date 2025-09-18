package com.exe.skillverse_backend.auth_service.entity;

/**
 * Simplified user status enum
 * INACTIVE: User registered but pending email verification or admin approval
 * ACTIVE: User is fully activated and can use the system
 */
public enum UserStatus {
    INACTIVE, // User not yet activated (pending email verification or admin approval)
    ACTIVE // User is fully activated and can login
}