package com.restaurant.platform.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Value("${app.cors.allowed-origins:http://localhost,http://localhost:*,http://127.0.0.1,http://127.0.0.1:*}")
    private List<String> allowedOrigins;

    @Value("${app.rate-limit.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Value("${app.rate-limit.trusted-proxies:127.0.0.1,::1,0:0:0:0:0:0:0:1}")
    private List<String> trustedProxies;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, AuthenticationProvider authenticationProvider) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        var rateLimitFilter = new RateLimitingAndBruteForceFilter(trustProxyHeaders, trustedProxies);
        var idempotencyFilter = new IdempotencyFilter();

        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/login", "/api/auth/register-workspace", "/error").permitAll()
                .requestMatchers("/api/auth/change-password").authenticated()
                .requestMatchers("/api/public/**", "/ws/**").permitAll()
                .requestMatchers("/api/super-admin/**").hasRole("SUPER_ADMIN")
                .requestMatchers("/api/org/**").hasRole("ORG_ADMIN")
                .requestMatchers("/api/pos/**").hasAnyRole("CASHIER", "ORG_ADMIN")
                .requestMatchers("/api/kds/**").hasAnyRole("KITCHEN_STAFF", "ORG_ADMIN")
                .requestMatchers("/api/waiter/**").hasAnyRole("WAITER", "CASHIER", "ORG_ADMIN")
                .requestMatchers("/api/analytics/**").hasAnyRole("ORG_ADMIN", "CASHIER")
                .requestMatchers("/api/branches/**").hasAnyRole("ORG_ADMIN", "CASHIER", "KITCHEN_STAFF", "WAITER")
                .anyRequest().authenticated())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(idempotencyFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> sanitized = allowedOrigins.stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        cfg.setAllowedOriginPatterns(sanitized);
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "Idempotency-Key"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}