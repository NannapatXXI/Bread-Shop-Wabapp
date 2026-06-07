package com.breadShop.XXI.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SseService {

    private final Map<Integer, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Integer userId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        SseEmitter old = emitters.put(userId, emitter);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) {}
        }
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitter.onError(e -> emitters.remove(userId, emitter));
        return emitter;
    }

    public void send(Integer userId, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name("notification").data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitters.remove(userId);
        }
    }
}
