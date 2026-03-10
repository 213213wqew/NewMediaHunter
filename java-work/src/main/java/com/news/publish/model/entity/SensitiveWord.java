package com.news.publish.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SensitiveWord {
    private Long id;
    private String word;
    private String category; // e.g., "政治", "广告", "滥用"
    private LocalDateTime createTime;
}
