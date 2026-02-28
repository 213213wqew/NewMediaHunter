package com.news.publish.service.adapter.impl.qiehao;

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
public class QiehaoAdapter implements PlatformAdapter {

    @Autowired
    private BrowserService browserService;

    @Override
    public String getPlatformKey() {
        return "qiehao";
    }

    @Override
    public void publishArticle(Article article, Account account, PublishTask task, String content) {
        log.info("开始通过浏览器自动化发布到企鹅号: {}", article.getTitle());
        
        try (BrowserContext context = browserService.createContextWithCookies(account.getCookieData())) {
            try (Page page = context.newPage()) {
                page.navigate("https://om.qq.com/article/index");
                log.info("已打开企鹅号编辑页...");
                
                // TODO: 编写具体的企鹅号发布逻辑
                
                log.info("自动化逻辑执行完毕 (企鹅号)");
            }
        } catch (Exception e) {
            log.error("自动化发布失败 (企鹅号): {}", e.getMessage());
        }

        task.setPlatformArticleId("qh_auto_" + UUID.randomUUID().toString().substring(0, 8));
        task.setPlatformArticleUrl("https://kuaibao.qq.com/s/" + task.getPlatformArticleId());
    }

    @Override
    public void refreshArticleStatus(PublishTask task, Account account) {
        log.info("模拟查询企鹅号文章状态: taskId={}", task.getId());
    }

    @Override
    public String uploadSingleMedia(String mediaUrl, Account account) {
        log.info("模拟企鹅号单图上传: url={}", mediaUrl);
        return "https://inews.gtimg.com/newsapp_bt/0/" + UUID.randomUUID().toString().substring(0, 10) + "/640";
    }

    @Override
    public void publishVideo(Article article, Account account, PublishTask task, String videoUrl) {
        log.info("模拟企鹅号视频发布: {}, url={}", article.getTitle(), videoUrl);
        task.setPlatformArticleId("qh_v_" + UUID.randomUUID().toString().substring(0, 8));
        task.setPlatformArticleUrl("https://v.qq.com/x/page/" + task.getPlatformArticleId() + ".html");
    }
}
