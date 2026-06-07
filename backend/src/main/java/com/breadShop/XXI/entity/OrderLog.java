package com.breadShop.XXI.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_logs")
public class OrderLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 50)
    private String orderNo;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 50)
    private String oldStatus;

    @Column(nullable = false, length = 50)
    private String newStatus;

    @ManyToOne
    @JoinColumn(name = "changed_by_id")
    private User changedBy;

    @Column(length = 255)
    private String changedByName;

    @Column(length = 100)
    private String trackingNo;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OrderLog() {}

    public OrderLog(Order order, String orderNo, User user, String oldStatus, String newStatus,
                    User changedBy, String changedByName, String trackingNo, String note) {
        this.order = order;
        this.orderNo = orderNo;
        this.user = user;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.changedBy = changedBy;
        this.changedByName = changedByName;
        this.trackingNo = trackingNo;
        this.note = note;
    }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public User getChangedBy() { return changedBy; }
    public void setChangedBy(User changedBy) { this.changedBy = changedBy; }
    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String changedByName) { this.changedByName = changedByName; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
