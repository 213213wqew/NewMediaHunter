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

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SkillExecutionResult {
        private boolean success;
        private String message;
        private String url;
        private Map<String, Object> data;

        public static SkillExecutionResult error(String msg) {
            SkillExecutionResult res = new SkillExecutionResult();
            res.setSuccess(false);
            res.setMessage(msg);
            return res;
        }
    }
}
