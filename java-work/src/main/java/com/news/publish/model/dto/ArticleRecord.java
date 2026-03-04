package com.news.publish.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 本地文件中的文章记录，用于 JSON 序列化（不依赖数据库） */
@Data
public class ArticleRecord {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String contentType;
    private String author;
    private String summary;
    private String coverImage;
    private String videoUrl;
    private String category;
    private String tags;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String platformSettings;
}
