package com.breadShop.XXI.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.breadShop.XXI.service.Mailservice;

/**
 * Actuator สำหรับ Prometheus/Grafana — เปิดแอปจริงบน port จริง (ไม่ใช่ MockMvc)
 * ต้องอยู่บน management port เท่านั้น ห้ามโผล่บน port ที่ลูกค้าเรียก
 */
// @AutoConfigureObservability: @SpringBootTest ปิด metrics export ไว้โดยปริยาย — เปิดให้เหมือนตอนรันจริง
@AutoConfigureObservability
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@Import(MySqlContainerConfig.class)
@EnabledIfSystemProperty(named = "integration", matches = "true")
@DisplayName("Monitoring endpoints (Actuator)")
class MonitoringEndpointsIT {

    @LocalServerPort private int appPort;
    @LocalManagementPort private int managementPort;
    @MockitoBean private Mailservice mailservice;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void health_isUp_withoutLeakingDetails() throws Exception {
        HttpResponse<String> res = get(managementPort, "/actuator/health");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).isEqualTo("{\"status\":\"UP\"}"); // ไม่บอกรายละเอียด DB/ดิสก์ให้คนนอก
    }

    @Test
    void prometheus_exposesRequestMetrics_forEachEndpoint() throws Exception {
        get(appPort, "/api/v1/products"); // ให้มี request เกิดขึ้นก่อน

        HttpResponse<String> res = get(managementPort, "/actuator/prometheus");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body())
                .contains("http_server_requests_seconds_count{")
                .contains("uri=\"/api/v1/products\"")
                .contains("http_server_requests_seconds_bucket{")       // histogram → p95 ได้
                .contains("application=\"breadshop-backend\"");
    }

    @Test
    void actuator_isNotReachableOnTheCustomerFacingPort() throws Exception {
        // health/prometheus เป็น permitAll ผ่าน security ไปได้ → port นี้ต้องไม่มี endpoint = 404
        for (String path : new String[] {"/actuator/health", "/actuator/prometheus"}) {
            HttpResponse<String> res = get(appPort, path);

            assertThat(res.statusCode()).as(path).isEqualTo(404);
            assertThat(res.body()).as(path).doesNotContain("http_server_requests").doesNotContain("\"UP\"");
        }
        // path อื่นถูก security กันก่อน (401) หรือไม่มีอยู่ (404) — อย่างไหนก็ได้ แต่ห้ามมีข้อมูลออกไป
        for (String path : new String[] {"/actuator/env", "/actuator"}) {
            HttpResponse<String> res = get(appPort, path);

            assertThat(res.statusCode()).as(path).isIn(401, 404);
            assertThat(res.body()).as(path).doesNotContain("propertySources").doesNotContain("_links");
        }
    }

    @Test
    void onlyHealthAndPrometheusAreExposed_evenOnTheManagementPort() throws Exception {
        // env/beans/heapdump มีความลับหรือข้อมูลภายใน — ต้องไม่เปิด
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/loggers"}) {
            assertThat(get(managementPort, path).statusCode()).as(path).isIn(401, 403, 404);
        }
    }
}
