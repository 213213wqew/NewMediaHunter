package com.news.publish.model.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 视频批量发布请求：多视频按账号轮询分配，后端按账号串行执行，最多 9 个账号并发。
 */
@Data
public class VideoBatchPublishRequest {
    private List<Long> articleIds;
    private List<Long> accountIds;
    private LocalDateTime scheduledTime;
}
