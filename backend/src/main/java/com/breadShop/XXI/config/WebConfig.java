package com.breadShop.XXI.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ApiLoggingInterceptor apiLoggingInterceptor;

    public WebConfig(ApiLoggingInterceptor apiLoggingInterceptor) {
        this.apiLoggingInterceptor = apiLoggingInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path base = Paths.get(System.getProperty("user.dir"));
        Path candidate = base.resolve("uploads");
        Path uploadsPath = candidate.toFile().exists() ? candidate : base.getParent().resolve("uploads");
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadsPath.toAbsolutePath() + "/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiLoggingInterceptor)
                // ไม่ log /logs/** เพื่อกัน recursive และ /auth/refresh ที่เรียกบ่อยมาก
                .excludePathPatterns(
                    "/api/v1/admin/logs/**",
                    "/api/v1/auth/refresh",
                    "/uploads/**"
                );
    }
}
