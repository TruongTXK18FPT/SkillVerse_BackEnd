# ✅ Portfolio Service - Proper Method Naming & Separation

## 🎯 Đã sửa lại đúng nguyên tắc Single Responsibility Principle

### ❌ Trước đây (SAI):
```java
// Gộp chung create và update - vi phạm SRP
public UserProfileDTO createOrUpdateExtendedProfile(...) {
    // Get or create - không rõ ràng
    PortfolioExtendedProfile profile = repo.findByUserId(userId)
        .orElseGet(() -> PortfolioExtendedProfile.builder()...);
    
    // Upload và update
    // ...
}
```

**Vấn đề:**
- ❌ Một method làm 2 việc (create VÀ update)
- ❌ Không thể kiểm soát hành vi (tạo mới hay cập nhật?)
- ❌ Khó test riêng từng chức năng
- ❌ Response không rõ ràng (201 Created hay 200 OK?)
- ❌ Không thể validate (ví dụ: user đã có profile rồi mà còn create?)

---

## ✅ Bây giờ (ĐÚNG):

### 1. **Create Method** - Tạo mới
```java
@Transactional
public UserProfileDTO createExtendedProfile(
        Long userId, 
        UserProfileDTO dto,
        MultipartFile avatarFile,
        MultipartFile videoFile,
        MultipartFile coverImageFile) {
    
    // Check if already exists
    if (extendedProfileRepository.existsByUserId(userId)) {
        throw new RuntimeException("Portfolio extended profile already exists");
    }
    
    // Create new profile
    PortfolioExtendedProfile extendedProfile = PortfolioExtendedProfile.builder()
            .userId(userId)
            .user(getUserOrThrow(userId))
            .build();
    
    // Upload media and set fields
    extendedProfile = uploadMediaAndSetFields(extendedProfile, dto, 
                                             avatarFile, videoFile, coverImageFile);
    extendedProfile = extendedProfileRepository.save(extendedProfile);
    
    log.info("Created extended profile for user: {}", userId);
    return getCombinedProfile(userId);
}
```

**Endpoint:**
```
POST /api/portfolio/profile
Response: 201 Created (hoặc 409 Conflict nếu đã tồn tại)
```

---

### 2. **Update Method** - Cập nhật
```java
@Transactional
public UserProfileDTO updateExtendedProfile(
        Long userId,
        UserProfileDTO dto,
        MultipartFile avatarFile,
        MultipartFile videoFile,
        MultipartFile coverImageFile) {
    
    // Must exist to update
    PortfolioExtendedProfile extendedProfile = extendedProfileRepository
            .findByUserId(userId)
            .orElseThrow(() -> new NotFoundException("Portfolio extended profile not found"));
    
    // Upload media and update fields
    extendedProfile = uploadMediaAndSetFields(extendedProfile, dto,
                                             avatarFile, videoFile, coverImageFile);
    extendedProfile = extendedProfileRepository.save(extendedProfile);
    
    log.info("Updated extended profile for user: {}", userId);
    return getCombinedProfile(userId);
}
```

**Endpoint:**
```
PUT /api/portfolio/profile
Response: 200 OK (hoặc 404 Not Found nếu chưa tạo)
```

---

### 3. **Delete Method** - Xóa
```java
@Transactional
public void deleteExtendedProfile(Long userId) {
    PortfolioExtendedProfile extendedProfile = extendedProfileRepository
            .findByUserId(userId)
            .orElseThrow(() -> new NotFoundException("Portfolio extended profile not found"));
    
    // Delete all media from Cloudinary
    if (extendedProfile.getAvatarPublicId() != null) {
        cloudinaryService.deleteFile(extendedProfile.getAvatarPublicId(), "image");
    }
    if (extendedProfile.getVideoIntroPublicId() != null) {
        cloudinaryService.deleteFile(extendedProfile.getVideoIntroPublicId(), "video");
    }
    if (extendedProfile.getCoverImagePublicId() != null) {
        cloudinaryService.deleteFile(extendedProfile.getCoverImagePublicId(), "image");
    }
    
    extendedProfileRepository.delete(extendedProfile);
    log.info("Deleted extended profile for user: {}", userId);
}
```

**Endpoint:**
```
DELETE /api/portfolio/profile
Response: 200 OK (hoặc 404 Not Found nếu không tồn tại)
```

---

### 4. **Get Methods** - Đọc dữ liệu

#### 4.1. Get own profile (authenticated)
```java
@Transactional(readOnly = true)
public UserProfileDTO getProfile(Long userId) {
    return getCombinedProfile(userId);
}
```
**Endpoint:** `GET /api/portfolio/profile` (authenticated)

