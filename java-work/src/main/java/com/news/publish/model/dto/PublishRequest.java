package com.news.publish.model.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发布请求 DTO
 */
@Data
public class PublishRequest {
    private Long articleId;     // 文章ID
    private List<Long> accountIds; // 要发布的账号ID列表
    private LocalDateTime scheduledTime; // 预计发布时间 (可选)
}
