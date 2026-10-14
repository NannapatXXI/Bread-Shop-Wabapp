package com.breadShop.XXI.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitInterceptorTest {

    private static MockHttpServletRequest loginFrom(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void evictExpired_dropsBucketsOlderThanTheWindow_keepsRecentOnes() throws Exception {
        RateLimitInterceptor limiter = new RateLimitInterceptor();
        limiter.preHandle(loginFrom("10.1.0.1"), new MockHttpServletResponse(), new Object());
        limiter.preHandle(loginFrom("10.1.0.2"), new MockHttpServletResponse(), new Object());
        assertThat(limiter.trackedClients()).isEqualTo(2);

        limiter.evictExpired(System.currentTimeMillis()); // ยังอยู่ในหน้าต่าง 1 นาที
        assertThat(limiter.trackedClients()).isEqualTo(2);

        limiter.evictExpired(System.currentTimeMillis() + 61_000); // ผ่านไปเกิน 1 นาที
        assertThat(limiter.trackedClients()).isZero();
    }

    @Test
    void otherPathsAndMethodsAreNotTracked() throws Exception {
        RateLimitInterceptor limiter = new RateLimitInterceptor();
        MockHttpServletRequest products = new MockHttpServletRequest("GET", "/api/v1/products");
        MockHttpServletRequest getLogin = new MockHttpServletRequest("GET", "/api/v1/auth/login");

        assertThat(limiter.preHandle(products, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(limiter.preHandle(getLogin, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(limiter.trackedClients()).isZero();
    }
}
