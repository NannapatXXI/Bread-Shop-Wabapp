package com.breadShop.XXI.config;

import java.nio.file.Path;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ApiLoggingInterceptor apiLoggingInterceptor;
    private final RateLimitInterceptor  rateLimitInterceptor;

    public WebConfig(ApiLoggingInterceptor apiLoggingInterceptor,
                     RateLimitInterceptor rateLimitInterceptor) {
        this.apiLoggingInterceptor = apiLoggingInterceptor;
        this.rateLimitInterceptor  = rateLimitInterceptor;
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        Path uploadsPath = UploadPaths.dir();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadsPath + "/");
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/auth/login", "/api/v1/auth/send-OTP-mail");

        registry.addInterceptor(apiLoggingInterceptor)
                // ไม่ log /logs/** เพื่อกัน recursive และ /auth/refresh ที่เรียกบ่อยมาก
                .excludePathPatterns(
                    "/api/v1/admin/logs/**",
                    "/api/v1/auth/refresh",
                    "/uploads/**"
                );
    }
}
