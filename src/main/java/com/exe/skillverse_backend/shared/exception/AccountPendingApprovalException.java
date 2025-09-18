package com.exe.skillverse_backend.shared.exception;

/**
 * Custom exception for account approval-related errors
 */
public class AccountPendingApprovalException extends AuthenticationException {

    public AccountPendingApprovalException(String message) {
        super(message, "ACCOUNT_PENDING_APPROVAL", 403);
    }

    public static AccountPendingApprovalException forMentor() {
        return new AccountPendingApprovalException(
                "Your mentor application is pending admin approval. Please wait for admin to review your application before logging in.");
    }

    public static AccountPendingApprovalException forRecruiter() {
        return new AccountPendingApprovalException(
                "Your recruiter application is pending admin approval. Please wait for admin to review your application before logging in.");
    }
}