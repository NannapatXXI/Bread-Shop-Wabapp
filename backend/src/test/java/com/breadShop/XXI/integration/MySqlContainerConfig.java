package com.breadShop.XXI.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;

/**
 * MySQL สำหรับ integration test — container ใหม่ทุกครั้งที่รัน ไม่ใช่ DB ที่ใช้พัฒนา
 * Flyway รัน migration ตั้งแต่ V1 บน DB เปล่า (เหมือน deploy ครั้งแรก)
 * @ServiceConnection ตั้ง spring.datasource.* ให้ชี้ container นี้เอง
 *
 * static → ทุก test class ใช้ container เดียวกัน (start ครั้งเดียว)
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlContainerConfig {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("breadProject");

    @Bean
    @ServiceConnection
    MySQLContainer<?> mysql() {
        return MYSQL;
    }
}
