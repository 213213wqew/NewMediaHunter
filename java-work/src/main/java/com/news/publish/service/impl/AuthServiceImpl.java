package com.news.publish.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.news.publish.model.dto.LoginRequest;
import com.news.publish.model.dto.LoginResponse;
import com.news.publish.model.entity.SysUser;
import com.news.publish.service.AuthService;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String DATA_DIR = "data";
    private static final String USERS_FILE = "users.json";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // 简单内存 Token 存储，生产环境应使用 Redis 或 JWT
    private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();
    
    // 内存 User 存储
    private final Map<Long, SysUser> userStore = new ConcurrentHashMap<>();

    private Path getFilePath() {
        return Paths.get(System.getProperty("user.dir"), DATA_DIR, USERS_FILE);
    }

    @PostConstruct
    @Override
    public void initRoles() {
        Path dir = Paths.get(System.getProperty("user.dir"), DATA_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("创建数据目录失败: {}", dir, e);
        }

        Path filePath = getFilePath();
        List<SysUser> users = new ArrayList<>();

        if (Files.exists(filePath)) {
            try {
                users = objectMapper.readValue(filePath.toFile(), new TypeReference<List<SysUser>>() {});
            } catch (Exception e) {
                log.error("读取 {} 失败: {}", USERS_FILE, e.getMessage());
            }
        }

        if (users.isEmpty()) {
            SysUser admin = new SysUser();
            admin.setId(1L);
            admin.setUsername("admin");
            admin.setPassword("123456");
            admin.setRole("ADMIN");
            admin.setCreateTime(LocalDateTime.now());
            users.add(admin);

            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), users);
                log.info("已生成默认 admin 用户到 {}", filePath);
            } catch (IOException e) {
                log.error("保存默认用户到 JSON 文件失败", e);
            }
        }

        for (SysUser u : users) {
             userStore.put(u.getId(), u);
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = userStore.values().stream()
                .filter(u -> u.getUsername().equals(request.getUsername()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("用户不存在"));
                
        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        tokenStore.put(token, user.getId());

        return new LoginResponse(token, user.getUsername(), user.getRole());
    }

    @Override
    public SysUser getUserByToken(String token) {
        if (token == null) return null;
        Long userId = tokenStore.get(token);
        if (userId != null) {
            return userStore.get(userId);
        }
        return null;
    }
}
