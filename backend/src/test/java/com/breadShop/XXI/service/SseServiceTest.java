package com.breadShop.XXI.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseService();
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Set<SseEmitter>> emitterMap() {
        return (Map<Integer, Set<SseEmitter>>) ReflectionTestUtils.getField(sseService, "emitters");
    }

    /** เปิดแท็บที่คาดว่าต้องสำเร็จ */
    private SseEmitter open(int userId) {
        return sseService.subscribe(userId).orElseThrow();
    }

    /** ใส่ emitter ปลอม (ไว้ verify ว่าถูกส่งไหม) เป็นแท็บของ user */
    private SseEmitter fakeTab(int userId) {
        SseEmitter emitter = mock(SseEmitter.class);
        emitterMap().computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(emitter);
        return emitter;
    }

    // complete()/timeout/error เรียก callback เฉพาะเมื่อมี servlet container จริง — ในเทสต้องเรียกเอง
    private static void close(SseEmitter emitter) {
        Runnable completion = (Runnable) ReflectionTestUtils.getField(emitter, "completionCallback");
        assertNotNull(completion);
        completion.run();
    }

    // ───────────── subscribe ─────────────

    @Test
    void subscribe_storesTheConnection() {
        SseEmitter emitter = open(1);

        assertThat(emitterMap().get(1)).containsExactly(emitter);
    }

    @Test
    void secondTab_doesNotKickTheFirst_bothStayConnected() {
        SseEmitter firstTab = open(1);
        SseEmitter secondTab = open(1);

        assertThat(secondTab).isNotSameAs(firstTab);
        assertThat(emitterMap().get(1)).containsExactlyInAnyOrder(firstTab, secondTab);
    }

    // ───────────── เพดานต่อคน ─────────────

    @Test
    void sixthTab_isRejected_andTheFiveOpenTabsAreUntouched() {
        List<SseEmitter> open = new ArrayList<>();
        for (int i = 0; i < SseService.MAX_TABS_PER_USER; i++) {
            open.add(open(1));
        }

        assertThat(sseService.subscribe(1)).isEmpty();   // controller ตอบ 429

        assertThat(emitterMap().get(1)).containsExactlyInAnyOrderElementsOf(open);
    }

    @Test
    void limitIsPerUser_anotherUserCanStillConnect() {
        for (int i = 0; i < SseService.MAX_TABS_PER_USER; i++) {
            open(1);
        }

        assertThat(sseService.subscribe(2)).isPresent();
    }

    @Test
    void afterATabCloses_aNewOneCanConnectAgain() {
        List<SseEmitter> open = new ArrayList<>();
        for (int i = 0; i < SseService.MAX_TABS_PER_USER; i++) {
            open.add(open(1));
        }

        close(open.get(0));

        assertThat(sseService.subscribe(1)).isPresent();
        assertThat(emitterMap().get(1)).hasSize(SseService.MAX_TABS_PER_USER);
    }

    /** หลายแท็บต่อเข้ามาพร้อมกันเป๊ะ (เช่น restore session เปิด 20 แท็บทีเดียว) — ต้องไม่เกินเพดานแม้แต่รอบเดียว */
    @Test
    void concurrentConnects_neverExceedTheLimit() throws Exception {
        int threads = 16;
        int rounds = 300;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < rounds; round++) {
                SseService fresh = new SseService();
                CyclicBarrier together = new CyclicBarrier(threads);
                List<Callable<Boolean>> connect = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    connect.add(() -> {
                        together.await();
                        return fresh.subscribe(1).isPresent();
                    });
                }
                long accepted = 0;
                for (Future<Boolean> f : pool.invokeAll(connect, 10, TimeUnit.SECONDS)) {
                    if (f.get()) {
                        accepted++;
                    }
                }
                assertThat(accepted).as("round %d", round).isEqualTo(SseService.MAX_TABS_PER_USER);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ───────────── send ─────────────

    @Test
    void send_whenUserHasNoTabOpen_doesNothing() {
        assertDoesNotThrow(() -> sseService.send(999, "payload"));
    }

    @Test
    void send_reachesEveryTabOfThatUser() throws IOException {
        SseEmitter laptop = fakeTab(1);
        SseEmitter phone = fakeTab(1);

        sseService.send(1, "payload");

        verify(laptop).send(any(SseEmitter.SseEventBuilder.class));
        verify(phone).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void send_neverReachesAnotherUser() throws IOException {
        SseEmitter alice = fakeTab(1);
        SseEmitter bob = fakeTab(2);

        sseService.send(1, "payload");

        verify(alice).send(any(SseEmitter.SseEventBuilder.class));
        verify(bob, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void send_aBrokenTabIsDropped_andTheOtherTabsStillGetIt() throws IOException {
        SseEmitter closedTab = fakeTab(1);
        SseEmitter liveTab = fakeTab(1);
        doThrow(new IOException("broken pipe")).when(closedTab).send(any(SseEmitter.SseEventBuilder.class));

        assertDoesNotThrow(() -> sseService.send(1, "payload"));

        verify(liveTab).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(emitterMap().get(1)).containsExactly(liveTab);
    }

    @Test
    void send_toAnAlreadyCompletedTab_dropsIt() throws IOException {
        SseEmitter completed = fakeTab(1);
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(completed).send(any(SseEmitter.SseEventBuilder.class));

        assertDoesNotThrow(() -> sseService.send(1, "payload"));

        assertThat(emitterMap()).doesNotContainKey(1);
    }

    // ───────────── ปิดแท็บ ─────────────

    @Test
    void closingOneTab_keepsTheOthers() {
        SseEmitter firstTab = open(1);
        SseEmitter secondTab = open(1);

        close(firstTab);

        assertThat(emitterMap().get(1)).containsExactly(secondTab);
    }

    @Test
    void closingTheLastTab_removesTheUser() {
        SseEmitter emitter = open(1);

        close(emitter);

        assertThat(emitterMap()).doesNotContainKey(1);
    }

    @Test
    void timeout_removesThatTab() {
        SseEmitter emitter = open(2);

        Runnable timeout = (Runnable) ReflectionTestUtils.getField(emitter, "timeoutCallback");
        assertNotNull(timeout);
        timeout.run();

        assertThat(emitterMap()).doesNotContainKey(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void error_removesThatTab() {
        SseEmitter emitter = open(3);

        Consumer<Throwable> error = (Consumer<Throwable>) ReflectionTestUtils.getField(emitter, "errorCallback");
        assertNotNull(error);
        error.accept(new RuntimeException("connection lost"));

        assertThat(emitterMap()).doesNotContainKey(3);
    }

    // ───────────── หลาย thread พร้อมกัน ─────────────

    /**
     * เปิด/ปิดแท็บของ user เดียวกันพร้อมกันหลาย thread — แท็บที่เพิ่งเปิดต้องอยู่ใน map ทุกครั้ง
     * (ถ้า "เพิ่มแท็บ" กับ "ลบ key ตอนแท็บสุดท้ายปิด" ไม่ atomic แท็บใหม่จะหลุดไปอยู่ใน set ที่ถูกลบทิ้ง
     *  → ไม่ได้รับแจ้งเตือนเลยจนกว่าจะต่อใหม่)
     */
    @Test
    void concurrentOpenAndClose_neverLosesAnOpenTab() throws Exception {
        int threads = SseService.MAX_TABS_PER_USER;   // แต่ละ thread เปิดค้างทีละ 1 แท็บ — ไม่ให้ชนเพดาน
        int rounds = 20_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger lostTabs = new AtomicInteger();
        try {
            List<Future<?>> done = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                done.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        SseEmitter tab = sseService.subscribe(1).orElseThrow();
                        Set<SseEmitter> tabs = emitterMap().get(1);
                        if (tabs == null || !tabs.contains(tab)) {
                            lostTabs.incrementAndGet();
                        }
                        close(tab);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : done) {
                f.get(30, TimeUnit.SECONDS);
            }

            assertThat(lostTabs.get()).as("tabs that were open but missing from the map").isZero();
            assertThat(emitterMap()).doesNotContainKey(1);   // ปิดครบทุกแท็บ → ไม่เหลือ key ค้าง
        } finally {
            pool.shutdownNow();
        }
    }
}
