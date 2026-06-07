package com.breadShop.XXI.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// --- 1. Database Entity ---
@Entity
@Table(name = "usersapp") 
public class User {
    
   
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, unique = true)
    private String email;
   

    @Column(name = "provider")
    private String provider; // จะเก็บค่า "google" หรือ "credentials" เพื่อทำให่้รู้ว่า login แบบไหน

    @Column(nullable = false)
    private String password;

   
    @CreationTimestamp 
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp 
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = true) 
    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    private String role = "USER"; // ค่าเริ่มต้น

    @Column(nullable = false)
    private boolean isActive = true; // false = banned

    // (Constructors, Getters, Setters)
   
   public User() {
    this.role = "USER";
}
   
    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = "USER";
        this.provider= ("credentials"); //สำหรับ user ที่สมัครผ่านระบบปกติ
    } 

    public User(String username, String email, String password, String role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role != null ? role : "USER";
    }
    
    public Integer getId() { return id; }
   
    public String getProvider() { return provider; }
   
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRole() {
        return role != null ? role.toUpperCase() : "USER";
    }
    
    public void setRole(String role) { this.role = role; }
    public void setProvider(String provider) { this.provider = provider; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { this.isActive = active; }

    // (Getters/Setters ใหม่ 3 อัน)
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}