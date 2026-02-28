package com.news.publish.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "account")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long platformId;

    @Column(nullable = false)
    private String accountName;

    private String appId;
    private String appSecret;

    @Column(columnDefinition = "TEXT")
    private String accessToken;
    
    @Column(columnDefinition = "TEXT")
    private String refreshToken;

    private LocalDateTime tokenExpiresAt;

    @Column(columnDefinition = "TEXT")
    private String cookieData;

    private Integer status; // 1-正常, 0-禁用

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Column(nullable = false)
    private Long userId;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) status = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
