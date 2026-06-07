package com.breadShop.XXI.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "api_request_logs")
public class ApiRequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 500)
    private String endpoint;

    @Column(length = 1000)
    private String uri;

    @Column(nullable = false)
    private int statusCode;

    @Column(nullable = false)
    private long durationMs;

    @Column(length = 255)
    private String userEmail;

    @Column(length = 50)
    private String ipAddress;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ApiRequestLog() {}

    public ApiRequestLog(String method, String endpoint, String uri,
                         int statusCode, long durationMs, String userEmail, String ipAddress) {
        this.method = method;
        this.endpoint = endpoint;
        this.uri = uri;
        this.statusCode = statusCode;
        this.durationMs = durationMs;
        this.userEmail = userEmail;
        this.ipAddress = ipAddress;
    }

    public Long getId() { return id; }
    public String getMethod() { return method; }
    public String getEndpoint() { return endpoint; }
    public String getUri() { return uri; }
    public int getStatusCode() { return statusCode; }
    public long getDurationMs() { return durationMs; }
    public String getUserEmail() { return userEmail; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
