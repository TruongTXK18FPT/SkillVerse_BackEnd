package com.exe.skillverse_backend.auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "invalidated_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvalidatedToken {

    @Id
    @Column(length = 36, nullable = false)
    private String jti; // JWT ID - unique identifier for the token

    @Column(name = "invalidated_at", nullable = false)
    private LocalDateTime invalidatedAt = LocalDateTime.now();

    public InvalidatedToken(String jti) {
        this.jti = jti;
        this.invalidatedAt = LocalDateTime.now();
    }
}