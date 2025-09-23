package com.exe.skillverse_backend.auth_service.entity;

public enum PrimaryRole {
    USER, // Regular user with basic access
    MENTOR, // Approved mentor who can offer services
    RECRUITER, // Approved recruiter who can post jobs
    ADMIN // Administrator with full access
}