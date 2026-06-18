package com.breadShop.XXI.dto;

// เอาไว้เป็น wrapper สำหรับทุก API response — เพื่อให้ frontend เข้าถึงข้อมูลได้ง่ายและ uniform | reviewed by peak
/**
 * [Claude] ApiResponse<T> — wrapper มาตรฐานสำหรับทุก API response
 *
 * ทุก endpoint ใน project นี้คืน format เดียวกันเสมอ:
 *
 *   สำเร็จ:  { "success": true,  "message": "...", "data": <T>   }
 *   ล้มเหลว: { "success": false, "message": "...", "data": null   }
 *
 * วิธีใช้ใน Controller:
 *   return ResponseEntity.ok(ApiResponse.ok(data));
 *   return ResponseEntity.ok(ApiResponse.ok("บันทึกสำเร็จ", data));
 *   return ResponseEntity.ok(ApiResponse.ok("ลบสำเร็จ"));
 *   return ResponseEntity.status(400).body(ApiResponse.error("ข้อมูลไม่ถูกต้อง"));
 *
 * วิธีใช้ใน Frontend (axios):
 *   const res = await api.get("/api/...");
 *   const data = res.data.data;  // เข้าถึงข้อมูลจาก .data.data เสมอ
 */
public class ApiResponse<T> {

    private final boolean success;
    private final String  message;
    private final T       data;

    // constructor เป็น private — ใช้ factory method แทน
    private ApiResponse(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data    = data;
    }

    // ─── Factory methods ──────────────────────────────────────────

    /** สำเร็จ — คืนข้อมูล */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "success", data);
    }

    /** สำเร็จ — คืนข้อมูลพร้อม custom message */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    /** สำเร็จ — ไม่มีข้อมูลคืน เช่น delete, logout */
    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null);
    }

    /** ล้มเหลว — ใช้ใน GlobalExceptionHandler หรือ validation fail */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }

    // ─── Getters ──────────────────────────────────────────────────
    public boolean isSuccess() { return success; }
    public String  getMessage() { return message; }
    public T       getData()    { return data; }
}
