package com.exe.skillverse_backend.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${cors.allowed.origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,https://skillverse.vn,https://www.skillverse.vn}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker to carry messages back to client
        // Messages with destinations starting with /topic will be routed to the broker
        config.enableSimpleBroker("/topic", "/queue");
        // Messages with destinations starting with /app will be routed to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");
        // User-specific destinations prefix
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        
        // Register the /ws endpoint for WebSocket connections with SockJS
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins)
                .withSockJS();
        
        // Also add without SockJS for native WebSocket clients
        registry.addEndpoint("/ws")
                .setAllowedOrigins(origins);
    }
}
