package com.breadShop.XXI.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitInterceptorTest {

    private static MockHttpServletRequest postFrom(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void eleventhPostFromTheSameIp_isBlockedWith429_otherIpsStillPass() throws Exception {
        RateLimitInterceptor limiter = new RateLimitInterceptor();
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.preHandle(postFrom("10.2.0.1"), new MockHttpServletResponse(), new Object())).isTrue();
        }

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        assertThat(limiter.preHandle(postFrom("10.2.0.1"), blocked, new Object())).isFalse();
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();

        assertThat(limiter.preHandle(postFrom("10.2.0.2"), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void concurrentRequestsFromOneIp_neverLetMoreThanTenThrough() throws Exception {
        RateLimitInterceptor limiter = new RateLimitInterceptor();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();

        for (int i = 0; i < 200; i++) {
            pool.submit(() -> {
                start.await();
                if (limiter.preHandle(postFrom("10.3.0.1"), new MockHttpServletResponse(), new Object())) {
                    allowed.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(allowed.get()).isEqualTo(10); // นับไม่หายแม้ยิงพร้อมกัน 200 ครั้ง
    }

    @Test
    void evictExpired_dropsWindowsOlderThanOneMinute_keepsRecentOnes() throws Exception {
        RateLimitInterceptor limiter = new RateLimitInterceptor();
        limiter.preHandle(postFrom("10.1.0.1"), new MockHttpServletResponse(), new Object());
        limiter.preHandle(postFrom("10.1.0.2"), new MockHttpServletResponse(), new Object());
        assertThat(limiter.trackedClients()).isEqualTo(2);

        limiter.evictExpired(System.currentTimeMillis());
        assertThat(limiter.trackedClients()).isEqualTo(2);

        limiter.evictExpired(System.currentTimeMillis() + 61_000);
        assertThat(limiter.trackedClients()).isZero();
    }

    @Test
    void nonPostRequestsAreNotCounted() throws Exception {
        RateLimitInterceptor limiter = new RateLimitInterceptor();
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        get.setRemoteAddr("10.4.0.1");

        for (int i = 0; i < 20; i++) {
            assertThat(limiter.preHandle(get, new MockHttpServletResponse(), new Object())).isTrue();
        }
        assertThat(limiter.trackedClients()).isZero();
    }
}
