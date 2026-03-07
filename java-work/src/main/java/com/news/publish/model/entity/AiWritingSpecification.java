package com.news.publish.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiWritingSpecification {
    private Long id;
    private String name; 
    private String category; 
    private String promptContent; 
    private Boolean isDefault = false; 
    private Boolean isSystem = false; // 系统级判定字段
    private Long userId; 
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

