package com.news.publish.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 媒体资源洗涤表 - 记录内容中的图片/视频在各平台的对齐状态
 */
@Data
@Entity
@Table(name = "media_resource")
public class MediaResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long articleId;

    @Column(nullable = false)
    private String originalUrl;

    private String fileType; // image, video

    private Long platformId;

    private String platformMediaId;

    @Column(name = "platform_media_url")
    private String platformMediaUrl;

    private Integer uploadStatus; // 0-未上传, 1-已上传, 2-失败

    private LocalDateTime createTime;

    @Column(nullable = false)
    private Long userId;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (uploadStatus == null) uploadStatus = 0;
        if (fileType == null) fileType = "image";
    }
}
