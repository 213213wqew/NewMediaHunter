package com.news.publish.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.news.publish.model.entity.AiConfig;
import com.news.publish.service.AiConfigService;
import com.news.publish.interceptor.UserContext;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiConfigServiceImpl implements AiConfigService {

    private static final String DATA_DIR = "data";
    private static final String CONFIG_FILE = "ai_config.json";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // 内存存储: Key 为 UserID
    private final Map<Long, AiConfig> configStore = new ConcurrentHashMap<>();

    private Path getFilePath() {
        return Paths.get(System.getProperty("user.dir"), DATA_DIR, CONFIG_FILE);
    }

    @PostConstruct
    public void init() {
        Path dir = Paths.get(System.getProperty("user.dir"), DATA_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("创建数据目录失败: {}", dir, e);
        }

        Path filePath = getFilePath();
        if (Files.exists(filePath)) {
            try {
                List<AiConfig> configs = objectMapper.readValue(filePath.toFile(), new TypeReference<List<AiConfig>>() {});
                if (configs != null) {
                    for (AiConfig config : configs) {
                        configStore.put(config.getUserId(), config);
                    }
                }
            } catch (Exception e) {
                log.error("读取 {} 失败: {}", CONFIG_FILE, e.getMessage());
            }
        }
    }

    private synchronized void saveToFile() {
        try {
            List<AiConfig> configs = new ArrayList<>(configStore.values());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(getFilePath().toFile(), configs);
        } catch (IOException e) {
            log.error("保存 AiConfig JSON 失败", e);
        }
    }

    @Override
    public AiConfig getConfig() {
        Long userId = UserContext.getUserId();
        if (userId == null) return null;
        return configStore.get(userId);
    }

    @Override
    public AiConfig saveConfig(AiConfig config) {
        Long userId = UserContext.getUserId();
        config.setUserId(userId);
        
        // 查找已有配置决定是否分配新Id或继承老Id
        AiConfig existing = configStore.get(userId);
        if (existing != null) {
            config.setId(existing.getId());
        } else {
            long maxId = configStore.values().stream().mapToLong(c -> c.getId() == null ? 0 : c.getId()).max().orElse(0L);
            config.setId(maxId + 1);
        }
        
        config.setUpdateTime(LocalDateTime.now());
        configStore.put(userId, config);
        saveToFile();
        
        return config;
    }
}
