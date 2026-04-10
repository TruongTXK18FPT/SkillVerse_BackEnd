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
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.security.oauth2.jwt.JwtDecoder;

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
                        "/api/auth/forgot-password",
                        "/api/auth/reset-password",
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
                        "/api/certificates/verify/**",
                        "/api/v1/payments/callback/payos",
                        // Meowl Chat Service (AI assistant - public access)
                        "/api/v1/meowl/chat",
                        "/api/v1/meowl/reminders/**",
                        "/api/v1/meowl/notifications/**",
                        "/api/v1/meowl/health",
                        // Public jobs listing
                        "/api/jobs/public",
                        "/api/jobs/public/**",
                        // Support tickets (public create/track)
                        "/api/v1/support/tickets",
                        "/api/v1/support/tickets/code/**",
                        "/api/v1/support/tickets/email/**",
                        "/api/v1/support/chat/**",
                        // Sliders
                        "/api/public/sliders",
                        // WebSocket
                        "/ws/**"
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

        private final JwtDecoder jwtDecoder;
        private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
        private final CorsConfigurationSource corsConfigurationSource;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
                httpSecurity.authorizeHttpRequests(request -> request
                                // Temporary public access for seeding leaderboard
                                .requestMatchers(HttpMethod.POST, "/api/admin/gamification/seed/leaderboard").permitAll()
                                // Public access for gamification leaderboard and badge definitions (read-only)
                                .requestMatchers(HttpMethod.GET, "/api/gamification/leaderboard").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/gamification/badges/definitions").permitAll()

                                // Admin endpoints - MUST require authentication (check BEFORE public endpoints)
                                .requestMatchers("/api/admin/**").authenticated()
                                .requestMatchers("/api/courses/pending").authenticated()
                                .requestMatchers("/api/courses/*/approve").authenticated()
                                .requestMatchers("/api/courses/*/reject").authenticated()

                                // Public authentication endpoints
                                .requestMatchers(HttpMethod.POST, PUBLIC_ENDPOINTS).permitAll()
                                .requestMatchers(HttpMethod.GET, PUBLIC_ENDPOINTS).permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/premium/plans", "/api/premium/plans/**")
                                .permitAll()
                                .requestMatchers("/ws/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/v1/expert-fields", "/api/v1/expert-fields/**")
                                .permitAll()

                                // Courses: public read only, write endpoints must be authenticated
                                .requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/*",
                                                "/api/courses/by-author/**")
                                .permitAll()

                                // Community posts: allow public GET only
                                .requestMatchers(HttpMethod.GET, "/api/posts", "/api/posts/**").permitAll()

                                // Seminars: allow public GET (isOwned check works with optional auth via JWT
                                // parsing)
                                .requestMatchers(HttpMethod.GET, "/api/seminars", "/api/seminars/*").permitAll()

                                // Meowl Shop: allow public GET for skins (viewing shop), require auth for purchase/select
                                .requestMatchers(HttpMethod.GET, "/api/skins", "/api/skins/**").permitAll()
                                
                                // Mentors: allow public GET for mentor list and profiles
                                .requestMatchers(HttpMethod.GET, "/api/mentors", "/api/mentors/**").permitAll()

                                // Portfolio: allow public portfolio viewing endpoints
                                .requestMatchers(HttpMethod.GET, "/api/portfolio/public", "/api/portfolio/public/**",
                                                "/api/portfolio/profile/slug/**")
                                .permitAll()
                                .requestMatchers(new RegexRequestMatcher("^/api/portfolio/profile/\\d+$", "GET"))
                                .permitAll()

                                // Short-term jobs: allow public GET for browsing and detail
                                .requestMatchers(HttpMethod.GET, "/api/short-term-jobs/public", "/api/short-term-jobs/public/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/short-term-jobs/search").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/short-term-jobs/{id}").permitAll()

                                // Job reviews: allow public GET for user rating summaries
                                .requestMatchers(HttpMethod.GET, "/api/job-reviews/user/*/summary").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/job-reviews/public/**").permitAll()

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
                                .requestMatchers(HttpMethod.GET, "/api/business/*/profile").permitAll()

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
