package com.news.publish.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Platform {
    private Long id;
    private String platformKey;
    private String platformName;
    private String officialUrl;
    private String configSchema;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
