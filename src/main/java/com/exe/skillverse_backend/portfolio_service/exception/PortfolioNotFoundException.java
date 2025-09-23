package com.exe.skillverse_backend.portfolio_service.exception;

public class PortfolioNotFoundException extends RuntimeException {
    public PortfolioNotFoundException(String message) {
        super(message);
    }

    public PortfolioNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}