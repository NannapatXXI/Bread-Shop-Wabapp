package com.breadShop.XXI.service;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * ช่องทาง realtime (Server-Sent Events) ไปหา browser — 1 user มีได้หลายการเชื่อมต่อ (หลายแท็บ / มือถือ + คอม)
 * แจ้งเตือนส่งไปทุกการเชื่อมต่อของ user นั้น สูงสุด MAX_TABS_PER_USER สาย
 * ข้อจำกัด: เก็บใน memory ใช้ได้กับ backend เครื่องเดียว
 */
@Service
public class SseService {

    private static final long TIMEOUT_MS = 5 * 60 * 1000L;   // หมดเวลาแล้ว browser จะต่อใหม่เอง

    /** กันคนเดียวเปิดสายค้างไว้เป็นร้อยจน connection ของ server หมด */
    static final int MAX_TABS_PER_USER = 5;

    private final Map<Integer, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * เปิดการเชื่อมต่อใหม่ของ user — แท็บที่เปิดอยู่แล้วไม่ถูกตัด
     * (เดิมตัดแท็บเก่า → แท็บเก่าต่อใหม่ไปตัดแท็บใหม่ วนแย่งกันทุกไม่กี่วินาที)
     *
     * ครบ MAX_TABS_PER_USER แล้ว → empty ให้แท็บใหม่ (controller ตอบ 429) ไม่ไปปิดแท็บเก่า
     * (ปิดแท็บเก่า = มันต่อกลับมาเตะแท็บอื่นวนไม่จบ)
     * browser ได้ 429 จะปิดสายถาวร แล้ว NotificationContext ลองต่อใหม่แบบเว้นระยะ (สูงสุด 30 วินาที)
     */
    public Optional<SseEmitter> subscribe(Integer userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        AtomicBoolean added = new AtomicBoolean();
        // เช็คเพดาน + เพิ่ม ใน compute เดียว — ทำพร้อมกับ subscribe/remove ของสายอื่นไม่ได้
        // (ไม่เกินเพดานแม้ต่อพร้อมกัน และไม่หลุดไปอยู่ใน set ที่เพิ่งถูกลบทิ้ง)
        emitters.compute(userId, (id, set) -> {
            Set<SseEmitter> tabs = set != null ? set : ConcurrentHashMap.newKeySet();
            if (tabs.size() < MAX_TABS_PER_USER) {
                added.set(tabs.add(emitter));
            }
            return tabs.isEmpty() ? null : tabs;
        });
        if (!added.get()) {
            return Optional.empty();
        }
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));
        return Optional.of(emitter);
    }

    /** ส่ง event "notification" ไปทุกแท็บของ user — ไม่ได้เปิดหน้าเว็บอยู่ก็ข้ามไป (ยังดูย้อนหลังจาก DB ได้) */
    public void send(Integer userId, Object data) {
        Set<SseEmitter> tabs = emitters.get(userId);
        if (tabs == null) {
            return;
        }
        for (SseEmitter emitter : tabs) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                // แท็บนี้ปิดไปแล้ว — เอาออก แท็บอื่นยังได้รับตามปกติ
                remove(userId, emitter);
            }
        }
    }

    /** เอาสายนี้ออก ถ้า user ไม่เหลือสายไหนเลยก็ลบ key ทิ้ง (คืน null จาก computeIfPresent = ลบ key) */
    private void remove(Integer userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, tabs) -> {
            tabs.remove(emitter);
            return tabs.isEmpty() ? null : tabs;
        });
    }
}
