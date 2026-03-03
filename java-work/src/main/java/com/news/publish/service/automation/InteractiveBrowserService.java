package com.news.publish.service.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.ScreenshotType;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

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
     */
    public synchronized void ensureBrowserReady() {
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

    public BrowserContext getPersistentContext() {
        return persistentContext;
    }

    /**
     * 彻底关闭浏览器并释放资源
     */
    public synchronized void closeBrowser() {
        log.info("正在关闭浏览器并释放资源...");
        cleanupQuietly();
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
     * 从热点列表页面（如创作罐头管理后台）提取表格数据
     * 兼容普通 table 与 Element UI el-table，表头顺序：标题、平台、领域、阅读(播放)、评论量、点赞量、发布时间、操作
     * 标题列取首行文本（该列可能含多行：标题+作者/关键词）
     * @param url 热点列表页 URL，如 https://www.czgts.cn/v1/hots/popular
     * @param filters 可选，与页面一致的筛选：platform, contentType, domains(逗号分隔), publishTime, sort, keyword；为空则只打开页面不点选
     * @return JSON 数组字符串，每项含 title, platform, category, reads, comments, likes, publishTime；失败或无表格返回 "[]"
     */
    public String getHotspotTableFromPage(String url, Map<String, Object> filters) {
        log.info("热点页表格提取开始: {} filters={}", url, filters);
        Page page = null;
        try {
            ensureBrowserReady();
            page = persistentContext.newPage();
            // 优先尝试从网络响应中捕获列表 API 返回的 JSON（与 F12 Network 里看到的接口一致）
            AtomicReference<String> capturedJson = new AtomicReference<>();
            AtomicReference<Boolean> isSearchResponse = new AtomicReference<>(false);
            page.onResponse(response -> {
                try {
                    String respUrl = response.url();
                    int status = response.status();
                    if (status != 200 || respUrl.contains("data:image")) return;
                    
                    // 核心逻辑：如果是 search 接口或是 list 接口
                    boolean isSearch = respUrl.contains("search");
                    // 增加 hot_board 接口判定（全网热榜专用）
                    boolean isList = respUrl.contains("hots") || respUrl.contains("popular") || respUrl.contains("list") 
                            || respUrl.contains("article") || respUrl.contains("hot_board");
                    
                    if (!isSearch && !isList) return;
                    
                    byte[] body = response.body();
                    if (body == null || body.length < 100) return;
                    String json = new String(body, StandardCharsets.UTF_8);
                    
                    // 如果已经捕获到了 search 响应，就不再被普通的 list 响应覆盖
                    if (isSearch) {
                        capturedJson.set(json);
                        isSearchResponse.set(true);
                        log.info("已捕获到筛选后的 Search API 响应: {} 长度={}", respUrl, json.length());
                    } else if (!isSearchResponse.get()) {
                        capturedJson.set(json);
                        // 如果是 hot_board 响应，也标记为较高优先级（因为它包含全量数据）
                        if (respUrl.contains("hot_board")) {
                             isSearchResponse.set(true); 
                        }
                        log.info("已捕获到列表 API 响应: {} 长度={}", respUrl, json.length());
                    }
                } catch (Exception e) {}
            });
            try {
                page.navigate(url, new Page.NavigateOptions()
                        .setTimeout(35000)
                        .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.LOAD));
            } catch (Exception navEx) {
                log.warn("navigate 超时或异常（若已捕获列表 API 将继续使用）: {}", navEx.getMessage());
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            if (filters != null && !filters.isEmpty()) {
                capturedJson.set(null); // 在触发动作前清空，确保拿到的是点击/输入后的新数据
                
                // 全网热榜 (/network) 页面是仪表盘布局，没有这种筛选按钮，直接跳过点击
                boolean isNetworkPage = url.contains("/network");
                if (isNetworkPage) {
                    log.info("检测到全网热榜页面，跳过筛选器点击逻辑，直接等待数据加载...");
                } else {
                    applyFiltersOnPage(page, filters);
                }

                // 延长轮询时间到约 10s (25 * 400ms)，处理较慢的搜索请求
                for (int i = 0; i < 25; i++) { // 25 * 400ms = 10s
                    try { Thread.sleep(400); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    String fromApi = capturedJson.get();
                    if (fromApi != null && !fromApi.isBlank()) {
                        // 如果明确是 Search 响应，或者虽然不是但已经有数据了，就尝试返回
                        String extracted = extractListArrayFromApiJson(fromApi, filters);
                        if (extracted != null && !extracted.equals("[]")) {
                            if (isSearchResponse.get()) {
                                log.info("捕获到有效的 API 数据，分支匹配成功，耗时 {}ms", (i + 1) * 400);
                                return extracted;
                            }
                            // 如果 3 秒后还没等到明确的 search，但有数据了，也凑合用（防止某些平台接口命名不规范）
                            if (i > 8) {
                                log.info("轮询超时前使用已捕获数据");
                                return extracted;
                            }
                        }
                    }
                }
                log.warn("筛选后 10s 内未捕获到含数据的 API 响应，尝试最后一次 DOM 提取前检查或回退");
            } else {
                // 无筛选：直接用初始 API 数据
                String fromApi = capturedJson.get();
                if (fromApi != null && !fromApi.isBlank()) {
                    String extracted = extractListArrayFromApiJson(fromApi, null);
                    if (extracted != null) {
                        int count = extracted.equals("[]") ? 0 : extracted.split("\"title\"").length - 1;
                        log.info("使用初始 API 数据（含 url），条数约: {}", count);
                        return extracted;
                    }
                }
            }
            // 回退：从 DOM 表格提取
            try {
                page.waitForSelector("table", new Page.WaitForSelectorOptions().setTimeout(15000));
            } catch (Exception e) {
                log.warn("未在 15s 内找到 table，继续尝试 DOM 提取: {}", e.getMessage());
            }
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            // 最后的挣扎：在执行 DOM 脚本前，最后看一眼 API 是否终于到了
            String finalApiCheck = capturedJson.get();
            if (finalApiCheck != null && !finalApiCheck.isBlank()) {
                String extracted = extractListArrayFromApiJson(finalApiCheck, filters);
                if (extracted != null && !extracted.equals("[]")) {
                    log.info("DOM 提取前最后一刻捕获到有效的 API 数据");
                    return extracted;
                }
            }
            // 按表头匹配列：避免列顺序变化导致播放量/评论量/点赞量错位（时间能取对说明表头匹配更可靠）
            String script = "() => {\n" +
                    "  const table = document.querySelector('table') || document.querySelector('.arco-table') || document.querySelector('.el-table table') || document.querySelector('[class*=\"table\"]');\n" +
                    "  if (!table) return JSON.stringify([]);\n" +
                    "  const thead = table.querySelector('thead');\n" +
                    "  const tbody = table.querySelector('tbody');\n" +
                    "  const headerRow = thead ? thead.querySelector('tr') : table.querySelector('tr');\n" +
                    "  const headerCells = headerRow ? headerRow.querySelectorAll('th, td') : [];\n" +
                    "  const getText = (el) => (el && el.innerText) ? el.innerText.trim() : '';\n" +
                    "  let idxTitle = 0, idxPlatform = 1, idxCategory = 2, idxReads = -1, idxComments = -1, idxLikes = -1, idxTime = -1;\n" +
                    "  headerCells.forEach((cell, i) => {\n" +
                    "    const t = getText(cell);\n" +
                    "    if (/阅读|播放/.test(t)) idxReads = i;\n" +
                    "    else if (/评论/.test(t)) idxComments = i;\n" +
                    "    else if (/点赞/.test(t)) idxLikes = i;\n" +
                    "    else if (/发布|时间/.test(t)) idxTime = i;\n" +
                    "    else if (/平台/.test(t)) idxPlatform = i;\n" +
                    "    else if (/领域/.test(t)) idxCategory = i;\n" +
                    "  });\n" +
                    "  const dataRows = tbody ? tbody.querySelectorAll('tr') : [];\n" +
                    "  const arr = [];\n" +
                    "  function findRowUrl(tr, tds) {\n" +
                    "    const pick = (h) => {\n" +
                    "      if (!h || typeof h !== 'string') return '';\n" +
                    "      h = h.trim();\n" +
                    "      if (h.startsWith('http')) return h;\n" +
                    "      if (h.startsWith('//')) return location.protocol + h;\n" +
                    "      if (h.startsWith('/')) return location.origin + h;\n" +
                    "      if (h.length > 5 && (h.includes('/') || h.includes('.'))) {\n" +
                    "        try { return new URL(h, location.href).href; } catch(e) { return h; }\n" +
                    "      }\n" +
                    "      return '';\n" +
                    "    };\n" +
                    "    const allLinks = tr.querySelectorAll('a[href]');\n" +
                    "    for (let i = 0; i < allLinks.length; i++) {\n" +
                    "      const h = pick(allLinks[i].getAttribute('href') || allLinks[i].href);\n" +
                    "      if (h && /toutiao\\.com|baijiahao|baidu\\.com|article|weibo|xiaohongshu|mp\\.weixin|bilibili|kuaishou|news/.test(h)) return h;\n" +
                    "    }\n" +
                    "    if (allLinks.length > 0) {\n" +
                    "      for (let i = 0; i < allLinks.length; i++) {\n" +
                    "        const h = pick(allLinks[i].getAttribute('href') || allLinks[i].href);\n" +
                    "        if (h && h.startsWith('http') && !h.includes('javascript:')) return h;\n" +
                    "      }\n" +
                    "    }\n" +
                    "    const lastCell = tds && tds.length > 0 ? tds[tds.length - 1] : null;\n" +
                    "    if (lastCell) {\n" +
                    "      const opLink = lastCell.querySelector('a[href^=\"http\"]');\n" +
                    "      if (opLink && opLink.href) return opLink.href;\n" +
                    "    }\n" +
                    "    const withData = tr.querySelector('[data-url], [data-href], [data-link], [data-src], [data-id]');\n" +
                    "    if (withData) {\n" +
                    "      const u = withData.getAttribute('data-url') || withData.getAttribute('data-href') || withData.getAttribute('data-link') || withData.getAttribute('data-src') || withData.getAttribute('data-id');\n" +
                    "      if (pick(u)) return pick(u);\n" +
                    "    }\n" +
                    "    if (tr.dataset && (tr.dataset.url || tr.dataset.href || tr.dataset.link || tr.dataset.id)) return pick(tr.dataset.url || tr.dataset.href || tr.dataset.link || tr.dataset.id);\n" +
                    "    try {\n" +
                    "      const dataRecord = tr.getAttribute('data-record') || tr.getAttribute('data-row') || tr.getAttribute('data-v-inspect');\n" +
                    "      if (dataRecord) {\n" +
                    "        let o = null;\n" +
                    "        try { o = JSON.parse(dataRecord); } catch(e) {}\n" +
                    "        if (o && (o.url || o.link || o.articleUrl || o.id)) return pick(o.url || o.link || o.articleUrl || o.id);\n" +
                    "      }\n" +
                    "    } catch (e) {}\n" +
                    "    const v = tr.__vueParentComponent || tr.__vue__;\n" +
                    "    if (v) {\n" +
                    "      const r = (v.ctx && v.ctx.record) || (v.props && v.props.record) || (v.row) || (v.setupState && (v.setupState.record || v.setupState.row)) || (v.data && v.data.record);\n" +
                    "      if (r && typeof r === 'object') {\n" +
                    "        const u = r.url || r.link || r.articleUrl || r.articleLink || r.id;\n" +
                    "        if (u) return pick(u);\n" +
                    "      }\n" +
                    "      let p = v.parent;\n" +
                    "      for (let k = 0; k < 5 && p; k++) {\n" +
                    "        const pr = (p.ctx && p.ctx.record) || (p.props && p.props.record) || (p.setupState && (p.setupState.record || p.setupState.row));\n" +
                    "        if (pr && typeof pr === 'object') {\n" +
                    "          const u = pr.url || pr.link || pr.articleUrl || pr.id;\n" +
                    "          if (u) return pick(u);\n" +
                    "        }\n" +
                    "        p = p.parent;\n" +
                    "      }\n" +
                    "    }\n" +
                    "    const arcoWithHref = tr.querySelector('a.arco-link[href], .arco-link a[href], a[class*=\"link\"][href]');\n" +
                    "    if (arcoWithHref) { const h = pick(arcoWithHref.getAttribute('href') || arcoWithHref.href); if (h) return h; }\n" +
                    "    const opLinks = lastCell ? lastCell.querySelectorAll('.arco-link, [class*=\"operation\"] .arco-link, a') : [];\n" +
                    "    for (let j = 0; j < opLinks.length; j++) {\n" +
                    "      const el = opLinks[j];\n" +
                    "      const u = el.getAttribute('data-href') || el.getAttribute('data-url') || el.href || (el.querySelector && el.querySelector('a') && el.querySelector('a').href);\n" +
                    "      if (pick(u)) return pick(u);\n" +
                    "    }\n" +
                    "    return '';\n" +
                    "  }\n" +
                    "  dataRows.forEach(tr => {\n" +
                    "    const tds = tr.querySelectorAll('td');\n" +
                    "    if (tds.length < 3) return;\n" +
                    "    const cell0 = tds[0];\n" +
                    "    const rawTitle = getText(cell0);\n" +
                    "    const title = rawTitle ? rawTitle.split(/\\r?\\n/)[0].trim() : '';\n" +
                    "    const url = findRowUrl(tr, tds);\n" +
                    "    const cell = (i) => (i >= 0 && i < tds.length) ? getText(tds[i]) : '';\n" +
                    "    arr.push({\n" +
                    "      title: title,\n" +
                    "      url: url,\n" +
                    "      platform: cell(idxPlatform),\n" +
                    "      category: cell(idxCategory),\n" +
                    "      reads: idxReads >= 0 ? cell(idxReads) : '',\n" +
                    "      comments: idxComments >= 0 ? cell(idxComments) : '',\n" +
                    "      likes: idxLikes >= 0 ? cell(idxLikes) : '',\n" +
                    "      publishTime: idxTime >= 0 ? cell(idxTime) : ''\n" +
                    "    });\n" +
                    "  });\n" +
                    "  return JSON.stringify(arr);\n" +
                    "}";
            Object result = page.evaluate(script);
            if (result == null) return "[]";
            String json = result.toString();
            int domCount = json.equals("[]") ? 0 : json.split("\"title\"").length - 1;
            log.info("热点页表格提取成功，条数: {}", domCount);
            try {
                JsonNode arr = new ObjectMapper().readTree(json);
                if (arr.isArray() && arr.size() > 0) {
                    log.info("DOM 表格第一条数据（供核对平台/链接等）: {}", arr.get(0));
                }
            } catch (Exception e) {
                log.debug("打印 DOM 第一条失败: {}", e.getMessage());
            }
            return json;
        } catch (Exception e) {
            log.error("热点页表格提取失败 (URL={}): {}", url, e.getMessage());
            return "[]";
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception ignored) {}
            }
            // 热点抓取完成后自动关闭整个浏览器，避免留「正受到自动测试软件的控制」窗口
            log.info("热点抓取结束，关闭浏览器");
            cleanupQuietly();
        }
    }

    /**
     * 在创作罐头热点页上按用户选择点击筛选（与 skills/old_bjh_skill 一致：在页面内执行 JS，用 querySelectorAll + textContent 查找并 .click()）
     */
    private void applyFiltersOnPage(Page page, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) return;
        try {
            // 之前的复杂 Java/JS 点法可能破坏了 Arco 内部状态，现在回归最稳健的文字定位点法
            List<String> toClick = new ArrayList<>();
            if (filters.get("platform") != null) toClick.add(String.valueOf(filters.get("platform")));
            if (filters.get("contentType") != null) toClick.add(String.valueOf(filters.get("contentType")));
            if (filters.get("domains") != null) {
                Object d = filters.get("domains");
                if (d instanceof String s) toClick.addAll(Arrays.asList(s.split("[,，]")));
            }
            if (filters.get("publishTime") != null) {
                String pt = String.valueOf(filters.get("publishTime"));
                Map<String, String> m = Map.of("3h","3小时内","12h","12小时内","1d","1天内","2d","2天内","7d","7天内");
                toClick.add(m.getOrDefault(pt, pt));
            }
            if (filters.get("sort") != null) {
                String s = String.valueOf(filters.get("sort"));
                Map<String, String> m = Map.of("reads","阅读(播放)量排序","time","发布时间排序","comments","评论量排序","likes","点赞量排序");
                toClick.add(m.getOrDefault(s, s));
            }

            for (String text : toClick) {
                if (text == null || text.trim().isEmpty() || "全部".equals(text.trim())) continue;
                try {
                   // 原生 text 推导点击，通常比 JS 更稳
                   page.locator("text=\"" + text.trim() + "\"").first().click();
                   Thread.sleep(200);
                } catch (Exception e) {
                   log.warn("点击筛选项 [{}] 失败，尝试 JS 兜底: {}", text, e.getMessage());
                   page.evaluate("t => { var e = Array.from(document.querySelectorAll('span, button')).find(x => x.innerText.trim() === t); if(e) e.click(); }", text.trim());
                   Thread.sleep(200);
                }
            }
            
            if (filters.get("keyword") != null) {
                page.fill("input[placeholder*='关键词'], input[placeholder*='搜索']", String.valueOf(filters.get("keyword")));
            }

            // 触发搜索
            log.info("执行最终『发现热点/搜索』点击...");
            page.locator("text=发现热点").first().click();
            Thread.sleep(200);
        } catch (Exception e) {
            log.warn("应用页面筛选时失败: {}", e.getMessage());
        }
    }

    /** 从接口返回的 JSON 中抽出列表数组（支持 { data: { list: [] } }、{ records: [] }、导出全网热榜特定平台等） */
    private String extractListArrayFromApiJson(String raw, Map<String, Object> filters) {
        if (raw == null || raw.isBlank()) return null;
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode root = om.readTree(raw);
            
            // 专项处理全网热榜 (hot_board) 多平台数据
            // 结构 1: 根节点直接包含 toutiaoHotBoard 等
            // 结构 2: data 节点包含 toutiaoHotBoard 等
            JsonNode dataNode = root.has("data") ? root.get("data") : root;
            if (dataNode.has("mixBoards") || dataNode.has("toutiaoHotBoard") || dataNode.has("weiboHotBoard")) {
                log.info("识别到全网热榜 (hot_board) 结构");
                String platform = (filters != null) ? String.valueOf(filters.getOrDefault("platform", "今日头条")) : "今日头条";
                
                String targetKey = "mixBoards"; // 默认为全网
                if (platform.contains("今日头条")) targetKey = "toutiaoHotBoard";
                else if (platform.contains("微博")) targetKey = "weiboHotBoard";
                else if (platform.contains("百度")) targetKey = "baiduHotBoard";
                else if (platform.contains("抖音")) targetKey = "douyinHotBoard";
                else if (platform.contains("知乎")) targetKey = "zhihuHotBoard";
                else if (platform.contains("小红书")) targetKey = "xiaohongshuHotBoard";

                if (dataNode.has(targetKey) && dataNode.get(targetKey).isArray()) {
                    log.info("成功提取全网热榜平台数据: {}", targetKey);
                    return om.writeValueAsString(dataNode.get(targetKey));
                }
                
                // 兜底：如果找不到平台专用榜单，尝试从 mixBoards (全网混合榜) 中找
                if (dataNode.has("mixBoards") && dataNode.get("mixBoards").isArray()) {
                    log.info("未找到专用榜单，回退至 mixBoards");
                    return om.writeValueAsString(dataNode.get("mixBoards"));
                }
            }

            if (root.isArray()) return raw;
            // 候选键名列表
            String[] arrayKeys = {"list", "records", "items", "rows", "data", "content", "results", "articles", "dataList", "news", "hots"};
            for (String key : arrayKeys) {
                if (root.has(key) && root.get(key).isArray()) {
                    return om.writeValueAsString(root.get(key));
                }
            }
            if (root.has("data")) {
                JsonNode data = root.get("data");
                if (data.isArray()) return om.writeValueAsString(data);
                for (String key : arrayKeys) {
                    if (data.has(key) && data.get(key).isArray()) {
                        return om.writeValueAsString(data.get(key));
                    }
                }
            }
            // 兜底：寻找第一个长度大于 0 的数组
            java.util.Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                JsonNode node = fields.next().getValue();
                if (node.isArray() && node.size() > 0) return om.writeValueAsString(node);
            }
        } catch (Exception e) {
            log.debug("从 API JSON 提取列表失败: {}", e.getMessage());
        }
        return null;
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


    /**
     * 点击页面上的“下载数据”按钮，并保存下载的文件
     * @return 返回保存的文件绝对路径，失败返回 null
     */
    public String downloadHotspotFile(String url, Map<String, Object> filters) {
        log.info("开始下载热点数据文件: {}", url);
        Page page = null;
        try {
            ensureBrowserReady();
            page = persistentContext.newPage();
            // 设置下载超时
            page.setDefaultTimeout(60000);
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE));
            
            if (filters != null && !filters.isEmpty()) {
                applyFiltersOnPage(page, filters);
            }

            // 监听下载事件
            final Page capturePage = page;
            Download download = page.waitForDownload(() -> {
                log.info("触发下载按钮点击...");
                // Arco Design 的下载按钮通常有特定文本
                capturePage.locator("text=下载数据").first().click();
            });

            String fileName = download.suggestedFilename();
            // 确保目录存在
            java.io.File dir = new java.io.File(USER_DATA_DIR, "downloads");
            if (!dir.exists()) dir.mkdirs();
            
            Path downloadPath = Paths.get(USER_DATA_DIR, "downloads", fileName);
            download.saveAs(downloadPath);
            log.info("文件下载完成: {}", downloadPath);
            return downloadPath.toString();
        } catch (Exception e) {
            log.error("下载文件失败: {}", e.getMessage());
            return null;
        } finally {
            if (page != null) page.close();
        }
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
