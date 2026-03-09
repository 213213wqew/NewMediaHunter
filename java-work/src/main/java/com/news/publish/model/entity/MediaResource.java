package com.news.publish.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 媒体资源洗涤表 - 记录内容中的图片/视频在各平台的对齐状态
 * (现已由数据库存储改为本地 JSON 文件存储)
 */
@Data
public class MediaResource {
    private Long id;
    private Long articleId;
    private String originalUrl;
    private String fileType; // image, video
    private Long platformId;
    private String platformMediaId;
    private String platformMediaUrl;
    private Integer uploadStatus; // 0-未上传, 1-已上传, 2-失败
    private LocalDateTime createTime;
    private Long userId;

    public void onCreate() {
        if (createTime == null) createTime = LocalDateTime.now();
        if (uploadStatus == null) uploadStatus = 0;
        if (fileType == null) fileType = "image";
    }
}

