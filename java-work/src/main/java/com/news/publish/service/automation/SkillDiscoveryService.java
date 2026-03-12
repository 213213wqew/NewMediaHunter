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
        log.info("开始扫描技能目录，配置路径: {}", skillsPath);
        File root = new File(skillsPath);
        if (!root.exists() || !root.isDirectory()) {
            // 尝试将路径转换为绝对路径再打印，帮助排查是否因相对路径起点不对导致找不到
            log.warn("技能主目录不存在或不是目录: {} (绝对路径: {})", skillsPath, root.getAbsolutePath());
            return;
        }

        log.info("正在从 {} 扫描技能...", root.getAbsolutePath());
        skills.clear();

        File platformsDir = new File(root, "platforms");
        File actualRoot = root;
        if (!platformsDir.exists() || !platformsDir.isDirectory()) {
            // 尝试扫描嵌套在 skills/platforms 下的情况（PyArmor 打包产生的结构）
            File nestedSkills = new File(root, "skills");
            File nestedPlatforms = new File(nestedSkills, "platforms");
            if (nestedPlatforms.exists() && nestedPlatforms.isDirectory()) {
                log.info("检测到嵌套技能目录，切换扫描起点至: {}", nestedSkills.getAbsolutePath());
                platformsDir = nestedPlatforms;
                actualRoot = nestedSkills;
            }
        }

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
                    meta.setPath(actualRoot.getAbsolutePath()); // 这里使用实际包含 agent_master.py 的目录
                    meta.setDescription("platforms/" + platformKey + "：登录/Token/注入分离，支持多账号");
                    skills.put(meta.getId(), meta);
                    log.info("发现平台技能: {} (platform: {}, path: {})", meta.getId(), platformKey, dir.getAbsolutePath());
                }
            }
        } else {
            log.info("未找到 platforms 目录: {}", platformsDir.getAbsolutePath());
        }

        if (skills.isEmpty()) {
            File subSkillsDir = new File(actualRoot, "sub_skills");
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
                        meta.setPath(actualRoot.getAbsolutePath());
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
