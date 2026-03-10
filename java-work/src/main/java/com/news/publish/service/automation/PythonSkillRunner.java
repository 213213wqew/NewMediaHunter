package com.news.publish.service.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Slf4j
@Service
public class PythonSkillRunner {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkillExecutionResult execute(SkillDiscoveryService.SkillMetadata meta, Map<String, Object> params) {
        // 使用 agent_master.py 作为入口。Windows 下优先使用 py 启动器
        String osName = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;
        if (osName.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", "py", "agent_master.py");
        } else {
            pb = new ProcessBuilder("python3", "agent_master.py");
        }
        pb.directory(new File(meta.getPath()));
        
        // 自动提取账号 ID (这里以后可以根据前端传入的账号 ID 动态指定)
        // 目前暂定为 default，或者从 params 中获取
        String accountId = (String) params.getOrDefault("accountId", "default_acc");
        params.put("account_id", accountId);

        // 设置环境变量
        Map<String, String> env = pb.environment();
        env.put("PYTHONUNBUFFERED", "1");
        env.put("MASTER_PATH", meta.getPath());

        try {
            Process process = pb.start();
            
            // 写入输入参数到 stdin
            try (OutputStream os = process.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                writer.write(objectMapper.writeValueAsString(params));
                writer.flush();
            }

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Skill Log] {}", line);
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            log.info("Python 技能执行完毕，退出码: {}", exitCode);

            // 自动清理 Python 下载的临时文件
            cleanupTempFiles(meta.getPath());

            // 解析最后一行 JSON 结果
            String lastLine = getLastLine(output.toString());
            if (lastLine != null && lastLine.trim().startsWith("{")) {
                return objectMapper.readValue(lastLine, SkillExecutionResult.class);
            } else {
                return SkillExecutionResult.error("未找到有效的 JSON 结果输出");
            }

        } catch (Exception e) {
            log.error("执行 Python 技能失败", e);
            return SkillExecutionResult.error("启动失败: " + e.getMessage());
        }
    }

    private String getLastLine(String output) {
        if (output == null || output.isEmpty()) return null;
        String[] lines = output.trim().split("\n");
        return lines[lines.length - 1];
    }

    /**
     * 读取 uploads/temp_files.json 并物理删除记录的文件
     */
    private void cleanupTempFiles(String skillPath) {
        try {
            // 定位 uploads 目录。由于 Python 脚本在 skills/platforms/.. 或 skills/sub_skills 下运行，
            // 它的 uploads_dir 通常在 skills/../uploads (即根目录下的 uploads)
            // 这里的 skillPath 是 e:/java-Project/新闻发布程序/skills
            File uploadsDir = new File(new File(skillPath).getParentFile(), "uploads");
            if (!uploadsDir.exists()) {
                // 尝试备选路径：与 skills 同级的 java-work/uploads
                uploadsDir = new File(new File(skillPath).getParentFile(), "java-work/uploads");
            }
            
            File logFile = new File(uploadsDir, "temp_files.json");
            if (!logFile.exists() || logFile.length() == 0) return;

            log.info("开始清理临时文件，记录文件: {}", logFile.getAbsolutePath());
            java.util.List<String> filesToDelete = objectMapper.readValue(logFile, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
            
            int deletedCount = 0;
            for (String path : filesToDelete) {
                File f = new File(path);
                if (f.exists()) {
                    if (f.delete()) {
                        deletedCount++;
                    } else {
                        log.warn("无法删除临时文件: {}", path);
                    }
                }
            }
            
            // 清空记录文件 (不是删除 JSON，而是写回空数组，防止并发写冲突)
            objectMapper.writeValue(logFile, new java.util.ArrayList<String>());
            log.info("临时文件清理完成，共删除 {} 个文件", deletedCount);

        } catch (Exception e) {
            log.warn("清理临时文件过程中发生异常: {}", e.getMessage());
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillExecutionResult {
        private boolean success;
        private String message;
        private String url;
        private Object data; // 改为 Object，以兼容 Python 直接返回数组 [] 的场景

        public static SkillExecutionResult error(String msg) {
            SkillExecutionResult res = new SkillExecutionResult();
            res.setSuccess(false);
            res.setMessage(msg);
            return res;
        }
    }
}
