package com.news.publish.model.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class HotNewsDto {
    private String id;
    private String title;
    private String summary;
    /** 来源媒体名称，如"新华网"、"36氪" */
    private String source;
    /** 原文链接 */
    private String url;
    /** 原文参考链接（由 AI 推断，可能为示例链接） */
    private String sourceUrl;
    /** 热度指数 */
    private Integer hotScore;
    private LocalDateTime publishTime;
    /** 话题标签 */
    private List<String> tags;
}

