package com.news.publish.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 桌面版：将会话（token/username/role）保存到本机文件，重启后恢复，避免每次都要重新登录。
 */
@Slf4j
@Service
public class DesktopSessionService {

    private static final String DIR = ".news-publisher";
    private static final String FILE = "session.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void saveSession(String token, String username, String role) {
        try {
            Path dir = Paths.get(System.getProperty("user.home"), DIR);
            Files.createDirectories(dir);
            Path file = dir.resolve(FILE);
            SessionData data = new SessionData(token, username, role);
            Files.writeString(file, objectMapper.writeValueAsString(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("保存桌面会话失败: {}", e.getMessage());
        }
    }

    public SessionData restoreSession() {
        try {
            Path file = Paths.get(System.getProperty("user.home"), DIR, FILE);
            if (!Files.isRegularFile(file)) return null;
            String json = Files.readString(file, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, SessionData.class);
        } catch (Exception e) {
            log.debug("恢复桌面会话失败或不存在: {}", e.getMessage());
            return null;
        }
    }

    /** 登出或 token 失效时清除本机会话，避免下次启动恢复无效 token */
    public void clearSession() {
        try {
            Path file = Paths.get(System.getProperty("user.home"), DIR, FILE);
            Files.deleteIfExists(file);
        } catch (Exception e) {
            log.debug("清除桌面会话失败: {}", e.getMessage());
        }
    }

    @Data
    public static class SessionData {
        private String token;
        private String username;
        private String role;

        public SessionData() {}

        public SessionData(String token, String username, String role) {
            this.token = token;
            this.username = username;
            this.role = role;
        }
    }
}
