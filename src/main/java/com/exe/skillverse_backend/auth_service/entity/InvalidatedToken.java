package com.exe.skillverse_backend.auth_service.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
