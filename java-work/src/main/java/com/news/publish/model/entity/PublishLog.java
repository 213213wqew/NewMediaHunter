package com.news.publish.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PublishLog {
    private Long id;

    private Long taskId;

    private String logLevel; // INFO, ERROR
    
    private String message;

    private String requestData;

    private String responseData;

    private Integer httpStatus;

    private String stackTrace;

    private LocalDateTime createTime;

    public void onCreate() {
        createTime = LocalDateTime.now();
    }
}
