package com.news.publish.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户 AI 模型配置（每用户独立一条记录）
 */
@Data
@Entity
@Table(name = "ai_config")
public class AiConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 提供商标识：openai / gemini / claude / qianwen / zhipu / deepseek / ollama / custom
     */
    private String provider;

    /**
     * 接口基地址，例如 https://api.openai.com/v1
     */
    private String baseUrl;

    /**
     * API Key（明文存储，生产建议加密）
     */
    private String apiKey;

    /**
     * 模型名称，例如 gpt-4o、gemini-pro、deepseek-chat
     */
    private String modelName;

    /**
     * 所属用户 ID（每个用户独立配置）
     */
    @Column(nullable = false, unique = true)
    private Long userId;

    private LocalDateTime updateTime;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
