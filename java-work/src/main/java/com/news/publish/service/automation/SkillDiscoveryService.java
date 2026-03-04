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
     * 刷新并扫描技能目录。
     * 优先扫描 platforms/ 子目录（登录/Token/注入已拆分，按平台+账号隔离）；若无则回退到 sub_skills/*.py。
     */
    public synchronized void refreshSkills() {
        File root = new File(skillsPath);
        if (!root.exists() || !root.isDirectory()) {
            log.warn("技能主目录不存在: {}", skillsPath);
            return;
        }

        skills.clear();

        File platformsDir = new File(root, "platforms");
        if (platformsDir.exists() && platformsDir.isDirectory()) {
            File[] dirs = platformsDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    String name = dir.getName();
                    if (name.startsWith("__")) continue;
                    String platformKey = name;
                    SkillMetadata meta = new SkillMetadata();
                    meta.setId(platformKey + "_agent");
                    meta.setName(platformKey.toUpperCase() + " 独立代理 Agent");
                    meta.setPlatform(platformKey);
                    meta.setVersion("2.0.0");
                    meta.setEntry("agent_master.py");
                    meta.setPath(root.getAbsolutePath());
                    meta.setDescription("platforms/" + platformKey + "：登录/Token/注入分离，支持多账号");
                    skills.put(meta.getId(), meta);
                    log.info("发现平台技能: {} (platform: {})", meta.getId(), platformKey);
                }
            }
        }

        if (skills.isEmpty()) {
            File subSkillsDir = new File(root, "sub_skills");
            if (subSkillsDir.exists() && subSkillsDir.isDirectory()) {
                File[] files = subSkillsDir.listFiles((d, n) -> n != null && n.endsWith(".py"));
                if (files != null) {
                    for (File file : files) {
                        String fileName = file.getName();
                        if ("__init__.py".equals(fileName)) continue;
                        String platformKey = fileName.replace(".py", "");
                        SkillMetadata meta = new SkillMetadata();
                        meta.setId(platformKey + "_agent");
                        meta.setName(platformKey.toUpperCase() + " 独立代理 Agent");
                        meta.setPlatform(platformKey);
                        meta.setVersion("2.0.0");
                        meta.setEntry("agent_master.py");
                        meta.setPath(root.getAbsolutePath());
                        meta.setDescription("依托于 Master Agent 的独立自动化逻辑");
                        skills.put(meta.getId(), meta);
                        log.info("发现独立代理分量: {} (platform: {})", meta.getId(), platformKey);
                    }
                }
            }
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
