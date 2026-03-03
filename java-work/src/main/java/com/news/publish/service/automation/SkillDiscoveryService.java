package com.news.publish.service.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SkillDiscoveryService {

    @Value("${app.skills-path:e:/java-Project/新闻发布程序/skills}")
    private String skillsPath;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SkillMetadata> skills = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        refreshSkills();
    }

    /**
     * 刷新并扫描技能目录 (总分模式)
     */
    public synchronized void refreshSkills() {
        File root = new File(skillsPath);
        if (!root.exists() || !root.isDirectory()) {
            log.warn("技能主目录不存在: {}", skillsPath);
            return;
        }

        File subSkillsDir = new File(root, "sub_skills");
        if (!subSkillsDir.exists() || !subSkillsDir.isDirectory()) {
            log.warn("子技能目录不存在: {}", subSkillsDir.getAbsolutePath());
            return;
        }

        File[] files = subSkillsDir.listFiles((dir, name) -> name.endsWith(".py"));
        if (files == null) return;

        skills.clear();
        for (File file : files) {
            String fileName = file.getName();
            if (fileName.equals("__init__.py")) continue;

            String platformKey = fileName.replace(".py", "");
            SkillMetadata meta = new SkillMetadata();
            meta.setId(platformKey + "_agent");
            meta.setName(platformKey.toUpperCase() + " 独立代理 Agent");
            meta.setPlatform(platformKey);
            meta.setVersion("2.0.0");
            meta.setEntry("agent_master.py"); // 所有分量都通过总 Agent 运行
            meta.setPath(root.getAbsolutePath());
            meta.setDescription("依托于 Master Agent 的独立自动化逻辑");
            
            skills.put(meta.getId(), meta);
            System.out.println(">>> [SKILL DISCOVERY] Found: " + meta.getId() + " at " + file.getAbsolutePath());
            log.info("发现独立代理分量: {} (platform: {})", meta.getId(), platformKey);
        }
    }

    public List<SkillMetadata> getAvailableSkills() {
        return new ArrayList<>(skills.values());
    }

    public SkillMetadata getSkill(String id) {
        return skills.get(id);
    }

    @Data
    public static class SkillMetadata {
        private String id;
        private String name;
        private String version;
        private String platform;
        private String entry;
        private String description;
        private List<String> params;
        private String path; // 运行时的绝对路径
    }
}