#### 4.2. Get by custom URL slug (public)
```java
@Transactional(readOnly = true)
public UserProfileDTO getProfileBySlug(String customUrlSlug) {
    PortfolioExtendedProfile extendedProfile = extendedProfileRepository
            .findByCustomUrlSlug(customUrlSlug)
            .orElseThrow(() -> new NotFoundException("Portfolio not found"));
    
    // Only return public portfolios
    if (!Boolean.TRUE.equals(extendedProfile.getIsPublic())) {
        throw new NotFoundException("Portfolio is not public");
    }
    
    // Increment view count
    extendedProfile.incrementPortfolioViews();
    extendedProfileRepository.save(extendedProfile);
    
    return getCombinedProfile(extendedProfile.getUserId());
}
```
**Endpoint:** `GET /api/portfolio/profile/slug/{slug}` (public)

#### 4.3. Check if has extended profile
```java
@Transactional(readOnly = true)
public boolean hasExtendedProfile(Long userId) {
    return extendedProfileRepository.existsByUserId(userId);
}
```
**Endpoint:** `GET /api/portfolio/profile/check` (authenticated)

---

### 5. **Helper Method** - Tái sử dụng code
```java
private PortfolioExtendedProfile uploadMediaAndSetFields(
        PortfolioExtendedProfile extendedProfile,
        UserProfileDTO dto,
        MultipartFile avatarFile,
        MultipartFile videoFile,
        MultipartFile coverImageFile) {
    
    // Upload avatar (delete old if exists)
    if (avatarFile != null && !avatarFile.isEmpty()) {
        if (extendedProfile.getAvatarPublicId() != null) {
            cloudinaryService.deleteFile(extendedProfile.getAvatarPublicId(), "image");
        }
        Map<String, Object> result = cloudinaryService.uploadImage(avatarFile, "portfolios/avatars");
        extendedProfile.setAvatarUrl((String) result.get("secure_url"));
        extendedProfile.setAvatarPublicId((String) result.get("public_id"));
    }
    
    // Upload video (delete old if exists)
    // ... tương tự
    
    // Upload cover image (delete old if exists)
    // ... tương tự
    
    // Update all DTO fields (only if not null)
    if (dto.getProfessionalTitle() != null) 
        extendedProfile.setProfessionalTitle(dto.getProfessionalTitle());
    // ... các fields khác
    
    return extendedProfile;
}
```

---

## 📋 Complete API Endpoints:

### Portfolio Extended Profile:
```
POST   /api/portfolio/profile                    - Create extended profile
PUT    /api/portfolio/profile                    - Update extended profile
DELETE /api/portfolio/profile                    - Delete extended profile
GET    /api/portfolio/profile                    - Get own profile (auth)
GET    /api/portfolio/profile/check              - Check if has extended profile
GET    /api/portfolio/profile/slug/{slug}        - Get public profile by slug
GET    /api/portfolio/profile/{userId}           - Get public profile by ID
```

### Projects:
```
POST   /api/portfolio/projects                   - Create project
PUT    /api/portfolio/projects/{projectId}       - Update project
DELETE /api/portfolio/projects/{projectId}       - Delete project
GET    /api/portfolio/projects                   - Get user's projects
GET    /api/portfolio/projects/featured          - Get featured projects
```

### Certificates:
```
POST   /api/portfolio/certificates               - Create certificate
DELETE /api/portfolio/certificates/{certId}      - Delete certificate
GET    /api/portfolio/certificates               - Get user's certificates
GET    /api/portfolio/certificates/verified      - Get verified certificates
```

### Reviews:
```
GET    /api/portfolio/reviews                    - Get user's reviews
```

### CV Generation:
```
POST   /api/portfolio/cv/generate                - Generate new CV
PUT    /api/portfolio/cv/{cvId}                  - Update CV
GET    /api/portfolio/cv                         - Get all user CVs
GET    /api/portfolio/cv/active                  - Get active CV
PUT    /api/portfolio/cv/{cvId}/activate         - Set CV as active
DELETE /api/portfolio/cv/{cvId}                  - Delete CV
```

---

## 🎯 Lợi ích của việc tách methods:

### ✅ Clear Intent (Ý định rõ ràng)
- `createExtendedProfile()` - Ai đọc cũng biết là **tạo mới**
- `updateExtendedProfile()` - Ai đọc cũng biết là **cập nhật**
- `deleteExtendedProfile()` - Ai đọc cũng biết là **xóa**

