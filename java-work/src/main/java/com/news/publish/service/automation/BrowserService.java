package com.news.publish.service.automation;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Paths;
import java.util.Collections;

@Slf4j
@Service
public class BrowserService {

    private Playwright playwright;
    private Browser browser;

    @Value("${browser.headless:false}")
    private boolean headless;

    // 注意：不再自动初始化浏览器实例
    // 浏览器资源统一由 InteractiveBrowserService 管理
    // 此 Service 保留仅为兼容旧 Adapter 的注入
    private boolean initialized = false;

    private synchronized void ensureInitialized() {
        if (!initialized) {
            log.info("BrowserService 按需初始化 (headless={})...", headless);
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(headless)
                    .setArgs(Collections.singletonList("--no-sandbox")));
            initialized = true;
        }
    }

    public BrowserContext createContextWithCookies(String cookieJson) {
        ensureInitialized();
        BrowserContext context = browser.newContext();
        if (cookieJson != null && !cookieJson.isEmpty()) {
            try {
                // 使用 Jackson 解析 Cookie 数组 (假设 Account 存的是标准 JSON 格式)
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<Cookie> cookies = mapper.readValue(cookieJson, 
                    mapper.getTypeFactory().constructCollectionType(java.util.List.class, Cookie.class));
                
                context.addCookies(cookies);
                log.info("成功注入 {} 个 Cookie", cookies.size());
            } catch (Exception e) {
                log.error("解析 Cookie JSON 失败: {}", e.getMessage());
            }
        }
        return context;
    }

    /**
     * 在指定页面执行简单的点击并输入操作 (演示用)
     */
    public void executeSimpleAction(String url, String selector, String text) {
        ensureInitialized();
        try (Page page = browser.newPage()) {
            page.navigate(url);
            page.fill(selector, text);
            page.click("button[type='submit']");
            log.info("成功在 {} 上执行操作", url);
        } catch (Exception e) {
            log.error("浏览器操作失败: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (!initialized) return;
        log.info("正在关闭 BrowserService Playwright 资源...");
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}
