package com.news.publish.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 本地文件中的发布任务记录，用于 JSON 序列化（不依赖数据库） */
@Data
public class PublishTaskRecord {
    private Long id;
    private Long userId;
    private Long articleId;
    private Long accountId;
    private Integer publishStatus;
    private String platformArticleId;
    private String platformArticleUrl;
    private String errorMessage;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime scheduledTime;
}
