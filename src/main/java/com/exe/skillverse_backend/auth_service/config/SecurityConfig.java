package com.exe.skillverse_backend.auth_service.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

        private static final String[] PUBLIC_ENDPOINTS = {
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/google",
                        "/api/auth/refresh",
                        "/api/auth/verify",
                        // logout should NOT be public; require authentication
                        "/api/auth/forgot-password/**",
                        "/api/auth/reset-password/**",
                        "/api/auth/verify-email/**",
                        "/api/auth/resend-verification/**",
                        "/api/auth/resend-otp",
                        "/api/auth/complete-profile",
                        // User service registration
                        "/api/users/register",
                        "/api/users/verify-email",
                        "/api/users/resend-otp",
                        // Mentor service registration
                        "/api/mentors/register",
                        // Business service registration
                        "/api/business/register",
                        "/api/courses",
                        "/api/courses/**",
                        "/api/courses/by-author/**",
                        "/api/v1/payments/callback/payos",
                        "/api/payment/payos/webhook", // PayOS webhook (alternative endpoint)
                        "/api/payments/test/**", // Payment test endpoints (DEV ONLY)
                        // Meowl Chat Service (AI assistant - public access)
                        "/api/v1/meowl/chat",
                        "/api/v1/meowl/reminders/**",
                        "/api/v1/meowl/notifications/**",
                        "/api/v1/meowl/health",
                        // Public jobs listing
                        "/api/jobs/public",
                        "/api/jobs/public/**",
                        // Public premium plans listing
                        "/api/premium/plans"
        };

        private static final String[] SWAGGER_ENDPOINTS = {
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/swagger-resources/**",
                        "/webjars/**"
        };

        private static final String[] ACTUATOR_ENDPOINTS = {
                        "/actuator/**"
        };

        private final org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;
        private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
        private final CorsConfigurationSource corsConfigurationSource;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
                httpSecurity.authorizeHttpRequests(request -> request
                                // Public authentication endpoints
                                .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
                                .requestMatchers(HttpMethod.GET, PUBLIC_ENDPOINTS).permitAll()

                                // Allow all preflight CORS requests
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                                // Swagger/OpenAPI documentation endpoints
                                .requestMatchers(SWAGGER_ENDPOINTS).permitAll()

                                // Actuator endpoints (consider restricting in production)
                                .requestMatchers(ACTUATOR_ENDPOINTS).permitAll()

                                // Health check endpoint
                                .requestMatchers(HttpMethod.GET, "/health").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()

                                // Error handling endpoint
                                .requestMatchers("/error").permitAll()

                                // Public GET endpoints for specific user operations
                                .requestMatchers(HttpMethod.GET, "/api/user/profile/public/**").permitAll()

                                // All other requests require authentication
                                .anyRequest().authenticated());

                httpSecurity.oauth2ResourceServer(oauth2 -> oauth2
                                .jwt(jwtConfigurer -> jwtConfigurer
                                                .decoder(jwtDecoder)
                                                .jwtAuthenticationConverter(jwtAuthenticationConverter()))
                                .authenticationEntryPoint(jwtAuthenticationEntryPoint));

                httpSecurity.csrf(AbstractHttpConfigurer::disable);
                httpSecurity.cors(cors -> cors.configurationSource(corsConfigurationSource));
                httpSecurity.sessionManagement(
                                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

                return httpSecurity.build();
        }

        @Bean
        JwtAuthenticationConverter jwtAuthenticationConverter() {
                JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
                jwtGrantedAuthoritiesConverter.setAuthorityPrefix("ROLE_"); // Add ROLE_ prefix for
                                                                            // @PreAuthorize("hasRole(...)")
                jwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("roles");

                JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
                jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);

                // Set principal claim name to use the User ID for lookup
                jwtAuthenticationConverter.setPrincipalClaimName("userId");

                return jwtAuthenticationConverter;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder(10);
        }
}