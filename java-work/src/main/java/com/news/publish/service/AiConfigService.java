package com.news.publish.service;

import com.news.publish.model.entity.AiConfig;

public interface AiConfigService {
    /** 获取当前登录用户的 AI 配置，没有则返回 null */
    AiConfig getConfig();

    /** 保存（新增或更新）当前用户的 AI 配置 */
    AiConfig saveConfig(AiConfig config);
}
