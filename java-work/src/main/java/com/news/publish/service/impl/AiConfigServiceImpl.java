package com.news.publish.service.impl;

import com.news.publish.model.entity.AiConfig;
import com.news.publish.repository.AiConfigRepository;
import com.news.publish.service.AiConfigService;
import com.news.publish.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private final AiConfigRepository aiConfigRepository;

    @Override
    public AiConfig getConfig() {
        Long userId = UserContext.getUserId();
        if (userId == null) return null;
        return aiConfigRepository.findByUserId(userId).orElse(null);
    }

    @Override
    public AiConfig saveConfig(AiConfig config) {
        Long userId = UserContext.getUserId();
        config.setUserId(userId);
        // 查找已有配置进行 upsert
        aiConfigRepository.findByUserId(userId).ifPresent(existing -> config.setId(existing.getId()));
        return aiConfigRepository.save(config);
    }
}
