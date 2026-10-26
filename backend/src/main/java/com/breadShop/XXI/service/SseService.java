package com.breadShop.XXI.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * ช่องทาง realtime (Server-Sent Events) ไปหา browser ของลูกค้า — 1 user มีได้ 1 การเชื่อมต่อ
 * (เปิดแท็บใหม่ แท็บเก่าจะถูกตัด) ข้อจำกัด: เก็บใน memory ใช้ได้กับ backend เครื่องเดียว
 */
@Service
public class SseService {

    private static final long TIMEOUT_MS = 5 * 60 * 1000L;   // หมดเวลาแล้ว browser จะต่อใหม่เอง

    private final Map<Integer, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Integer userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        SseEmitter old = emitters.put(userId, emitter);
        if (old != null) {
            try {
                old.complete();   // ตัดแท็บเก่า
            } catch (IllegalStateException ignored) {
                // แท็บเก่าหลุดไปเองแล้ว — ไม่ต้องทำอะไร
            }
        }
        // remove(key, value) — ลบเฉพาะถ้ายังเป็นตัวนี้ ไม่ไปลบตัวใหม่ที่มาแทนแล้ว
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitter.onError(e -> emitters.remove(userId, emitter));
        return emitter;
    }

    /** ส่ง event "notification" — user ไม่ได้เปิดหน้าเว็บอยู่ก็ข้ามไป (ยังดูย้อนหลังจาก DB ได้) */
    public void send(Integer userId, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("notification").data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitters.remove(userId, emitter);
        }
    }
}
