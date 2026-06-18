package com.breadShop.XXI.service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SseService {

    private final Map<Integer, SseEmitter> emitters = new ConcurrentHashMap<>(); //ใช้ ConcurrentHashMap เพราะ user subscribe และ admin ส่ง notification มาพร้อมกันหลาย request ในเวลาเดียวกัน

    /** 
     * สร้าง SseEmitter สำหรับผู้ใช้ที่ระบุ และเก็บไว้ใน emitters map โดยถ้ามี emitter เก่าของผู้ใช้นั้นอยู่แล้ว จะทำการ complete emitter เก่าและแทนที่ด้วย emitter ใหม่
     * @param userId รหัสผู้ใช้ที่ต้องการ subscribe สำหรับรับ notification ผ่าน SSE
     * @return SseEmitter ใหม่ที่สร้างขึ้นสำหรับผู้ใช้
     */
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

    /**
     * ส่งข้อมูล notification ไปยังผู้ใช้ที่ระบุผ่าน SseEmitter โดยถ้าไม่มี emitter ของผู้ใช้นั้นอยู่ จะไม่ทำอะไร
     * @param userId รหัสผู้ใช้ที่ต้องการส่ง notification
     * @param data ข้อมูล notification ที่จะส่งไปยังผู้ใช้ โดยจะถูกส่งในรูปแบบ JSON
     */
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
