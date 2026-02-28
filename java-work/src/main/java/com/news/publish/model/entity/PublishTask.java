package com.news.publish.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "publish_task")
public class PublishTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long articleId;

    @Column(nullable = false)
    private Long accountId;

    private Integer publishStatus; // 0-待处理, 1-排号中, 2-发布中, 3-成功, 4-失败
    
    private String platformArticleId;
    private String platformArticleUrl;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime scheduledTime; // 预计发布时间

    @Column(nullable = false)
    private Long userId;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (publishStatus == null) publishStatus = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
