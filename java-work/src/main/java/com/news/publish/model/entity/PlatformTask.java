package com.news.publish.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PlatformTask {
    private Long id;

    private String platformKey;

    private String topic;

    private String extraInfo;

    private LocalDateTime updateTime;

    public void preUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
