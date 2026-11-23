package com.restaurant.platform.infrastructure.config;

import com.restaurant.platform.infrastructure.security.TenantIsolationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcSecurityConfig implements WebMvcConfigurer {

    private final TenantIsolationInterceptor tenantIsolationInterceptor;

    public WebMvcSecurityConfig(TenantIsolationInterceptor tenantIsolationInterceptor) {
        this.tenantIsolationInterceptor = tenantIsolationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantIsolationInterceptor).addPathPatterns("/api/**");
    }
}