# SkillVerse Backend - Tổng Hợp Tính Năng

> Tài liệu tổng hợp tất cả các feature và function của hệ thống SkillVerse Backend (Spring Boot)

---

## Mục Lục

1. [Authentication & Authorization](#1-authentication--authorization)
2. [User Management](#2-user-management)
3. [Course Management](#3-course-management)
4. [Mentor & Mentorship](#4-mentor--mentorship)
5. [Mentor Booking](#5-mentor-booking)
6. [AI Roadmap](#6-ai-roadmap)
7. [AI Chat](#7-ai-chat)
8. [Study Planner](#8-study-planner)
9. [Wallet & Payment](#9-wallet--payment)
10. [Premium Subscription](#10-premium-subscription)
11. [Portfolio & CV](#11-portfolio--cv)
12. [Job & Recruitment](#12-job--recruitment)
13. [Community & Blog](#13-community--blog)
14. [Gamification](#14-gamification)
15. [Notification](#15-notification)
16. [Support & Ticket](#16-support--ticket)
17. [Admin Management](#17-admin-management)
18. [Shared Services](#18-shared-services)

---

## 1. Authentication & Authorization

### 1.1 Authentication Features

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Login** | Đăng nhập bằng email/password | `POST /api/auth/login` |
| **Google OAuth** | Đăng nhập bằng Google ID Token | `POST /api/auth/google` |
| **Refresh Token** | Làm mới access token | `POST /api/auth/refresh` |
| **Logout** | Đăng xuất, vô hiệu hóa token | `POST /api/auth/logout` |
| **Verify Token** | Xác thực token còn hiệu lực | `GET /api/auth/verify` |
| **Verify Email** | Xác thực email bằng OTP | `POST /api/auth/verify-email` |
| **Resend OTP** | Gửi lại mã OTP | `POST /api/auth/resend-otp` |
| **Forgot Password** | Yêu cầu reset password (gửi OTP) | `POST /api/auth/forgot-password` |
| **Reset Password** | Reset password với OTP | `POST /api/auth/reset-password` |
| **Set Password** | Đặt password cho user Google OAuth | `POST /api/auth/set-password` |
| **Change Password** | Đổi password (user đã login) | `POST /api/auth/change-password` |

### 1.2 Authorization

- **JWT Token**: Sử dụng JWT với access token và refresh token
- **Roles**: USER, MENTOR, ADMIN, CONTENT_ADMIN, RECRUITER, PARENT
- **Primary Role**: Xác định role chính của user
- **Permission**: Role-based access control (RBAC)

---

## 2. User Management

### 2.1 User Profile

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Profile** | Lấy thông tin profile của user | `GET /api/users/profile` |
| **Update Profile** | Cập nhật thông tin profile | `PUT /api/users/profile` |
| **Upload Avatar** | Tải lên ảnh đại diện | `POST /api/users/avatar` |
| **Get User by ID** | Lấy thông tin user theo ID | `GET /api/users/{userId}` |
| **Search Users** | Tìm kiếm user | `GET /api/users/search` |

### 2.2 User Registration

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Register** | Đăng ký tài khoản mới | `POST /api/users/register` |
| **Verify Email** | Xác thực email sau đăng ký | `POST /api/auth/verify-email` |

### 2.3 User Skills

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Add Skill** | Thêm kỹ năng cho user | `POST /api/users/skills` |
| **Remove Skill** | Xóa kỹ năng | `DELETE /api/users/skills/{skillId}` |
| **Get User Skills** | Lấy danh sách kỹ năng | `GET /api/users/skills` |

---

## 3. Course Management

### 3.1 Course CRUD

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Course** | Tạo khóa học mới (Mentor/Admin) | `POST /api/courses` |
| **Update Course** | Cập nhật khóa học | `PUT /api/courses/{courseId}` |
| **Delete Course** | Xóa khóa học | `DELETE /api/courses/{courseId}` |
| **Get Course** | Lấy chi tiết khóa học | `GET /api/courses/{courseId}` |
| **List Courses** | Danh sách khóa học (phân trang, lọc) | `GET /api/courses` |
| **Get Courses by Author** | Khóa học của một mentor | `GET /api/courses/by-author/{authorId}` |
| **Submit Course** | Nộp khóa học để duyệt | `POST /api/courses/{courseId}/submit` |

### 3.2 Module & Lesson

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Module** | Tạo module mới | `POST /api/modules` |
| **Update Module** | Cập nhật module | `PUT /api/modules/{moduleId}` |
| **Delete Module** | Xóa module | `DELETE /api/modules/{moduleId}` |
| **List Modules** | Danh sách module trong khóa học | `GET /api/courses/{courseId}/modules` |
| **Create Lesson** | Tạo bài học | `POST /api/lessons` |
| **Update Lesson** | Cập nhật bài học | `PUT /api/lessons/{lessonId}` |
| **Delete Lesson** | Xóa bài học | `DELETE /api/lessons/{lessonId}` |
| **Get Lesson** | Lấy chi tiết bài học | `GET /api/lessons/{lessonId}` |
| **Update Lesson Progress** | Cập nhật tiến độ học | `PUT /api/lessons/{lessonId}/progress` |

### 3.3 Quiz & Exam

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Quiz** | Tạo bài quiz | `POST /api/quizzes` |
| **Update Quiz** | Cập nhật quiz | `PUT /api/quizzes/{quizId}` |
| **Submit Quiz** | Nộp bài quiz | `POST /api/quizzes/{quizId}/submit` |
| **Get Quiz Result** | Xem kết quả quiz | `GET /api/quizzes/{quizId}/result` |
| **Get Quiz Questions** | Lấy câu hỏi quiz | `GET /api/quizzes/{quizId}/questions` |

### 3.4 Assignment

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Assignment** | Tạo bài tập | `POST /api/assignments` |
| **Submit Assignment** | Nộp bài tập | `POST /api/assignments/{assignmentId}/submit` |
| **Grade Assignment** | Chấm bài tập (Mentor) | `PUT /api/assignments/{submissionId}/grade` |
| **Get Submissions** | Lấy danh sách bài nộp | `GET /api/assignments/{assignmentId}/submissions` |

### 3.5 Enrollment

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Enroll Course** | Đăng ký khóa học | `POST /api/enrollments` |
| **Get Enrollment** | Lấy thông tin đăng ký | `GET /api/enrollments/{enrollmentId}` |
| **List My Enrollments** | Danh sách khóa học đã đăng ký | `GET /api/enrollments/my` |
| **Update Progress** | Cập nhật tiến độ học | `PUT /api/enrollments/{enrollmentId}/progress` |

### 3.6 Certificate

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Issue Certificate** | Cấp chứng chỉ | `POST /api/certificates/issue` |
| **Get Certificate** | Lấy thông tin chứng chỉ | `GET /api/certificates/{certificateId}` |
| **Verify Certificate** | Xác thực chứng chỉ | `GET /api/certificates/verify/{code}` |
| **Download Certificate** | Tải chứng chỉ PDF | `GET /api/certificates/{certificateId}/download` |

### 3.7 Coding Exercise (Codelab)

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Exercise** | Tạo bài tập code | `POST /api/codelabs/exercises` |
| **Submit Code** | Nộp bài code | `POST /api/codelabs/exercises/{exerciseId}/submit` |
| **Run Tests** | Chạy test cases | `POST /api/codelabs/exercises/{exerciseId}/run` |
| **Get Test Cases** | Lấy danh sách test cases | `GET /api/codelabs/exercises/{exerciseId}/tests` |

---

## 4. Mentor & Mentorship

### 4.1 Mentor Profile

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get All Mentors** | Lấy danh sách mentor đã duyệt | `GET /api/mentors` |
| **Get Mentor Profile** | Lấy profile mentor | `GET /api/mentors/{mentorId}/profile` |
| **Get My Profile** | Lấy profile mentor hiện tại | `GET /api/mentors/profile` |
| **Update Profile** | Cập nhật profile | `PUT /api/mentors/profile` |
| **Upload Avatar** | Tải ảnh đại diện | `POST /api/mentors/avatar` |
| **Upload Signature** | Tải chữ ký (system drawing) | `POST /api/mentors/signature/system` |
| **Get Skills** | Lấy danh sách skill của mentor | `GET /api/mentors/skills` |
| **Get Leaderboard** | Bảng xếp hạng mentor | `GET /api/mentors/leaderboard` |
| **Get Total Students** | Số lượng student đã dạy | `GET /api/mentors/{mentorId}/stats/total-students` |

### 4.2 Mentor Registration

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Register as Mentor** | Đăng ký làm mentor | `POST /api/mentors/register` |
| **Get Registration Status** | Trạng thái đăng ký | `GET /api/mentors/register/status` |

### 4.3 Favorite Mentor

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Add Favorite** | Thêm mentor yêu thích | `POST /api/mentors/favorite/{mentorId}` |
| **Remove Favorite** | Bỏ mentor yêu thích | `DELETE /api/mentors/favorite/{mentorId}` |
| **Get Favorites** | Danh sách mentor yêu thích | `GET /api/mentors/favorites` |

---

## 5. Mentor Booking

### 5.1 Booking Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Booking Intent** | Tạo yêu cầu đặt lịch (thanh toán) | `POST /api/mentor-bookings/intent` |
| **Create with Wallet** | Đặt lịch bằng ví | `POST /api/mentor-bookings/wallet` |
| **Approve Booking** | Mentor duyệt booking | `PUT /api/mentor-bookings/{id}/approve` |
| **Reject Booking** | Mentor từ chối | `PUT /api/mentor-bookings/{id}/reject` |
| **Start Session** | Bắt đầu buổi học | `PUT /api/mentor-bookings/{id}/start` |
| **Complete Session** | Hoàn tất buổi học | `PUT /api/mentor-bookings/{id}/complete` |
| **Cancel Booking** | Hủy đặt lịch (trước 1 ngày) | `DELETE /api/mentor-bookings/{id}` |
| **Rate Mentor** | Đánh giá sau buổi học | `POST /api/mentor-bookings/{id}/rating` |
| **Get My Bookings** | Danh sách booking của tôi | `GET /api/mentor-bookings/me` |
| **Download Invoice** | Tải hóa đơn PDF | `GET /api/mentor-bookings/{id}/invoice` |

### 5.2 Mentor Availability

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Set Availability** | Đặt lịch rảnh | `POST /api/mentor-availability` |
| **Get Availability** | Lấy lịch rảnh | `GET /api/mentor-availability/{mentorId}` |
| **Update Availability** | Cập nhật lịch rảnh | `PUT /api/mentor-availability/{id}` |
| **Delete Availability** | Xóa lịch rảnh | `DELETE /api/mentor-availability/{id}` |

### 5.3 Booking Reviews

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Reviews** | Lấy đánh giá booking | `GET /api/booking-reviews` |
| **Create Review** | Tạo đánh giá | `POST /api/booking-reviews` |

---

## 6. AI Roadmap

### 6.1 Roadmap Generation

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Generate Roadmap** | Tạo lộ trình học AI | `POST /api/v1/ai/roadmap/generate` |
| **Pre-validate Request** | Kiểm tra trước khi generate | `POST /api/v1/ai/roadmap/validate` |
| **Clarify Request** | Sinh câu hỏi làm rõ | `POST /api/v1/ai/roadmap/clarify` |

### 6.2 Roadmap Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get User Roadmaps** | Danh sách roadmap của user | `GET /api/v1/ai/roadmap` |
| **Get Roadmap by ID** | Chi tiết một roadmap | `GET /api/v1/ai/roadmap/{sessionId}` |
| **Update Progress** | Cập nhật tiến độ quest | `POST /api/v1/ai/roadmap/{sessionId}/progress` |

### 6.3 Analytics

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Global Mode Counts** | Thống kê roadmap theo mode (admin) | `GET /api/v1/ai/roadmap/analytics/mode-counts` |
| **My Mode Counts** | Thống kê roadmap của user | `GET /api/v1/ai/roadmap/analytics/mode-counts/me` |
| **Mode Counts in Range** | Thống kê theo khoảng thời gian | `GET /api/v1/ai/roadmap/analytics/mode-counts/range` |
| **Daily/Weekly/Monthly Counts** | Thống kê theo ngày/tuần/tháng | `GET /api/v1/ai/roadmap/analytics/mode-counts/...` |

---

## 7. AI Chat

### 7.1 Chat Session

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Session** | Tạo phiên chat mới | `POST /api/chat/sessions` |
| **Get Sessions** | Danh sách phiên chat | `GET /api/chat/sessions` |
| **Get Session by ID** | Chi tiết phiên chat | `GET /api/chat/sessions/{sessionId}` |
| **Delete Session** | Xóa phiên chat | `DELETE /api/chat/sessions/{sessionId}` |

### 7.2 Chat Message

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Send Message** | Gửi tin nhắn cho AI | `POST /api/chat/messages` |
| **Get Messages** | Lấy tin nhắn trong phiên | `GET /api/chat/sessions/{sessionId}/messages` |
| **Speech to Text** | Chuyển giọng nói thành text | `POST /api/speech/to-text` |

### 7.3 Expert Prompt

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Expert Fields** | Lấy danh sách lĩnh vực expert | `GET /api/expert-fields` |
| **Get Prompt Config** | Lấy cấu hình prompt | `GET /api/expert-fields/{fieldId}/prompt` |

---

## 8. Study Planner

### 8.1 Study Session

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Session** | Tạo phiên học | `POST /api/study-planner/sessions` |
| **Create Sessions Batch** | Tạo nhiều phiên học | `POST /api/study-planner/sessions/batch` |
| **Get Sessions** | Danh sách phiên học | `GET /api/study-planner/sessions` |
| **Get Sessions in Range** | Phiên học trong khoảng thời gian | `GET /api/study-planner/sessions/range` |
| **Update Status** | Cập nhật trạng thái phiên | `PATCH /api/study-planner/sessions/{sessionId}/status` |
| **Delete Session** | Xóa phiên học | `DELETE /api/study-planner/sessions/{sessionId}` |

### 8.2 AI Schedule Generation

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Generate Schedule** | Sinh lịch học AI | `POST /api/study-planner/generate-schedule` |
| **Generate Proposal** | Sinh đề xuất lịch | `POST /api/study-planner/generate-proposal` |
| **Refine Schedule** | Tinh chỉnh lịch | `POST /api/study-planner/refine-schedule` |
| **Check Schedule Health** | Kiểm tra sức khỏe lịch | `POST /api/study-planner/schedule-health` |
| **Suggest Fix** | Đề xuất sửa lỗi | `POST /api/study-planner/schedule-suggest-fix` |

### 8.3 Task Board (Kanban)

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Task Board** | Lấy board công việc | `GET /api/task-board` |
| **Create Task** | Tạo task mới | `POST /api/task-board/tasks` |
| **Update Task** | Cập nhật task | `PUT /api/task-board/tasks/{taskId}` |
| **Move Task** | Di chuyển task (cột/khác) | `PATCH /api/task-board/tasks/{taskId}/move` |
| **Delete Task** | Xóa task | `DELETE /api/task-board/tasks/{taskId}` |
| **Create Column** | Tạo cột mới | `POST /api/task-board/columns` |
| **Update Column** | Cập nhật cột | `PUT /api/task-board/columns/{columnId}` |
| **Delete Column** | Xóa cột | `DELETE /api/task-board/columns/{columnId}` |

---

## 9. Wallet & Payment

### 9.1 Wallet

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get My Wallet** | Lấy thông tin ví | `GET /api/wallet/my-wallet` |
| **Get Transactions** | Lịch sử giao dịch | `GET /api/wallet/transactions` |
| **Get Transaction Detail** | Chi tiết giao dịch | `GET /api/wallet/transactions/{id}` |
| **Get Statistics** | Thống kê ví | `GET /api/wallet/statistics` |
| **Download Invoice** | Tải hóa đơn PDF | `GET /api/wallet/transactions/{id}/invoice` |

### 9.2 Deposit

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Deposit** | Tạo yêu cầu nạp tiền (PayOS) | `POST /api/wallet/deposit` |

### 9.3 Coins

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Coin Packages** | Lấy gói coins | `GET /api/wallet/coins/packages` |
| **Calculate Price** | Tính giá coin tùy chỉnh | `GET /api/wallet/coins/calculate-price` |
| **Purchase with Cash** | Mua coins bằng tiền mặt | `POST /api/wallet/coins/purchase-with-cash` |
| **Purchase with PayOS** | Mua coins qua PayOS | `POST /api/wallet/coins/purchase-with-payos` |

### 9.4 Withdrawal

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Withdrawal** | Tạo yêu cầu rút tiền | `POST /api/wallet/withdraw/request` |
| **Get My Withdrawals** | Lịch sử rút tiền | `GET /api/wallet/withdraw/my-requests` |
| **Get Withdrawal Detail** | Chi tiết yêu cầu | `GET /api/wallet/withdraw/{id}` |
| **Cancel Withdrawal** | Hủy yêu cầu rút | `PUT /api/wallet/withdraw/{id}/cancel` |

### 9.5 Security

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Set PIN** | Đặt mã PIN giao dịch | `PUT /api/wallet/pin` |
| **Update Bank Account** | Cập nhật thông tin ngân hàng | `PUT /api/wallet/bank-account` |
| **Toggle 2FA** | Bật/tắt 2FA | `PUT /api/wallet/2fa` |

### 9.6 Payment Integration

| Function | Description | Endpoint |
|----------|-------------|----------|
| **PayOS Webhook** | Nhận webhook từ PayOS | `POST /api/webhooks/payos` |
| **Create Payment** | Tạo thanh toán | `POST /api/payments/create` |
| **Verify Payment** | Xác thực thanh toán | `GET /api/payments/{id}/verify` |
| **Get Payment Status** | Lấy trạng thái | `GET /api/payments/{id}/status` |

---

## 10. Premium Subscription

### 10.1 Subscription Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Plans** | Lấy danh sách gói premium | `GET /api/premium/plans` |
| **Get Plan by ID** | Chi tiết gói premium | `GET /api/premium/plans/{planId}` |
| **Create Subscription** | Tạo đăng ký premium | `POST /api/premium/subscribe` |
| **Get Current Subscription** | Đăng ký hiện tại | `GET /api/premium/subscription/current` |
| **Get Subscription History** | Lịch sử đăng ký | `GET /api/premium/subscription/history` |
| **Cancel Subscription** | Hủy đăng ký | `PUT /api/premium/subscription/cancel` |
| **Check Premium Status** | Kiểm tra trạng thái premium | `GET /api/premium/status` |
| **Recover Pending** | Kích hoạt đăng ký chờ | `POST /api/premium/subscription/recover` |

### 10.2 Premium Features

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Purchase with Wallet** | Mua premium bằng ví | `POST /api/premium/purchase-with-wallet` |
| **Enable Auto Renewal** | Bật gia hạn tự động | `POST /api/premium/subscription/enable-auto-renewal` |
| **Cancel Auto Renewal** | Hủy gia hạn tự động | `POST /api/premium/subscription/cancel-auto-renewal` |
| **Cancel with Refund** | Hủy có hoàn tiền | `POST /api/premium/subscription/cancel-with-refund` |
| **Check Refund Eligibility** | Kiểm tra điều kiện hoàn tiền | `GET /api/premium/subscription/refund-eligibility` |

### 10.3 Usage Limits

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Usage** | Lấy mức sử dụng hiện tại | `GET /api/usage-limits` |
| **Get Limit Details** | Chi tiết giới hạn | `GET /api/usage-limits/{feature}` |

---

## 11. Portfolio & CV

### 11.1 Extended Profile

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Profile** | Tạo profile mở rộng | `POST /api/portfolio/profile` |
| **Update Profile** | Cập nhật profile | `PUT /api/portfolio/profile` |
| **Delete Profile** | Xóa profile | `DELETE /api/portfolio/profile` |
| **Get My Profile** | Lấy profile của tôi | `GET /api/portfolio/profile` |
| **Get Public Profile** | Lấy profile công khai | `GET /api/portfolio/profile/{userId}` |
| **Get Profile by Slug** | Lấy theo URL tùy chỉnh | `GET /api/portfolio/profile/slug/{slug}` |
| **Check Extended Profile** | Kiểm tra có profile chưa | `GET /api/portfolio/profile/check` |
| **Get All Public** | Tất cả profile công khai | `GET /api/portfolio/public` |

### 11.2 Projects

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Project** | Tạo dự án | `POST /api/portfolio/projects` |
| **Update Project** | Cập nhật dự án | `PUT /api/portfolio/projects/{projectId}` |
| **Get My Projects** | Danh sách dự án của tôi | `GET /api/portfolio/projects` |
| **Get Public Projects** | Dự án công khai | `GET /api/portfolio/public/{userId}/projects` |
| **Delete Project** | Xóa dự án | `DELETE /api/portfolio/projects/{projectId}` |

### 11.3 Certificates

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Add Certificate** | Thêm chứng chỉ bên ngoài | `POST /api/portfolio/certificates` |
| **Get My Certificates** | Danh sách chứng chỉ | `GET /api/portfolio/certificates` |
| **Get Public Certificates** | Chứng chỉ công khai | `GET /api/portfolio/public/{userId}/certificates` |
| **Delete Certificate** | Xóa chứng chỉ | `DELETE /api/portfolio/certificates/{certificateId}` |

### 11.4 CV Generation

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Generate CV** | Sinh CV bằng AI | `POST /api/portfolio/cv/generate` |
| **Update CV** | Cập nhật CV | `PUT /api/portfolio/cv/{cvId}` |
| **Get Active CV** | Lấy CV đang hiển thị | `GET /api/portfolio/cv/active` |
| **Get All CVs** | Tất cả các phiên bản CV | `GET /api/portfolio/cv/all` |
| **Set Active CV** | Đặt làm CV mặc định | `PUT /api/portfolio/cv/{cvId}/set-active` |
| **Delete CV** | Xóa CV | `DELETE /api/portfolio/cv/{cvId}` |

### 11.5 Reviews

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get My Reviews** | Lấy đánh giá về tôi | `GET /api/portfolio/reviews` |
| **Get Public Reviews** | Đánh giá công khai | `GET /api/portfolio/public/{userId}/reviews` |
| **Verify Review** | Admin duyệt đánh giá | `PUT /api/portfolio/reviews/{id}/verify` |

---

## 12. Job & Recruitment

### 12.1 Job Posting

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Job** | Tạo tin tuyển dụng | `POST /api/jobs` |
| **Update Job** | Cập nhật tin | `PUT /api/jobs/{id}` |
| **Change Status** | Thay đổi trạng thái | `PATCH /api/jobs/{id}/status` |
| **Get My Jobs** | Tin tuyển dụng của tôi | `GET /api/jobs/my-jobs` |
| **Get Public Jobs** | Tin tuyển dụng công khai | `GET /api/jobs/public` |
| **Get Job Details** | Chi tiết tin | `GET /api/jobs/{id}` |
| **Delete Job** | Xóa tin | `DELETE /api/jobs/{id}` |
| **Reopen Job** | Mở lại tin đã đóng | `POST /api/jobs/{id}/reopen` |

### 12.2 Job Application

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Apply Job** | Ứng tuyển | `POST /api/applications` |
| **Get My Applications** | Đơn ứng tuyển của tôi | `GET /api/applications/my` |
| **Get Applications (Recruiter)** | Quản lý đơn ứng tuyển | `GET /api/applications/job/{jobId}` |
| **Update Application Status** | Cập nhật trạng thái | `PATCH /api/applications/{id}/status` |
| **Withdraw Application** | Rút đơn ứng tuyển | `DELETE /api/applications/{id}` |

### 12.3 Short-term Jobs (Gig)

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Short-term Job** | Tạo việc ngắn hạn | `POST /api/short-term-jobs` |
| **Get Short-term Jobs** | Danh sách việc ngắn hạn | `GET /api/short-term-jobs` |
| **Apply Short-term Job** | Ứng tuyển việc ngắn hạn | `POST /api/short-term-jobs/{id}/apply` |

### 12.4 Recruiter Profile

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Profile** | Lấy profile recruiter | `GET /api/recruiter/profile` |
| **Update Profile** | Cập nhật profile | `PUT /api/recruiter/profile` |
| **Register Business** | Đăng ký doanh nghiệp | `POST /api/business/register` |
| **Get Business Status** | Trạng thái đăng ký | `GET /api/business/status` |

### 12.5 Job Reviews

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Review Application** | Đánh giá ứng viên | `POST /api/job-reviews` |

---

## 13. Community & Blog

### 13.1 Posts

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Post** | Tạo bài viết | `POST /api/posts` |
| **Update Post** | Cập nhật bài viết | `PUT /api/posts/{id}` |
| **Delete Post** | Xóa bài viết | `DELETE /api/posts/{id}` |
| **Get Post** | Lấy chi tiết bài viết | `GET /api/posts/{id}` |
| **List Posts** | Danh sách bài viết (phân trang, lọc) | `GET /api/posts` |
| **Get Saved Posts** | Bài viết đã lưu | `GET /api/posts/saved` |
| **Get Stats** | Thống kê bài viết | `GET /api/posts/stats` |
| **Get Trends** | Xu hướng bài viết | `GET /api/posts/trends` |

### 13.2 Interactions

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Like Post** | Thích bài viết | `POST /api/posts/{id}/like` |
| **Dislike Post** | Không thích | `POST /api/posts/{id}/dislike` |
| **Save Post** | Lưu bài viết | `POST /api/posts/{id}/save` |

### 13.3 Comments

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Add Comment** | Thêm bình luận | `POST /api/posts/{id}/comments` |
| **Get Comments** | Lấy bình luận | `GET /api/posts/{id}/comments` |
| **Delete Comment** | Xóa bình luận | `DELETE /api/posts/{postId}/comments/{commentId}` |
| **Hide Comment** | Ẩn bình luận | `POST /api/posts/{postId}/comments/{commentId}/hide` |
| **Unhide Comment** | Hiện bình luận | `POST /api/posts/{postId}/comments/{commentId}/unhide` |
| **Report Comment** | Báo cáo bình luận | `POST /api/posts/{postId}/comments/{commentId}/report` |

---

## 14. Gamification

### 14.1 Dashboard

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Dashboard** | Lấy thông tin gamification | `GET /api/gamification/dashboard` |

### 14.2 Badges

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get User Badges** | Danh huy hiệu của user | `GET /api/gamification/badges` |
| **Get Badge Definitions** | Tất cả huy hiệu có thể nhận | `GET /api/gamification/badges/definitions` |

### 14.3 Mini Games

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Available Games** | Danh sách game có thể chơi | `GET /api/gamification/games` |
| **Get Game Definition** | Chi tiết game | `GET /api/gamification/games/{gameKey}` |
| **Start Game** | Bắt đầu phiên game | `POST /api/gamification/games/start` |
| **Complete Game** | Hoàn thành phiên game | `POST /api/gamification/games/complete` |
| **Get Game History** | Lịch sử chơi game | `GET /api/gamification/games/history` |

### 14.4 Leaderboard

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Leaderboard** | Bảng xếp hạng | `GET /api/gamification/leaderboard` |
| **Get User Position** | Vị trí của user | `GET /api/gamification/leaderboard/position` |

### 14.5 Daily Check-in

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Check In** | Điểm danh hàng ngày | `POST /api/daily-checkin` |
| **Get Check-in Status** | Trạng thái điểm danh | `GET /api/daily-checkin/status` |
| **Get Check-in History** | Lịch sử điểm danh | `GET /api/daily-checkin/history` |

### 14.6 Activity Logging

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Log Activity** | Ghi nhận hoạt động | `POST /api/gamification/activities` |
| **Get Activities** | Lịch sử hoạt động | `GET /api/gamification/activities` |

---

## 15. Notification

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Notifications** | Lấy danh sách thông báo | `GET /api/notifications` |
| **Mark as Read** | Đánh dấu đã đọc | `PUT /api/notifications/{id}/read` |
| **Mark All as Read** | Đánh dấu tất cả đã đọc | `PUT /api/notifications/read-all` |
| **Delete Notification** | Xóa thông báo | `DELETE /api/notifications/{id}` |
| **Get Unread Count** | Số thông báo chưa đọc | `GET /api/notifications/unread-count` |

---

## 16. Support & Ticket

### 16.1 Support Tickets

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Ticket** | Tạo ticket hỗ trợ | `POST /api/support/tickets` |
| **Get My Tickets** | Danh sách ticket của tôi | `GET /api/support/tickets` |
| **Get Ticket Details** | Chi tiết ticket | `GET /api/support/tickets/{id}` |
| **Add Message** | Thêm tin nhắn vào ticket | `POST /api/support/tickets/{id}/messages` |
| **Close Ticket** | Đóng ticket | `PUT /api/support/tickets/{id}/close` |

### 16.2 Pre-chat (Mentor)

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Create Pre-chat Block** | Tạo block câu hỏi | `POST /api/prechat/blocks` |
| **Get Pre-chat Blocks** | Lấy danh sách block | `GET /api/prechat/blocks/{mentorId}` |
| **Submit Pre-chat** | Gửi câu trả lời pre-chat | `POST /api/prechat/submit` |
| **Get Pre-chat Reports** | Báo cáo pre-chat | `GET /api/prechat/reports` |

---

## 17. Admin Management

### 17.1 User Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get All Users** | Danh sách tất cả user | `GET /api/admin/users` |
| **Get User Details** | Chi tiết user | `GET /api/admin/users/{userId}` |
| **Update User** | Cập nhật user | `PUT /api/admin/users/{userId}` |
| **Delete User** | Xóa user | `DELETE /api/admin/users/{userId}` |
| **Change User Role** | Thay đổi role | `PUT /api/admin/users/{userId}/role` |

### 17.2 Course Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get All Courses** | Tất cả khóa học | `GET /api/admin/courses` |
| **Approve Course** | Duyệt khóa học | `PUT /api/admin/courses/{courseId}/approve` |
| **Reject Course** | Từ chối khóa học | `PUT /api/admin/courses/{courseId}/reject` |
| **Feature Course** | Đặt làm nổi bật | `PUT /api/admin/courses/{courseId}/feature` |

### 17.3 Mentor Application

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Applications** | Danh sách đăng ký mentor | `GET /api/admin/applications` |
| **Get Application Details** | Chi tiết đăng ký | `GET /apiid}` |
|/admin/applications/{ **Approve Application** | Duyệt đăng ký | `PUT /api/admin/applications/{id}/approve` |
| **Reject Application** | Từ chối đăng ký | `PUT /api/admin/applications/{id}/reject` |
| **Get Application Stats** | Thống kê đăng ký | `GET /api/admin/applications/stats` |

### 17.4 Job Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get All Jobs** | Tất cả tin tuyển dụng | `GET /api/admin/jobs` |
| **Approve Job** | Duyệt tin | `PUT /api/admin/jobs/{id}/approve` |
| **Reject Job** | Từ chối tin | `PUT /api/admin/jobs/{id}/reject` |

### 17.5 Premium Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get All Subscriptions** | Tất cả đăng ký premium | `GET /api/admin/subscriptions` |
| **Get Subscription Details** | Chi tiết đăng ký | `GET /api/admin/subscriptions/{id}` |
| **Cancel Subscription** | Hủy đăng ký (admin) | `PUT /api/admin/subscriptions/{id}/cancel` |

### 17.6 Wallet Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get All Wallets** | Tất cả ví | `GET /api/admin/wallets` |
| **Get Wallet Details** | Chi tiết ví | `GET /api/admin/wallets/{userId}` |
| **Get All Transactions** | Tất cả giao dịch | `GET /api/admin/transactions` |
| **Get Withdrawal Requests** | Yêu cầu rút tiền | `GET /api/admin/withdrawals` |
| **Approve Withdrawal** | Duyệt rút tiền | `PUT /api/admin/withdrawals/{id}/approve` |
| **Reject Withdrawal** | Từ chối rút tiền | `PUT /api/admin/withdrawals/{id}/reject` |

### 17.7 Gamification Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get All Badges** | Tất cả huy hiệu | `GET /api/admin/gamification/badges` |
| **Create Badge** | Tạo huy hiệu mới | `POST /api/admin/gamification/badges` |
| **Update Badge** | Cập nhật huy hiệu | `PUT /api/admin/gamification/badges/{id}` |
| **Get All Games** | Tất cả game | `GET /api/admin/gamification/games` |
| **Create Game** | Tạo game mới | `POST /api/admin/gamification/games` |

### 17.8 Reports

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Reports** | Danh sách báo cáo vi phạm | `GET /api/admin/reports` |
| **Handle Report** | Xử lý báo cáo | `PUT /api/admin/reports/{id}/handle` |

### 17.9 Email Notifications

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Send Email** | Gửi email thủ công | `POST /api/admin/emails/send` |
| **Send Bulk Emails** | Gửi email hàng loạt | `POST /api/admin/emails/send-bulk` |

### 17.10 Roadmap Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get All Roadmaps** | Tất cả roadmap | `GET /api/admin/roadmaps` |
| **Delete Roadmap** | Xóa roadmap | `DELETE /api/admin/roadmaps/{id}` |

### 17.11 Content Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get Sliders** | Lấy danh sách slider | `GET /api/admin/sliders` |
| **Create Slider** | Tạo slider mới | `POST /api/admin/sliders` |
| **Update Slider** | Cập nhật slider | `PUT /api/admin/sliders/{id}` |
| **Delete Slider** | Xóa slider | `DELETE /api/admin/sliders/{id}` |

---

## 18. Shared Services

### 18.1 Media Management

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Upload Media** | Tải lên file media | `POST /api/media/upload` |
| **Get Media** | Lấy thông tin media | `GET /api/media/{id}` |
| **Delete Media** | Xóa media | `DELETE /api/media/{id}` |

### 18.2 Skills

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Get All Skills** | Lấy danh sách kỹ năng | `GET /api/skills` |
| **Create Skill** | Tạo kỹ năng mới | `POST /api/skills` |
| **Update Skill** | Cập nhật kỹ năng | `PUT /api/skills/{id}` |
| **Delete Skill** | Xóa kỹ năng | `DELETE /api/skills/{id}` |

### 18.3 Health Check

| Function | Description | Endpoint |
|----------|-------------|----------|
| **Health Check** | Kiểm tra trạng thái hệ thống | `GET /api/health` |

---

## Tổng Kết

| Service | Số lượng Endpoints | Mô Tả |
|---------|-------------------|-------|
| Auth | 12 | Authentication & Authorization |
| User | 6 | User management |
| Course | 25+ | Course, Module, Lesson, Quiz, Assignment |
| Mentor | 15+ | Mentor profile & registration |
| Booking | 12 | Mentor booking & scheduling |
| AI Roadmap | 20+ | AI-powered learning roadmap |
| AI Chat | 6 | AI chatbot |
| Study Planner | 15+ | Study sessions & task board |
| Wallet | 18 | Wallet, deposit, withdrawal, coins |
| Premium | 15+ | Subscription management |
| Portfolio | 25+ | Portfolio, CV generation |
| Job | 15+ | Job posting & applications |
| Community | 18+ | Posts, comments, likes |
| Gamification | 12 | Badges, games, leaderboard |
| Notification | 5 | Push notifications |
| Support | 6 | Support tickets |
| Admin | 40+ | Full admin management |
| Shared | 10+ | Media, skills, health |

**Tổng cộng: ~300+ API Endpoints**

---

> Document generated from SkillVerse Backend source code analysis
> Last updated: 2026-03-10
