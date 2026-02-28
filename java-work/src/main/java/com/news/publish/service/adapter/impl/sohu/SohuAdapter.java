package com.news.publish.service.adapter.impl.sohu;

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
public class SohuAdapter implements PlatformAdapter {

    @Autowired
    private BrowserService browserService;

    @Override
    public String getPlatformKey() {
        return "sohu";
    }

    @Override
    public void publishArticle(Article article, Account account, PublishTask task, String content) {
        log.info("开始通过浏览器自动化发布到搜狐号: {}", article.getTitle());
        
        try (BrowserContext context = browserService.createContextWithCookies(account.getCookieData())) {
            try (Page page = context.newPage()) {
                // 导航到搜狐号发布页
                page.navigate("https://mp.sohu.com/mpfe/v3/main/news/addarticle");
                log.info("已打开搜狐号编辑页...");
                
                // TODO: 编写具体的搜狐号发布逻辑
                
                log.info("自动化逻辑执行完毕 (搜狐)");
            }
        } catch (Exception e) {
            log.error("自动化发布失败 (搜狐): {}", e.getMessage());
        }

        task.setPlatformArticleId("sohu_auto_" + UUID.randomUUID().toString().substring(0, 8));
        task.setPlatformArticleUrl("https://sohu.com/a/" + task.getPlatformArticleId());
    }

    @Override
    public void refreshArticleStatus(PublishTask task, Account account) {
        log.info("模拟查询搜狐号文章状态: taskId={}", task.getId());
    }

    @Override
    public String uploadSingleMedia(String mediaUrl, Account account) {
        log.info("模拟搜狐号图床转换: url={}", mediaUrl);
        return mediaUrl;
    }

    @Override
    public void publishVideo(Article article, Account account, PublishTask task, String videoUrl) {
        log.info("模拟搜狐号视频发布: {}, url={}", article.getTitle(), videoUrl);
        task.setPlatformArticleId("sohu_v_" + UUID.randomUUID().toString().substring(0, 8));
        task.setPlatformArticleUrl("https://sohu.com/v/" + task.getPlatformArticleId());
    }
}
