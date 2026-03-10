package com.news.publish.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Article {
    private Long id;

    private String title;

    private String content;

    private String contentType; // html, markdown
    private String author;
    private String summary;
    private String coverImage;
    /** 视频地址（当 contentType 为 video 时使用，用于视频分发） */
    private String videoUrl;
    private String category;
    private String tags;

    private Integer status; // 0-草稿, 1-就绪

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    private String platformSettings;

    private Long userId;

    public void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (status == null) status = 0;
    }

    public void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
