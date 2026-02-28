package com.news.publish.service.automation;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ScreenshotType;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class InteractiveBrowserService {

    private Playwright playwright;
    private BrowserContext persistentContext;

    @Value("${browser.headless:false}")
    private boolean headless;

    @Value("${browser.chrome-path:}")
    private String chromePath;

    private final Map<String, Page> sessions = new ConcurrentHashMap<>();

    // 用户数据目录路径
    private static final String USER_DATA_DIR =
            System.getProperty("user.home") + "/.news-publisher/browser-data";

    /**
     * 懒加载：确保持久化浏览器上下文可用
     * - 首次调用时才启动浏览器（不在应用启动时打开）
     * - 如果浏览器被关闭或崩溃，自动重建
     */
    private synchronized void ensureBrowserReady() {
        // 检查现有上下文是否可用
        if (persistentContext != null) {
            try {
                // 尝试一个网络交互操作来检测上下文是否存活
                // 注意：.pages() 是本地缓存，不能检测断开，必须用通信方法如 .cookies()
                persistentContext.cookies();
                return; // 上下文正常，直接返回
            } catch (Exception e) {
                log.warn("持久化上下文已失效，正在重建: {}", e.getMessage());
                cleanupQuietly();
            }
        }

        log.info("正在启动持久化浏览器 (headless={})...", headless);

        // 确保用户数据目录存在
        java.io.File dir = new java.io.File(USER_DATA_DIR);
        if (!dir.exists()) dir.mkdirs();

        // 清理可能存在的锁文件（防止上次崩溃后锁住）
        for (String lockName : new String[]{"SingletonLock", "SingletonCookie", "SingletonSocket"}) {
            java.io.File lock = new java.io.File(USER_DATA_DIR, lockName);
            if (lock.exists()) {
                lock.delete();
                log.info("已清理锁文件: {}", lockName);
            }
        }

        if (playwright != null) {
            try { playwright.close(); } catch (Exception ignored) {}
        }
        playwright = Playwright.create();

        BrowserType.LaunchPersistentContextOptions opts =
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(headless)
                        .setViewportSize(1280, 800)
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .setArgs(Arrays.asList(
                                "--no-sandbox",
                                "--disable-blink-features=AutomationControlled",
                                "--disable-infobars"
                        ));
        
        // 如果配置了本地 Chrome 路径，则使用
        if (chromePath != null && !chromePath.trim().isEmpty()) {
            java.io.File chromeFile = new java.io.File(chromePath);
            if (chromeFile.exists()) {
                opts.setExecutablePath(Paths.get(chromePath));
                log.info("使用自定义浏览器路径: {}", chromePath);
            }
        }

        persistentContext = playwright.chromium().launchPersistentContext(
                Paths.get(USER_DATA_DIR), opts);

        log.info("持久化浏览器启动成功！数据目录: {}", USER_DATA_DIR);
    }

    /**
     * 启动一个新会话
     */
    public String startSession(String url, String cookieJson) {
        String sessionId = "sess_" + System.currentTimeMillis();
        log.info("启动会话 {}, 目标URL: {}", sessionId, url);

        Page page = null;
        for (int i = 0; i < 2; i++) {
            // 确保浏览器就绪
            ensureBrowserReady();
            try {
                page = persistentContext.newPage();
                break;
            } catch (PlaywrightException e) {
                if (e.getMessage() != null && (e.getMessage().contains("close") || e.getMessage().contains("TargetClosedError"))) {
                    log.warn("无法创建新页面，浏览器持久化上下文失效，将在重试中重建: {}", e.getMessage());
                    cleanupQuietly(); // 清理失效上下文
                } else {
                    throw e;
                }
            }
        }

        if (page == null) {
            throw new RuntimeException("无法创建新页面，持久化浏览器重新初始化失败");
        }
        
        // 如果提供了 Cookie，则注入
        if (cookieJson != null && !cookieJson.trim().isEmpty() && !"{ }".equals(cookieJson.trim())) {
            try {
                // 简单的 Cookie 注入。实际可能需要解析 JSON
                log.info("正在为会话 {} 注入初始化 Cookie...", sessionId);
                // 这里暂不解析复杂 JSON，假设由 Skill 自己处理或通过 Context 自动携带
            } catch (Exception e) {
                log.warn("Cookie 注入失败: {}", e.getMessage());
            }
        }

        try {
            page.navigate(url, new Page.NavigateOptions().setTimeout(45000)); // 扩容超时
            log.info("会话 {} 页面加载完成: {}", sessionId, url);
        } catch (Exception e) {
            log.warn("会话 {} 页面初次加载超时或异常，继续尝试: {}", sessionId, e.getMessage());
        }

        sessions.put(sessionId, page);
        return sessionId;
    }

    /**
     * 获取页面截图 (Base64)
     */
    public String captureScreenshot(String sessionId) {
        Page page = sessions.get(sessionId);
        if (page == null) return null;

        try {
            // 如果页面已经关闭，则直接返回 null 避免报错
            if (page.isClosed()) {
                sessions.remove(sessionId);
                return null;
            }
            
            byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                    .setType(ScreenshotType.PNG)
                    .setFullPage(false));
            return Base64.getEncoder().encodeToString(screenshot);
        } catch (Exception e) {
            // 会话关闭导致的截图失败通常是由于前端轮询的延迟，不需要打印堆栈，降低日志级别
            if (e.getMessage().contains("closed") || e.getMessage().contains("TargetClosedError")) {
                log.debug("Session closed, stopping screenshot capture: {}", sessionId);
            } else {
                log.warn("截图警告 (session={}): {}", sessionId, e.getMessage());
            }
            return null;
        }
    }

    /**
     * 执行填单动作
     */
    public void fillField(String sessionId, String selector, String value) {
        Page page = sessions.get(sessionId);
        if (page != null) {
            try {
                page.fill(selector, value);
                log.info("fillField 成功: session={}, selector={}", sessionId, selector);
            } catch (Exception e) {
                log.error("fillField 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 执行点击动作
     */
    public void clickElement(String sessionId, String selector) {
        Page page = sessions.get(sessionId);
        if (page != null) {
            try {
                page.click(selector);
            } catch (Exception e) {
                log.error("clickElement 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 获取当前 URL
     */
    public String getCurrentUrl(String sessionId) {
        Page page = sessions.get(sessionId);
        if (page == null) return null;
        try {
            return page.url();
        } catch (Exception e) {
            log.warn("获取 URL 失败 (session={}): {}", sessionId, e.getMessage());
            return "unknown";
        }
    }

    /**
     * 在坐标处点击
     */
    public void clickAtPoint(String sessionId, int x, int y) {
        Page page = sessions.get(sessionId);
        if (page != null) {
            try {
                page.mouse().click(x, y);
            } catch (Exception e) {
                log.error("clickAtPoint 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 在会话中执行 JavaScript
     */
    public Object executeScript(String sessionId, String script) {
        Page page = sessions.get(sessionId);
        if (page != null) {
            try {
                return page.evaluate(script);
            } catch (Exception e) {
                log.error("executeScript 失败 (session={}): {}", sessionId, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 万能网页无头提取器：打开任意网址，等待渲染完成，提取肉眼可见的纯文本
     * 自动复用带有 Cookie 的持久化上下文。
     */
    public String getRenderedText(String url) {
        log.info("RAG 提取器开始抓取: {}", url);
        Page page = null;
        try {
            ensureBrowserReady();
            page = persistentContext.newPage();
            
            // 等待网络空闲，确保 React/Vue 渲染完毕
            page.navigate(url, new Page.NavigateOptions()
                .setTimeout(60000)
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE));
            
            // 获取可视纯文本
            Object result = page.evaluate("document.body.innerText");
            if (result == null) return "";
            
            String text = result.toString();
            // 简单清理多余的连续换行符
            text = text.replaceAll("(?m)^[ \\t]*\\r?\\n", "");
            log.info("RAG 提取器抓取成功，提取可视文本长度: {}", text.length());
            return text;
        } catch (Exception e) {
            log.error("RAG 提取器抓取失败 (URL={}): {}", url, e.getMessage());
            throw new RuntimeException("网页抓取失败: " + e.getMessage());
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 键盘逐字输入（模拟真实打字）
     */
    public void keyboardType(String sessionId, String text) {
        Page page = sessions.get(sessionId);
        if (page != null) {
            try {
                page.keyboard().type(text, new Keyboard.TypeOptions().setDelay(30));
                log.info("keyboardType 完成: session={}, 长度={}", sessionId, text.length());
            } catch (Exception e) {
                log.error("keyboardType 失败 (session={}): {}", sessionId, e.getMessage());
            }
        }
    }

    /**
     * 关闭会话（只关闭页面，不关闭浏览器上下文）
     */
    public void closeSession(String sessionId) {
        Page page = sessions.remove(sessionId);
        if (page != null) {
            try {
                page.close();
                log.info("会话 {} 的页面已关闭（浏览器保持运行）", sessionId);
            } catch (Exception ignored) {}
        }
    }

    /**
     * 静默清理资源
     */
    private void cleanupQuietly() {
        try {
            if (persistentContext != null) persistentContext.close();
        } catch (Exception ignored) {}
        try {
            if (playwright != null) playwright.close();
        } catch (Exception ignored) {}
        persistentContext = null;
        playwright = null;
    }

    @PreDestroy
    public void cleanup() {
        log.info("正在清理交互式浏览器资源...");
        sessions.forEach((id, page) -> {
            try { page.close(); } catch (Exception ignored) {}
        });
        sessions.clear();
        cleanupQuietly();
    }
}
