package com.news.publish.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Account {
    private Long id;

    private Long platformId;

    private String accountName;

    private String appId;
    private String appSecret;

    private String accessToken;
    
    private String refreshToken;

    private LocalDateTime tokenExpiresAt;

    private String cookieData;

    private Integer status; // 1-正常, 0-禁用

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private Long userId;

    public void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) status = 1;
    }

    public void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
