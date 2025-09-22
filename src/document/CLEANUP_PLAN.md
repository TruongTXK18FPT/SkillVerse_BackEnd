# 🧹 **Cleanup Plan: Remove Unused Registration Endpoints**

## 🎯 **What Needs to be Removed**

Since we're properly separating registration by domain services:

### **✅ KEEP in auth_service**
- `POST /api/auth/login` - Authentication
- `POST /api/auth/refresh` - Token refresh
- `POST /api/auth/logout` - Authentication
- `POST /api/auth/register` - Simple auth-only registration (optional)

### **❌ REMOVE from auth_service**
- `POST /api/register/user` → **MOVED** to `/api/user/register`
- `POST /api/register/mentor` → **SHOULD MOVE** to `/api/mentor/register`
- `POST /api/register/recruiter` → **SHOULD MOVE** to `/api/recruiter/register`
- `POST /api/register/verify-otp` → **SHOULD BE** in each service
- `POST /api/register/resend-otp` → **SHOULD BE** in each service

### **🗑️ REMOVE Entire Files**
- `RegistrationController.java` - No longer needed
- `MultiRoleRegistrationService.java` - No longer needed
- Related DTOs that are duplicated

## 🔧 **Implementation Plan**

1. ✅ **Remove RegistrationController entirely**
2. ✅ **Remove MultiRoleRegistrationService**
3. ✅ **Clean up SecurityConfig** (remove old endpoints)
4. ✅ **Remove unused DTOs**
5. ✅ **Update documentation**

## 📝 **New Clean Architecture**

```
auth_service/
├── controller/AuthController.java ✅ (login, logout, refresh only)
└── service/AuthService.java ✅ (authentication only)

user_service/
├── controller/UserRegistrationController.java ✅ 
├── service/UserRegistrationService.java ✅
└── endpoints: /api/user/register, /api/user/verify-email

mentor_service/
├── controller/MentorRegistrationController.java (to be created)
├── service/MentorRegistrationService.java (to be created)  
└── endpoints: /api/mentor/register, /api/mentor/verify-email

business_service/
├── controller/RecruiterRegistrationController.java (to be created)
├── service/RecruiterRegistrationService.java (to be created)
└── endpoints: /api/recruiter/register, /api/recruiter/verify-email
```

## 🎯 **Benefits After Cleanup**

- ✅ **No Confusion**: Clear separation of concerns
- ✅ **No Duplication**: Each service handles its own registration
- ✅ **Cleaner APIs**: Logical endpoint grouping
- ✅ **Better Maintainability**: Easier to find and modify code
- ✅ **Proper Architecture**: Domain-driven design principles

Let's proceed with the cleanup! 🚀