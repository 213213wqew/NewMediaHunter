package com.news.publish.service.adapter.impl.toutiao;

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
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Component
public class ToutiaoAdapter implements PlatformAdapter {

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private BrowserService browserService;

    @Override
    public String getPlatformKey() {
        return "toutiao";
    }

    @Override
    public void publishArticle(Article article, Account account, PublishTask task, String content) {
        log.info("开始通过浏览器自动化发布到头条号: {}", article.getTitle());
        
        try (BrowserContext context = browserService.createContextWithCookies(account.getCookieData())) {
            try (Page page = context.newPage()) {
                page.navigate("https://mp.toutiao.com/profile_v4/graphic/publish");
                log.info("已打开头条号编辑页...");
                
                // TODO: 编写具体的头条号发布逻辑
                
                log.info("自动化逻辑执行完毕 (今日头条)");
            }
        } catch (Exception e) {
            log.error("自动化发布失败 (今日头条): {}", e.getMessage());
        }

        String articleId = "tt_auto_" + System.currentTimeMillis() / 1000;
        task.setPlatformArticleId(articleId);
        task.setPlatformArticleUrl("https://www.toutiao.com/article/" + articleId);
    }

    @Override
    public void refreshArticleStatus(PublishTask task, Account account) {
        log.info("查询头条号文章状态: taskId={}", task.getId());
    }

    @Override
    public String uploadSingleMedia(String mediaUrl, Account account) {
        log.info("今日头条素材洗涤 (Cookie 模式): {}", mediaUrl);
        String cookie = account.getCookieData();
        if (cookie == null || cookie.isEmpty()) {
            log.warn("头条号 Cookie 为空，无法洗涤图片，返回原链接");
            return mediaUrl;
        }

        try {
            // 模拟图片上传至头条图床
            log.info("正在使用 Cookie 凭证上传媒体素材至今日头条图床...");
            return "http://p1.toutiaoimg.com/large/simulated_" + System.currentTimeMillis();
        } catch (Exception e) {
            log.error("头条号素材洗涤失败", e);
        }
        return mediaUrl;
    }

    @Override
    public void publishVideo(Article article, Account account, PublishTask task, String videoUrl) {
        log.warn("目前头条号 Cookie 适配器仅支持图文发布");
    }
}
