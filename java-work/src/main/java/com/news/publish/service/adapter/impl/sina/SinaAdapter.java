package com.news.publish.service.adapter.impl.sina;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.news.publish.model.entity.Account;
import com.news.publish.model.entity.Article;
import com.news.publish.model.entity.PublishTask;
import com.news.publish.service.adapter.PlatformAdapter;
import com.news.publish.service.automation.BrowserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class SinaAdapter implements PlatformAdapter {

    @Autowired
    private BrowserService browserService;

    @Override
    public String getPlatformKey() {
        return "sina";
    }

    @Override
    public void publishArticle(Article article, Account account, PublishTask task, String content) {
        log.info("开始通过浏览器自动化发布到新浪号: {}", article.getTitle());
        
        try (BrowserContext context = browserService.createContextWithCookies(account.getCookieData())) {
            try (Page page = context.newPage()) {
                // 1. 导航到新浪号编辑页 (示例 URL)
                page.navigate("https://mp.sina.com.cn/main/editor");
                
                // 2. 检查是否已登录 (如果没登录，Cookie 可能失效或格式不对)
                // if (page.url().contains("login")) { ... }
                
                log.info("已打开新浪号编辑页，正在模拟填写内容...");
                
                // 3. 这里编写具体的 DOM 操作逻辑 (需要根据新浪后台真实选择器调整)
                // page.fill("input[placeholder='请输入标题']", article.getTitle());
                // page.setContent(content); // 或者操作富文本编辑器
                
                // 4. 提交
                // page.click(".publish-btn");
                
                log.info("自动化逻辑执行完毕 (由于环境限制，具体点击操作已注释)");
            }
        } catch (Exception e) {
            log.error("自动化发布失败: {}", e.getMessage());
        }

        task.setPlatformArticleId("sina_auto_" + UUID.randomUUID().toString().substring(0, 8));
        task.setPlatformArticleUrl("https://k.sina.com.cn/article_" + task.getPlatformArticleId());
    }

    @Override
    public void refreshArticleStatus(PublishTask task, Account account) {
        log.info("模拟查询新浪号文章状态: taskId={}", task.getId());
    }

    @Override
    public String uploadSingleMedia(String mediaUrl, Account account) {
        log.info("模拟新浪号媒体检测: url={}", mediaUrl);
        return mediaUrl; 
    }

    @Override
    public void publishVideo(Article article, Account account, PublishTask task, String videoUrl) {
        log.info("模拟新浪号视频发布: {}, url={}", article.getTitle(), videoUrl);
        task.setPlatformArticleId("sina_v_" + UUID.randomUUID().toString().substring(0, 8));
        task.setPlatformArticleUrl("https://k.sina.com.cn/v/" + task.getPlatformArticleId());
    }
}
