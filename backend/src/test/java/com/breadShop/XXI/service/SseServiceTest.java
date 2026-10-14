package com.breadShop.XXI.service;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseService();
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, SseEmitter> emitterMap() {
        return (Map<Integer, SseEmitter>) ReflectionTestUtils.getField(sseService, "emitters");
    }

    @Test
    void subscribe_shouldCreateAndStoreEmitter() {
        SseEmitter emitter = sseService.subscribe(1);

        assertNotNull(emitter);
        assertThat(emitterMap()).containsKey(1);
        assertThat(emitterMap().get(1)).isSameAs(emitter);
    }

    @Test
    void subscribe_whenOldEmitterExists_shouldReplaceWithoutThrowing() {
        SseEmitter first = sseService.subscribe(1);

        SseEmitter second = assertDoesNotThrow(() -> sseService.subscribe(1));

        assertThat(second).isNotSameAs(first);
        assertThat(emitterMap().get(1)).isSameAs(second);
    }

    @Test
    void send_whenNoEmitterForUser_shouldDoNothing() {
        assertDoesNotThrow(() -> sseService.send(999, "payload"));
    }

    @Test
    void send_whenEmitterExists_shouldSendEvent() throws IOException {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        emitterMap().put(1, mockEmitter);

        sseService.send(1, "payload");

        verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(emitterMap()).containsKey(1);
    }

    @Test
    void send_whenIOExceptionThrown_shouldRemoveEmitter() throws IOException {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        emitterMap().put(1, mockEmitter);

        assertDoesNotThrow(() -> sseService.send(1, "payload"));

        assertThat(emitterMap()).doesNotContainKey(1);
    }

    @Test
    void subscribe_whenEmitterCompletes_shouldRemoveFromMap() {
        SseEmitter emitter = sseService.subscribe(1);
        assertThat(emitterMap()).containsKey(1);

        // complete() เรียก onCompletion เฉพาะเมื่อมี servlet container จริง — ในเทสต้องเรียก callback เอง
        Runnable completionCallback = (Runnable) ReflectionTestUtils.getField(emitter, "completionCallback");
        assertNotNull(completionCallback);
        completionCallback.run();

        assertThat(emitterMap()).doesNotContainKey(1);
    }

    @Test
    void subscribe_whenEmitterTimesOut_shouldRemoveFromMap() {
        SseEmitter emitter = sseService.subscribe(2);
        assertThat(emitterMap()).containsKey(2);

        Runnable timeoutCallback = (Runnable) ReflectionTestUtils.getField(emitter, "timeoutCallback");
        assertNotNull(timeoutCallback);
        timeoutCallback.run();

        assertThat(emitterMap()).doesNotContainKey(2);
    }

    @SuppressWarnings("unchecked")
    @Test
    void subscribe_whenEmitterErrors_shouldRemoveFromMap() {
        SseEmitter emitter = sseService.subscribe(3);
        assertThat(emitterMap()).containsKey(3);

        java.util.function.Consumer<Throwable> errorCallback =
                (java.util.function.Consumer<Throwable>) ReflectionTestUtils.getField(emitter, "errorCallback");
        assertNotNull(errorCallback);
        errorCallback.accept(new RuntimeException("connection lost"));

        assertThat(emitterMap()).doesNotContainKey(3);
    }
}
