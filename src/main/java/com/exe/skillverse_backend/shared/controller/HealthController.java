package com.exe.skillverse_backend.shared.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Tag(name = "Health Check", description = "System health monitoring endpoints")
@RestController
@RequestMapping("/api")
public class HealthController {

    private static final String STATUS_KEY = "status";
    private static final String TIMESTAMP_KEY = "timestamp";
    private static final String VERSION_KEY = "version";
    private static final String DATABASE_KEY = "database";
    private static final String ERROR_KEY = "error";
    
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Operation(
        summary = "Basic health check",
        description = "Returns basic application health status"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Application is healthy",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = """
                    {
                        "status": "UP",
                        "timestamp": "2025-09-26T10:30:00",
                        "version": "1.0.0",
                        "environment": "production"
                    }
                    """)
            )
        )
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> simpleHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("version", "1.0.0");
        health.put("service", "skillverse-backend");
        
        return ResponseEntity.ok(health);
    }

    @Operation(
        summary = "Detailed health check",
        description = "Returns detailed application health including database connectivity"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Detailed health information",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(example = """
                    {
                        "status": "UP",
                        "timestamp": "2025-09-26T10:30:00",
                        "components": {
                            "database": {"status": "UP"},
                            "diskSpace": {"status": "UP", "free": "10GB"},
                            "memory": {"status": "UP", "used": "512MB", "max": "2GB"}
                        }
                    }
                    """)
            )
        )
    })
    @GetMapping("/health/detailed")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        Map<String, Object> components = new HashMap<>();
        
        // Check database
        components.put("database", checkDatabaseHealth());
        
        // Check memory
        components.put("memory", checkMemoryHealth());
        
        // Check disk space
        components.put("diskSpace", checkDiskSpace());
        
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        health.put("service", "skillverse-backend");
        health.put("version", "1.0.0");
        health.put("components", components);
        
        return ResponseEntity.ok(health);
    }

    @Operation(
        summary = "API readiness check",
        description = "Check if API is ready to serve requests"
    )
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> readiness = new HashMap<>();
        boolean isReady = true;
        
        // Check critical components
        Map<String, Object> checks = new HashMap<>();
        
        // Database check
        Map<String, Object> dbCheck = checkDatabaseHealth();
        checks.put("database", dbCheck);
        if (!"UP".equals(dbCheck.get("status"))) {
            isReady = false;
        }
        
        readiness.put("status", isReady ? "READY" : "NOT_READY");
        readiness.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        readiness.put("checks", checks);
        
        return ResponseEntity.status(isReady ? 200 : 503).body(readiness);
    }

    @Operation(
        summary = "API liveness check",
        description = "Check if API is alive and responsive"
    )
    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> liveness = new HashMap<>();
        liveness.put("status", "ALIVE");
        liveness.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        liveness.put("uptime", System.currentTimeMillis());
        
        return ResponseEntity.ok(liveness);
    }



    private Map<String, Object> checkDatabaseHealth() {
        Map<String, Object> dbHealth = new HashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(1)) {
                dbHealth.put("status", "UP");
                dbHealth.put("database", connection.getMetaData().getDatabaseProductName());
                dbHealth.put("version", connection.getMetaData().getDatabaseProductVersion());
            } else {
                dbHealth.put("status", "DOWN");
                dbHealth.put("error", "Connection invalid");
            }
        } catch (Exception e) {
            dbHealth.put("status", "DOWN");
            dbHealth.put("error", e.getMessage());
        }
        return dbHealth;
    }

    private Map<String, Object> checkMemoryHealth() {
        Map<String, Object> memHealth = new HashMap<>();
        Runtime runtime = Runtime.getRuntime();
        
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
        
        memHealth.put("status", memoryUsagePercent < 90 ? "UP" : "WARNING");
        memHealth.put("used", formatBytes(usedMemory));
        memHealth.put("max", formatBytes(maxMemory));
        memHealth.put("usage", String.format("%.2f%%", memoryUsagePercent));
        
        return memHealth;
    }

    private Map<String, Object> checkDiskSpace() {
        Map<String, Object> diskHealth = new HashMap<>();
        try {
            java.io.File file = new java.io.File(".");
            long freeSpace = file.getFreeSpace();
            long totalSpace = file.getTotalSpace();
            long usedSpace = totalSpace - freeSpace;
            
            double diskUsagePercent = (double) usedSpace / totalSpace * 100;
            
            diskHealth.put("status", diskUsagePercent < 90 ? "UP" : "WARNING");
            diskHealth.put("free", formatBytes(freeSpace));
            diskHealth.put("total", formatBytes(totalSpace));
            diskHealth.put("usage", String.format("%.2f%%", diskUsagePercent));
        } catch (Exception e) {
            diskHealth.put("status", "UNKNOWN");
            diskHealth.put("error", e.getMessage());
        }
        return diskHealth;
    }

    private String formatBytes(long bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.2f%s", size, units[unitIndex]);
    }
}