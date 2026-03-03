package com.news.publish.service.automation;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * “一键创作”内容提取专项服务
 * 负责从不同平台（头条、百家号等）页面中精准提取正文
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleExtractorService {

    private final InteractiveBrowserService browserService;

    /**
     * 提取文章/微头条正文
     * @param url 目标地址
     * @param platform 平台标识（今日头条、百家号）
     * @param type 内容类型（短图文、文章）
     * @return 纯净正文
     */
    public String extractContent(String url, String platform, String type) {
        if ("视频".equals(type)) {
            log.info("检测到视频类型，跳过一键创作内容提取: {}", url);
            return "";
        }

        log.info("开始一键创作内容提取: url={}, platform={}, type={}", url, platform, type);
        Page page = null;
        try {
            browserService.ensureBrowserReady();
            page = browserService.getPersistentContext().newPage();
            
            // 1. 导航与等待
            page.navigate(url, new Page.NavigateOptions()
                .setTimeout(60000)
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE));
            
            // --- 增强：模拟滚动，触发图片懒加载 ---
            page.evaluate("async () => {" +
                "  for (let i = 0; i < 5; i++) {" +
                "    window.scrollBy(0, window.innerHeight);" +
                "    await new Promise(resolve => setTimeout(resolve, 500));" +
                "  }" +
                "}");

            // 2. 根据平台和类型选择最优脚本
            String script = buildExtractionScript(platform, type);
            Object result = page.evaluate(script);

            if (result == null) return "";
            
            String text = result.toString();
            // 3. 规范化格式
            text = text.replaceAll("(?m)^[ \\t]*\\r?\\n", "\n"); // 压缩空行
            return text.trim();
        } catch (Exception e) {
            log.error("提取内容失败 (url={}): {}", url, e.getMessage());
            return "";
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception ignored) {}
            }
            // 提取任务完成后自动关闭整个浏览器，防止窗口残留
            browserService.closeBrowser();
        }
    }

    /**
     * 构建针对性 JS 提取脚本
     */
    private String buildExtractionScript(String platform, String type) {
        StringBuilder sb = new StringBuilder("() => {");
        sb.append("  const selectors = [];");
        
        // 针对今日头条的逻辑
        if (platform != null && platform.contains("今日头条")) {
            if ("短图文".equals(type) || "微头条".equals(type)) {
                sb.append("  selectors.push('.weitoutiao-html', '.wtt-content', '.post-content');");
            } else {
                sb.append("  selectors.push('.article-content', 'article', '.content-body');");
            }
        } 
        // 针对百家号/百度
        else if (platform != null && (platform.contains("百家号") || platform.contains("百度"))) {
            sb.append("  selectors.push('.index-module_contentContainer_3wQgg', '.article-content', '#article-content', '.bjh-p', '.bjh-image-container');");
        }
        
        // 通用兜底选择器
        sb.append("  selectors.push('article', '.post-content', 'main', '.entry-content');");
        
        sb.append("  let target = null;");
        sb.append("  for(const s of selectors) {");
        sb.append("    let el = document.querySelector(s);");
        sb.append("    if(el && (el.innerText || el.textContent).length > 20) { target = el; break; }");
        sb.append("  }");
        
        sb.append("  if(!target) target = document.body;");
        
        // 深度清理噪音逻辑
        sb.append("  const clone = target.cloneNode(true);");
        sb.append("  const noise = clone.querySelectorAll('script, style, iframe, .side-bar, .comment-box, .recommend-box, button, nav, .footer, .index-module_forwardContainer_1Rg2A');");
        sb.append("  noise.forEach(n => n.remove());");
        
        // --- 核心增强：提取图片并转化为 AI 极其显著的占位符 ---
        sb.append("  const imgs = clone.querySelectorAll('img');");
        sb.append("  imgs.forEach((img, idx) => {");
        sb.append("    let src = img.getAttribute('original') || img.getAttribute('data-src') || img.getAttribute('data-lazy-src') || img.getAttribute('data-actualsrc') || img.src;");
        sb.append("    if (src) {");
        // 关键逻辑：补全协议，处理 // 或相对路径
        sb.append("      try { src = new URL(src, document.baseURI).href; } catch(e) {}");
        sb.append("      if (src.startsWith('http') && !src.includes('avatar') && !src.includes('icon') && !src.includes('logo')) {");
        sb.append("        const p = document.createElement('div');");
        sb.append("        p.textContent = `\\n\\n【图片素材${idx+1} 开始】${src}【图片素材${idx+1} 结束】\\n\\n`;");
        sb.append("        if(img.parentNode) img.parentNode.replaceChild(p, img);");
        sb.append("        return;");
        sb.append("      }");
        sb.append("    }");
        sb.append("    img.remove();");
        sb.append("  });");
        
        // 百度/今日头条等平台图片可能在 noscript 中
        sb.append("  clone.querySelectorAll('noscript').forEach(ns => {");
        sb.append("    const match = ns.innerHTML.match(/src=\"(https?:\\/\\/[^\"]+)\"/);");
        sb.append("    if(match && match[1]) {");
        sb.append("      const p = document.createElement('div');");
        sb.append("      p.textContent = `\\n\\n【图片素材_补 开始】${match[1]}【图片素材_补 结束】\\n\\n`;");
        sb.append("      if(ns.parentNode) ns.parentNode.replaceChild(p, ns);");
        sb.append("    } else { ns.remove(); }");
        sb.append("  });");

        // 采用 innerText 获取可见文本（先挂载再读取以确保可见性逻辑生效）
        sb.append("  document.body.appendChild(clone);");
        sb.append("  const text = clone.innerText || clone.textContent || '';");
        sb.append("  clone.remove();");
        sb.append("  return text;");
        sb.append("}");
        
        return sb.toString();
    }
}
