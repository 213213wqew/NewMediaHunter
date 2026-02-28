package com.news.publish.service.adapter.impl.dayuhao;

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
public class DayuhaoAdapter implements PlatformAdapter {

    @Autowired
    private BrowserService browserService;

    @Override
    public String getPlatformKey() {
        return "dayuhao";
    }

    @Override
    public void publishArticle(Article article, Account account, PublishTask task, String content) {
        log.info("开始通过浏览器自动化发布到大鱼号: {}", article.getTitle());
        
        try (BrowserContext context = browserService.createContextWithCookies(account.getCookieData())) {
            try (Page page = context.newPage()) {
                page.navigate("https://mp.dayu.com/dashboard/article/write");
                log.info("已打开大鱼号编辑页...");
                
                // TODO: 编写具体的大鱼号发布逻辑
                
                log.info("自动化逻辑执行完毕 (大鱼号)");
            }
        } catch (Exception e) {
            log.error("自动化发布失败 (大鱼号): {}", e.getMessage());
        }

        task.setPlatformArticleId("dayu_auto_" + UUID.randomUUID().toString().substring(0, 8));
        task.setPlatformArticleUrl("https://uc.cn/a/" + task.getPlatformArticleId() + ".html");
    }

    @Override
    public void refreshArticleStatus(PublishTask task, Account account) {
        log.info("模拟查询大鱼号文章状态: taskId={}", task.getId());
    }

    @Override
    public String uploadSingleMedia(String mediaUrl, Account account) {
        log.info("模拟大鱼号媒体检测: url={}", mediaUrl);
        return mediaUrl;
    }

    @Override
    public void publishVideo(Article article, Account account, PublishTask task, String videoUrl) {
        log.info("模拟大鱼号视频发布: {}, url={}", article.getTitle(), videoUrl);
        task.setPlatformArticleId("dayu_v_" + UUID.randomUUID().toString().substring(0, 8));
        task.setPlatformArticleUrl("https://uc.cn/v/" + task.getPlatformArticleId());
    }
}
