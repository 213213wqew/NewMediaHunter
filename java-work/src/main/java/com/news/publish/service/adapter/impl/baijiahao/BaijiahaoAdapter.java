package com.news.publish.service.adapter.impl.baijiahao;

import com.news.publish.model.entity.Account;
import com.news.publish.model.entity.Article;
import com.news.publish.model.entity.PublishTask;
import com.news.publish.service.adapter.PlatformAdapter;
import com.news.publish.service.automation.BrowserService;
import com.news.publish.service.automation.PythonSkillRunner;
import com.news.publish.service.automation.SkillDiscoveryService;
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

    @Autowired
    private PythonSkillRunner pythonSkillRunner;

    @Autowired
    private SkillDiscoveryService skillDiscoveryService;

    @Autowired
    private com.news.publish.service.MediaResourceFileStorage mediaResourceFileStorage;

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
        
        // Cookie 模式：走百家号技能（图文发布）
        else if (cookie != null && !cookie.isEmpty()) {
            log.info("通过百家号技能发布图文: {}", article.getTitle());
            SkillDiscoveryService.SkillMetadata meta = skillDiscoveryService.getSkill("baijiahao_agent");
            if (meta == null) {
                throw new RuntimeException("未找到百家号技能 (baijiahao_agent)，请确认 skills/platforms/baijiahao 已配置");
            }
            Map<String, Object> params = new HashMap<>();
            params.put("platform", "baijiahao");
            params.put("command", "PUBLISH");
            params.put("account_id", account.getId());
            params.put("accountId", account.getId().toString());
            params.put("cookieJson", cookie);
            params.put("title", article.getTitle());
            params.put("htmlContent", content != null ? content : article.getContent());
            params.put("summary", article.getSummary());
            params.put("tags", article.getTags());
            // 将本地 uploads 的绝对路径传给 Python 脚本，避免路径猜测
            String localUploadsDir = java.nio.file.Paths.get("uploads").toAbsolutePath().toString();
            params.put("localUploadsDir", localUploadsDir);
            log.info("本地素材目录: {}", localUploadsDir);
            if (article.getPlatformSettings() != null && !article.getPlatformSettings().isBlank()) {
                try {
                    Map<String, Object> settings = new com.fasterxml.jackson.databind.ObjectMapper().readValue(article.getPlatformSettings(), Map.class);
                    params.put("platformSettings", settings);
                    
                    // 封面图路径转换逻辑
                    Map<String, Object> bjhSettings = (Map<String, Object>) settings.get("baijiahao");
                    if (bjhSettings == null) bjhSettings = (Map<String, Object>) settings.get("bjh");
                    
                    if (bjhSettings != null) {
                        String coverImage = (String) bjhSettings.get("coverImage");
                        if (coverImage != null && !coverImage.isEmpty()) {
                            if (coverImage.startsWith("/api/file/view/")) {
                                String fileName = coverImage.substring("/api/file/view/".length());
                                java.nio.file.Path path = java.nio.file.Paths.get("uploads", fileName).toAbsolutePath();
                                if (java.nio.file.Files.exists(path)) {
                                    params.put("localCoverPath", path.toString());
                                    log.info("已解析百家号本地封面路径: {}", path);
                                }
                            } else if (coverImage.startsWith("http://") || coverImage.startsWith("https://")) {
                                // 先查 MediaResource 表查询是否已存在本地副本
                                com.news.publish.model.entity.MediaResource cached = 
                                    mediaResourceFileStorage.findByOriginalUrl(coverImage).orElse(null);
                                if (cached != null && cached.getPlatformMediaUrl() != null
                                        && cached.getPlatformMediaUrl().startsWith("/api/file/view/")) {
                                    String localFileName = cached.getPlatformMediaUrl().substring("/api/file/view/".length());
                                    java.nio.file.Path localPath = java.nio.file.Paths.get("uploads", localFileName).toAbsolutePath();
                                    if (java.nio.file.Files.exists(localPath)) {
                                        params.put("localCoverPath", localPath.toString());
                                        log.info("封面图命中本地缓存: {}", localPath);
                                    } else {
                                        log.warn("本地缓存文件不存在，将重新下载: {}", localPath);
                                        cached = null; // 文件丢失，强制重下载
                                    }
                                }
                                if (cached == null || !params.containsKey("localCoverPath")) {
                                    try {
                                        java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("bjh_cover_", ".jpg");
                                        
                                        // 伪装浏览器请求头以绕过防盗链 (resolve 403 Forbidden)
                                        HttpHeaders headers = new HttpHeaders();
                                        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                                        headers.set(HttpHeaders.REFERER, "https://www.toutiao.com/");
                                        HttpEntity<String> entity = new HttpEntity<>(headers);
                                        
                                        org.springframework.http.ResponseEntity<byte[]> res = restTemplate.exchange(
                                                coverImage, 
                                                org.springframework.http.HttpMethod.GET, 
                                                entity, 
                                                byte[].class
                                        );
                                        
                                        byte[] imageBytes = res.getBody();
                                        if (imageBytes != null) {
                                            java.nio.file.Files.write(tempFile, imageBytes);
                                            params.put("localCoverPath", tempFile.toAbsolutePath().toString());
                                            log.info("已下载远程封面至临时文件: {}", tempFile.toAbsolutePath());
                                        }
                                    } catch (Exception e) {
                                        log.error("下载远程封面图失败: {}", coverImage, e);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            PythonSkillRunner.SkillExecutionResult result = pythonSkillRunner.execute(meta, params);
            if (result != null && !result.isSuccess() && result.getMessage() != null) {
                throw new RuntimeException("百家号技能执行失败: " + result.getMessage());
            }
            if (result != null && result.getData() != null) {
                if (result.getData().get("platformArticleId") != null) {
                    task.setPlatformArticleId(String.valueOf(result.getData().get("platformArticleId")));
                }
                if (result.getUrl() != null) task.setPlatformArticleUrl(result.getUrl());
                else if (result.getData().get("final_url") != null) task.setPlatformArticleUrl(String.valueOf(result.getData().get("final_url")));
            }
            if (task.getPlatformArticleId() == null) {
                task.setPlatformArticleId("bjh_auto_" + System.currentTimeMillis() / 1000);
                task.setPlatformArticleUrl("https://baijiahao.baidu.com/s?id=" + task.getPlatformArticleId());
            }
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
        log.info("通过百家号技能发布视频: {}", article.getTitle());
        SkillDiscoveryService.SkillMetadata meta = skillDiscoveryService.getSkill("baijiahao_agent");
        if (meta == null) {
            throw new RuntimeException("未找到百家号技能 (baijiahao_agent)");
        }
        Map<String, Object> params = new HashMap<>();
        params.put("platform", "baijiahao");
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
        if (result != null && !result.isSuccess()) {
            throw new RuntimeException(result.getMessage() != null ? result.getMessage() : "百家号视频发布失败");
        }
        if (result != null && result.getData() != null) {
            if (result.getData().get("platformArticleId") != null) {
                task.setPlatformArticleId(String.valueOf(result.getData().get("platformArticleId")));
            }
            if (result.getUrl() != null) task.setPlatformArticleUrl(result.getUrl());
        }
        if (task.getPlatformArticleId() == null) {
            task.setPlatformArticleId("bjh_video_" + System.currentTimeMillis() / 1000);
            task.setPlatformArticleUrl("https://baijiahao.baidu.com/s?id=" + task.getPlatformArticleId());
        }
    }
}
