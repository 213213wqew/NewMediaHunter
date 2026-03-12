package com.news.publish.service.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Slf4j
@Service
public class PythonSkillRunner {

    /** 可选：内置/便携 Python 可执行文件路径。配置后用户无需单独安装 Python。相对路径相对于进程工作目录。 */
    @Value("${app.python-path:}")
    private String configuredPythonPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public SkillExecutionResult execute(SkillDiscoveryService.SkillMetadata meta, Map<String, Object> params) {
        String skillDir = meta.getPath();
        File workDir = new File(skillDir);
        String pythonExec = resolvePythonExecutable(skillDir);
        File skillDirFile = new File(skillDir);
        File parentDirFile = skillDirFile.getParentFile();
        String parentDirAbs = parentDirFile != null ? parentDirFile.getAbsolutePath() : "";

        // 通过 -c 显式把 SKILL_DIR 与 SKILL_PARENT_DIR 加入 sys.path 再执行 agent_master.py，
        // 这样嵌入式/系统 Python 都能找到 pyarmor_runtime_xxxxxx（不依赖 PYTHONPATH 是否被解释器读取）
        String injectCode = "import sys, os; "
            + "sys.path.insert(0, os.environ.get('SKILL_DIR', '')); "
            + "sys.path.insert(0, os.environ.get('SKILL_PARENT_DIR', '')); "
            + "import runpy; runpy.run_path('agent_master.py', run_name='__main__')";

        ProcessBuilder pb;
        if (pythonExec != null) {
            pb = new ProcessBuilder(pythonExec, "-c", injectCode);
        } else {
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", "py", "-c", injectCode);
            } else {
                pb = new ProcessBuilder("python3", "-c", injectCode);
            }
        }
        pb.directory(workDir);

        Map<String, String> env = pb.environment();
        env.put("PYTHONUNBUFFERED", "1");
        env.put("MASTER_PATH", meta.getPath());
        env.put("SKILL_DIR", skillDir);
        if (parentDirFile != null) {
            env.put("SKILL_PARENT_DIR", parentDirAbs);
            env.put("PYTHONPATH", parentDirAbs);
        }

        String accountId = (String) params.getOrDefault("accountId", "default_acc");
        params.put("account_id", accountId);

        log.info("执行技能: workDir={}, sys.path 已注入 SKILL_DIR 与 SKILL_PARENT_DIR", workDir.getAbsolutePath());

        try {
            pb.redirectErrorStream(true); // 合并 stderr，避免缓冲区满导致阻塞，且便于看到完整错误
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

            // 解析 JSON 结果：取输出中「最后一行以 { 开头」的行（兼容前面多行 DEBUG / traceback）
            String jsonLine = findLastJsonLine(output.toString());
            if (jsonLine != null) {
                return objectMapper.readValue(jsonLine, SkillExecutionResult.class);
            }
            log.warn("未找到有效的 JSON 结果输出，完整输出: {}", output.length() > 500 ? output.substring(0, 500) + "..." : output);
            return SkillExecutionResult.error("未找到有效的 JSON 结果输出");

        } catch (Exception e) {
            log.error("执行 Python 技能失败", e);
            return SkillExecutionResult.error("启动失败: " + e.getMessage());
        }
    }

    /**
     * 解析用于执行技能的 Python 可执行文件路径。
     * 若配置了 app.python-path 且该路径存在，则使用（支持内置/便携 Python，用户无需安装）；否则返回 null 表示用系统 py/python3。
     */
    private String resolvePythonExecutable(String skillDir) {
        if (configuredPythonPath == null || configuredPythonPath.isBlank()) return null;
        String path = configuredPythonPath.trim();
        Path p = Paths.get(path);
        if (!p.isAbsolute()) {
            Path fromCwd = Paths.get(System.getProperty("user.dir", ".")).resolve(path).normalize();
            if (Files.isExecutable(fromCwd) || Files.exists(fromCwd)) {
                return fromCwd.toAbsolutePath().toString();
            }
            Path fromSkillDir = Paths.get(skillDir).resolve(path).normalize();
            if (Files.isExecutable(fromSkillDir) || Files.exists(fromSkillDir)) {
                return fromSkillDir.toAbsolutePath().toString();
            }
            return null;
        }
        if (Files.isExecutable(p) || Files.exists(p)) {
            return p.toString();
        }
        return null;
    }

    /** 从输出中找最后一行以 { 开头的行（即技能打印的 JSON），前面可有 DEBUG/traceback。 */
    private String findLastJsonLine(String output) {
        if (output == null || output.isEmpty()) return null;
        String[] lines = output.trim().split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("{")) return line;
        }
        return null;
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
