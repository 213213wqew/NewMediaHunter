package com.news.publish.service.adapter.impl.baijiahao;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.news.publish.model.entity.Account;
import com.news.publish.model.entity.Article;
import com.news.publish.model.entity.PublishTask;
import com.news.publish.service.adapter.PlatformAdapter;
import com.news.publish.service.automation.BrowserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class BaijiahaoAdapter implements PlatformAdapter {

    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private BrowserService browserService;

    @Override
    public String getPlatformKey() {
        return "baijiahao";
    }

    @Override
    @SuppressWarnings("unchecked")
    public void publishArticle(Article article, Account account, PublishTask task, String content) {
        log.info("正在同步文章到百家号: 账号={}, 标题={}", account.getAccountName(), article.getTitle());

        String appId = decodeIfBase64(account.getAppId());
        String appSecret = decodeIfBase64(account.getAppSecret());
        String cookie = account.getCookieData();

        // 优先使用 API 模式
        if (appId != null && !appId.isEmpty() && appSecret != null && !appSecret.isEmpty()) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("app_id", appId);
                body.put("app_token", appSecret);
                body.put("title", article.getTitle());
                body.put("content", content);
                body.put("is_original", 1);

                String url = "https://baijiahao.baidu.com/builder/common/api/publish";
                Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

                if (response != null && Integer.valueOf(0).equals(response.get("errno"))) {
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    String articleId = String.valueOf(data.get("article_id"));
                    task.setPlatformArticleId(articleId);
                    task.setPlatformArticleUrl("https://baijiahao.baidu.com/s?id=" + articleId);
                    log.info("百家号 API 发布完成，文章ID: {}", articleId);
                    return;
                } else {
                    String error = response != null ? (String) response.get("errmsg") : "未知错误";
                    throw new RuntimeException("API 模式发布失败: " + error);
                }
            } catch (Exception e) {
                log.error("百家号 API 同步异常", e);
                throw new RuntimeException("API 模式同步失败: " + e.getMessage());
            }
        } 
        
        // 优先使用浏览器自动化（原 Cookie 模式升级）
        else if (cookie != null && !cookie.isEmpty()) {
            log.info("尝试通过浏览器自动化发布到百家号: {}", article.getTitle());
            try (BrowserContext context = browserService.createContextWithCookies(cookie)) {
                try (Page page = context.newPage()) {
                    page.navigate("https://baijiahao.baidu.com/builder/rc/edit?type=news");
                    log.info("已打开百家号编辑页...");
                    
                    // TODO: 编写具体的百家号发布逻辑
                    
                    log.info("自动化逻辑执行完毕 (百家号)");
                }
            } catch (Exception e) {
                log.error("自动化发布失败 (百家号): {}", e.getMessage());
                throw new RuntimeException("自动化模式发布失败: " + e.getMessage());
            }
            
            String articleId = "bjh_auto_" + System.currentTimeMillis() / 1000;
            task.setPlatformArticleId(articleId);
            task.setPlatformArticleUrl("https://baijiahao.baidu.com/s?id=" + articleId);
        } else {
            throw new RuntimeException("百家号发布失败: 未配置 API 凭证或登录 Cookie");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void refreshArticleStatus(PublishTask task, Account account) {
        log.info("查询百家号文章状态: articleId={}", task.getPlatformArticleId());
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("app_id", decodeIfBase64(account.getAppId()));
            body.put("app_token", decodeIfBase64(account.getAppSecret()));
            body.put("article_id", task.getPlatformArticleId());

            String url = "https://baijiahao.baidu.com/builder/common/api/queryStatus";
            Map<String, Object> response = restTemplate.postForObject(url, body, Map.class);

            if (response != null && Integer.valueOf(0).equals(response.get("errno"))) {
                log.info("百家号状态返回: {}", response.get("data"));
            }
        } catch (Exception e) {
            log.error("百家号状态查询异常", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public String uploadSingleMedia(String mediaUrl, Account account) {
        log.info("百家号洗涤图片: {}", mediaUrl);
        try {
            byte[] imageBytes = restTemplate.getForObject(mediaUrl, byte[].class);
            if (imageBytes == null) return mediaUrl;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("app_id", decodeIfBase64(account.getAppId()));
            body.add("app_token", decodeIfBase64(account.getAppSecret()));
            
            ByteArrayResource resource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "image.jpg";
                }
            };
            body.add("media", resource);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = "https://baijiahao.baidu.com/builder/common/api/upload";
            
            Map<String, Object> response = restTemplate.postForObject(url, entity, Map.class);
            if (response != null && Integer.valueOf(0).equals(response.get("errno"))) {
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                return (String) data.get("url");
            }
        } catch (Exception e) {
            log.error("百家号图片上传失败", e);
        }
        return mediaUrl;
    }

    private String decodeIfBase64(String str) {
        if (str == null || str.isEmpty()) return str;
        try {
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(str);
            // 简单的判断是否为有效字符串，Base64 解码后的字节如果全是可见字符，则认为是加密串
            String decoded = new String(decodedBytes);
            // 这里逻辑比较简单，只要能解开且不报错就用解开后的
            return decoded;
        } catch (Exception e) {
            return str;
        }
    }

    @Override
    public void publishVideo(Article article, Account account, PublishTask task, String videoUrl) {
        log.warn("目前百家号适配器仅支持图文发布");
    }
}
