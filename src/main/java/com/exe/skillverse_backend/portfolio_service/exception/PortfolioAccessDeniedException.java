package com.exe.skillverse_backend.portfolio_service.exception;

public class PortfolioAccessDeniedException extends RuntimeException {
    public PortfolioAccessDeniedException(String message) {
        super(message);
    }

    public PortfolioAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}