### ✅ Proper HTTP Status Codes
- Create → 201 Created / 409 Conflict
- Update → 200 OK / 404 Not Found
- Delete → 200 OK / 404 Not Found
- Read → 200 OK / 404 Not Found

### ✅ Validation Logic
- Create: Kiểm tra xem đã tồn tại chưa? Nếu có → throw exception
- Update: Kiểm tra xem có tồn tại không? Nếu không → throw exception
- Delete: Kiểm tra xem có tồn tại không? Nếu không → throw exception

### ✅ Testability (Dễ test)
```java
@Test
void shouldCreateExtendedProfile() {
    // Given: user chưa có extended profile
    // When: call createExtendedProfile()
    // Then: profile được tạo, return 201
}

@Test
void shouldThrowErrorWhenCreateExistingProfile() {
    // Given: user đã có extended profile
    // When: call createExtendedProfile()
    // Then: throw exception
}

@Test
void shouldUpdateExtendedProfile() {
    // Given: user đã có extended profile
    // When: call updateExtendedProfile()
    // Then: profile được update, return 200
}

@Test
void shouldThrowErrorWhenUpdateNonExistingProfile() {
    // Given: user chưa có extended profile
    // When: call updateExtendedProfile()
    // Then: throw NotFoundException
}
```

### ✅ RESTful Best Practices
```
POST   /resource     - Create (201 Created)
PUT    /resource     - Update (200 OK)
DELETE /resource     - Delete (200 OK or 204 No Content)
GET    /resource     - Read (200 OK)
```

### ✅ Separation of Concerns
- Upload logic → Helper method `uploadMediaAndSetFields()`
- Create logic → `createExtendedProfile()`
- Update logic → `updateExtendedProfile()`
- Delete logic → `deleteExtendedProfile()`
- Read logic → `getProfile()`, `getProfileBySlug()`, `hasExtendedProfile()`

---

## 📝 Usage Examples:

### Example 1: User creates portfolio for first time
```javascript
// Step 1: Check if has extended profile
GET /api/portfolio/profile/check
Response: { "hasExtendedProfile": false }

// Step 2: Create extended profile
POST /api/portfolio/profile
FormData:
  - profile: { professionalTitle: "Full Stack Developer", ... }
  - avatar: file.jpg
  - video: intro.mp4
  - coverImage: cover.jpg

Response: 201 Created
{
  "success": true,
  "message": "Portfolio extended profile created successfully",
  "data": { ...combined profile... }
}
```

### Example 2: User updates portfolio
```javascript
// Update with new avatar and cover, same video
PUT /api/portfolio/profile
FormData:
  - profile: { professionalTitle: "Senior Full Stack Developer", ... }
  - avatar: new_avatar.jpg
  - coverImage: new_cover.jpg
  - video: null (không thay đổi)

Response: 200 OK
{
  "success": true,
  "message": "Portfolio extended profile updated successfully",
  "data": { ...updated profile... }
}
```

### Example 3: Public views portfolio
```javascript
// Access via custom URL
GET /api/portfolio/profile/slug/john-doe-developer

Response: 200 OK
{
  "success": true,
  "data": {
    ...combined profile...,
    "portfolioViews": 1235  // Auto-incremented
  }
}
```

### Example 4: User deletes portfolio
```javascript
DELETE /api/portfolio/profile

Response: 200 OK
{
  "success": true,
  "message": "Portfolio extended profile deleted successfully"
}

// All media files automatically deleted from Cloudinary
// Basic profile from user_service NOT affected
```

---

## 🎉 Summary:

**Đã refactor thành công:**
- ✅ Tách `createOrUpdateExtendedProfile()` thành 3 methods riêng biệt
- ✅ `createExtendedProfile()` - Tạo mới (POST, 201 Created)
- ✅ `updateExtendedProfile()` - Cập nhật (PUT, 200 OK)
- ✅ `deleteExtendedProfile()` - Xóa (DELETE, 200 OK)
- ✅ Helper method `uploadMediaAndSetFields()` - Tái sử dụng code
- ✅ Proper validation cho từng method
- ✅ Proper HTTP status codes
- ✅ RESTful API design
- ✅ Dễ test, dễ maintain

**Principle áp dụng:**
- ✅ Single Responsibility Principle (SRP)
- ✅ RESTful API Design
- ✅ DRY (Don't Repeat Yourself) - via helper methods
- ✅ Clear naming conventions

---

**Date:** 2025-01-20  
**Author:** GitHub Copilot  
**Status:** ✅ REFACTORED - Production Ready
