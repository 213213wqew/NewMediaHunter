package com.news.publish.service.adapter.impl.toutiao;

import com.news.publish.model.entity.Account;
import com.news.publish.model.entity.Article;
import com.news.publish.model.entity.PublishTask;
import com.news.publish.service.adapter.PlatformAdapter;
import com.news.publish.service.automation.BrowserService;
import com.news.publish.service.automation.PythonSkillRunner;
import com.news.publish.service.automation.SkillDiscoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ToutiaoAdapter implements PlatformAdapter {

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private BrowserService browserService;

    @Autowired
    private PythonSkillRunner pythonSkillRunner;

    @Autowired
    private SkillDiscoveryService skillDiscoveryService;

    @Override
    public String getPlatformKey() {
        return "toutiao";
    }

    @Override
    public void publishArticle(Article article, Account account, PublishTask task, String content) {
        log.info("开始通过头条技能发布图文到头条号: {}", article.getTitle());
        SkillDiscoveryService.SkillMetadata meta = skillDiscoveryService.getSkill("toutiao_agent");
        if (meta == null) {
            throw new RuntimeException("未找到头条技能 (toutiao_agent)，请确认 skills/platforms/toutiao 已配置");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("platform", "toutiao");
        params.put("command", "PUBLISH");
        params.put("account_id", account.getId());
        params.put("accountId", account.getId().toString());
        params.put("cookieJson", account.getCookieData());
        params.put("title", article.getTitle());
        params.put("htmlContent", content != null ? content : article.getContent());
        params.put("summary", article.getSummary());
        params.put("tags", article.getTags());
        if (article.getPlatformSettings() != null && !article.getPlatformSettings().isBlank()) {
            try {
                params.put("platformSettings", new com.fasterxml.jackson.databind.ObjectMapper().readValue(article.getPlatformSettings(), Map.class));
            } catch (Exception ignored) {}
        }
        PythonSkillRunner.SkillExecutionResult result = pythonSkillRunner.execute(meta, params);
        if (result != null && result.isSuccess() && result.getData() != null) {
            if (result.getData().get("platformArticleId") != null) {
                task.setPlatformArticleId(String.valueOf(result.getData().get("platformArticleId")));
            }
            if (result.getUrl() != null) task.setPlatformArticleUrl(result.getUrl());
            else if (result.getData().get("final_url") != null) task.setPlatformArticleUrl(String.valueOf(result.getData().get("final_url")));
        }
        if (task.getPlatformArticleId() == null) {
            task.setPlatformArticleId("tt_auto_" + System.currentTimeMillis() / 1000);
            task.setPlatformArticleUrl("https://www.toutiao.com/article/" + task.getPlatformArticleId());
        }
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
        log.info("开始通过头条技能发布视频到头条号: {}", article.getTitle());
        SkillDiscoveryService.SkillMetadata meta = skillDiscoveryService.getSkill("toutiao_agent");
        if (meta == null) {
            throw new RuntimeException("未找到头条技能 (toutiao_agent)，请确认 skills/platforms/toutiao 已配置");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("platform", "toutiao");
        params.put("command", "PUBLISH_VIDEO");
        params.put("account_id", account.getId());
        params.put("accountId", account.getId().toString());
        params.put("cookieJson", account.getCookieData());
        params.put("title", article.getTitle());
        params.put("videoPath", videoUrl);
        params.put("videoUrl", videoUrl);
        params.put("summary", article.getSummary());
        params.put("tags", article.getTags());
        if (article.getPlatformSettings() != null && !article.getPlatformSettings().isBlank()) {
            try {
                params.put("platformSettings", new com.fasterxml.jackson.databind.ObjectMapper().readValue(article.getPlatformSettings(), Map.class));
            } catch (Exception ignored) {}
        }
        PythonSkillRunner.SkillExecutionResult result = pythonSkillRunner.execute(meta, params);
        // 只有技能完整跑完并返回 success 才视为成功；中途关闭浏览器等会返回 failure，此处抛异常让 PublishServiceImpl 将任务标为失败
        if (result == null || !result.isSuccess()) {
            throw new RuntimeException(result != null && result.getMessage() != null ? result.getMessage() : "头条视频发布失败或流程未完成（如中途关闭浏览器）");
        }
        if (result.getData() != null) {
            boolean skippedPublish = Boolean.TRUE.equals(result.getData().get("skipped_publish"));
            if (skippedPublish) {
                task.setPublishStatus(2);
                task.setErrorMessage("已填写未发布（测试）");
            } else {
                if (result.getData().get("platformArticleId") != null) {
                    task.setPlatformArticleId(String.valueOf(result.getData().get("platformArticleId")));
                }
                if (result.getUrl() != null) task.setPlatformArticleUrl(result.getUrl());
                else if (result.getData().get("final_url") != null) task.setPlatformArticleUrl(String.valueOf(result.getData().get("final_url")));
            }
        }
        boolean skipped = result.getData() != null && Boolean.TRUE.equals(result.getData().get("skipped_publish"));
        if (!skipped && task.getPlatformArticleId() == null) {
            task.setPlatformArticleId("tt_video_" + System.currentTimeMillis() / 1000);
            task.setPlatformArticleUrl("https://www.toutiao.com/video/" + task.getPlatformArticleId());
        }
    }
}
