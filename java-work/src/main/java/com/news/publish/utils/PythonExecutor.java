package com.news.publish.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 跨语言执行器：负责调用外部 Python 脚本并捕获输出
 * 使用独立线程读取 stdout / stderr，避免缓冲区死锁
 */
@Slf4j
@Component
public class PythonExecutor {

    /** Windows 控制台的默认编码通常是 GBK，Python 脚本输出中文时可能走系统编码 */
    private static final Charset SYS_CHARSET = Charset.forName(
            System.getProperty("os.name", "").toLowerCase().contains("win") ? "GBK" : "UTF-8"
    );

    /**
     * 执行 Python 脚本并获取返回的 JSON 字符串
     */
    public String execute(String scriptPath, List<String> args) {
        // 依次尝试多种 Python 可执行名称（兼容各种安装方式）
        for (String pythonCmd : new String[]{"py", "python", "python3"}) {
            try {
                String result = doExecute(pythonCmd, scriptPath, args);
                if (result != null && (result.contains("[") || result.contains("{"))) {
                    return result;
                }
            } catch (Exception e) {
                log.debug("Python command '{}' failed: {}", pythonCmd, e.getMessage());
            }
        }
        log.error("All python executable attempts failed.");
        return null;
    }

    private String doExecute(String pythonCmd, String scriptPath, List<String> args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(pythonCmd);
        command.add(scriptPath);
        command.addAll(args);

        log.info("Executing: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        // 强制 Python 使用 UTF-8 输出，解决 Windows 默认 GBK 编码无法处理 emoji 的问题
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONUTF8", "1");
        pb.redirectErrorStream(false);

        Process process = pb.start();

        ExecutorService exec = Executors.newFixedThreadPool(2);
        // 用 UTF-8 读取 stdout/stderr（已通过 PYTHONIOENCODING 保证 Python 按 UTF-8 写）
        Future<String> stdoutFuture = exec.submit(() -> readStream(process.getInputStream(), StandardCharsets.UTF_8, false));
        Future<String> stderrFuture = exec.submit(() -> readStream(process.getErrorStream(), StandardCharsets.UTF_8, true));

        String stdout = stdoutFuture.get(60, TimeUnit.SECONDS);
        String stderr = stderrFuture.get(60, TimeUnit.SECONDS);

        int exitCode = process.waitFor();
        exec.shutdown();

        if (!stderr.isEmpty()) {
            log.warn("Python stderr:\n{}", stderr);
        }

        if (exitCode != 0) {
            log.error("Python exited with code: {}; stderr={}", exitCode, stderr);
            return null;
        }

        return stdout;
    }

    private String readStream(InputStream is, Charset charset, boolean isStderr) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isStderr) {
                    sb.append(line).append("\n");
                } else {
                    // stdout：收集所有内容（包含 JSON 和可能的 Python print 调试信息）
                    sb.append(line).append("\n");
                }
            }
        } catch (Exception e) {
            log.debug("Stream read error: {}", e.getMessage());
        }
        return sb.toString();
    }
}
