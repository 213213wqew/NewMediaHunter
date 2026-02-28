package com.news.publish.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "publish_log")
public class PublishLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    private String logLevel; // INFO, ERROR
    
    @Column(columnDefinition = "LONGTEXT")
    private String message;

    @Column(columnDefinition = "LONGTEXT")
    private String requestData;

    @Column(columnDefinition = "LONGTEXT")
    private String responseData;

    private Integer httpStatus;

    @Column(columnDefinition = "LONGTEXT")
    private String stackTrace;

    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
