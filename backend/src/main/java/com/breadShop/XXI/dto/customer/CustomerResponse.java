package com.breadShop.XXI.dto.customer;

import java.time.LocalDateTime;

/**
 * DTO สำหรับแสดงรายชื่อ user ในหน้า admin customer list
 * ไม่ expose password หรือ internal fields
 */
public class CustomerResponse {

    private final Integer id;
    private final String  username;
    private final String  email;
    private final String  role;
    private final String  provider;    // "credentials" | "google"
    private final LocalDateTime createdAt;
    private final int     orderCount;  // จำนวน order ที่เคยสั่ง
    private final boolean isActive;    // false = banned

    public CustomerResponse(Integer id, String username, String email,
                             String role, String provider,
                             LocalDateTime createdAt, int orderCount, boolean isActive) {
        this.id         = id;
        this.username   = username;
        this.email      = email;
        this.role       = role;
        this.provider   = provider;
        this.createdAt  = createdAt;
        this.orderCount = orderCount;
        this.isActive   = isActive;
    }

    public Integer getId()            { return id; }
    public String  getUsername()      { return username; }
    public String  getEmail()         { return email; }
    public String  getRole()          { return role; }
    public String  getProvider()      { return provider; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public int     getOrderCount()    { return orderCount; }
    public boolean getIsActive()       { return isActive; }
}
