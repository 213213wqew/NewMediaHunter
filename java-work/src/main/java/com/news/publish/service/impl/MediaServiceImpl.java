package com.news.publish.service.impl;

import com.news.publish.model.entity.Account;
import com.news.publish.model.entity.Article;
import com.news.publish.model.entity.MediaResource;
import com.news.publish.repository.PlatformRepository;
import com.news.publish.service.MediaService;
import com.news.publish.service.MediaResourceFileStorage;
import com.news.publish.service.adapter.PlatformAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final MediaResourceFileStorage mediaRepository;
    private final PlatformRepository platformRepository;
    private final List<PlatformAdapter> adapters;

    @Override
    public String cleanContent(Article article, Account account) {
        String originalContent = article.getContent();
        if (originalContent == null || originalContent.isEmpty()) {
            return originalContent;
        }

        Document doc = Jsoup.parseBodyFragment(originalContent);
        Elements imgs = doc.select("img");

        if (imgs.isEmpty()) {
            return originalContent;
        }

        // 移除原先基于 PlatformAdapter 动态上传的逻辑，统一下载到本地素材中心
        // 这样不仅解决了防盗链，还让 Python 端接管上传逻辑，一举两得
        org.springframework.web.client.RestTemplate restTemplate = new org.springframework.web.client.RestTemplate();

        for (Element img : imgs) {
            String originalUrl = img.attr("src");
            if (originalUrl == null || originalUrl.isEmpty()) continue;

            // 如果已经是本地 /api/file/view/ 的素材，直接跳过
            if (originalUrl.startsWith("/api/file/view/")) {
                continue;
            }

            try {
                // 1. 检查是否已经洗过（缓存）
                MediaResource resource = mediaRepository.findByArticleIdAndOriginalUrlAndPlatformId(
                        article.getId(), originalUrl, account.getPlatformId() // 暂时保持使用 platformId 区分
                ).orElseGet(() -> {
                    // 2. 如果没洗过，将其下载到本地 uploads 目录并替换为 /api/file/view/ 链接
                    log.info("正在为文章 {} 账户 {} 下载远程图片至本地: {}", article.getId(), account.getAccountName(), originalUrl);
                    
                    String localUrl = originalUrl;
                    try {
                        String fileName = java.util.UUID.randomUUID().toString() + ".jpg";
                        java.nio.file.Path targetPath = java.nio.file.Paths.get("uploads", fileName);
                        
                        // 防盗链请求头
                        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
                        headers.set(org.springframework.http.HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                        
                        // 动态防盗链策略：头条系图片必须带头条 Referer，否则 403；百度等其他图库往往需要置空
                        if (originalUrl.contains("toutiaoimg.com") || originalUrl.contains("pstatp.com")) {
                            headers.set(org.springframework.http.HttpHeaders.REFERER, "https://www.toutiao.com/");
                        }
                        
                        org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
                        
                        org.springframework.http.ResponseEntity<byte[]> res = restTemplate.exchange(
                                originalUrl, 
                                org.springframework.http.HttpMethod.GET, 
                                entity, 
                                byte[].class
                        );
                        
                        byte[] imageBytes = res.getBody();
                        if (imageBytes != null) {
                            java.nio.file.Files.createDirectories(targetPath.getParent());
                            java.nio.file.Files.write(targetPath, imageBytes);
                            localUrl = "/api/file/view/" + fileName;
                            log.info("图片已成功转存至本地: {}", localUrl);
                        } else {
                            throw new RuntimeException("返回的图片内容为空");
                        }
                    } catch (Exception e) {
                        log.error("下载远程图片失败: {}", originalUrl, e);
                        // 【核心修复】：图片下载失败必须抛出异常阻断保存，不再生成含有残缺外链记录的素材到数据库
                        throw new RuntimeException("下载远程图片失败，无法生成本地素材: " + originalUrl, e);
                    }
    
                    MediaResource newResource = new MediaResource();
                    newResource.setArticleId(article.getId());
                    newResource.setOriginalUrl(originalUrl);
                    newResource.setPlatformId(account.getPlatformId());
                    newResource.setPlatformMediaUrl(localUrl);
                    newResource.setUploadStatus(1);
                    newResource.setUserId(article.getUserId());
                    return mediaRepository.save(newResource);
                });
    
                // 3. 检查缓存记录是否是有效的 /api/file/view/ 本地链接
                //    如果是旧版本遗料（如头条URL或其他公网地址），就重新下载更新记录
                String cachedUrl = resource.getPlatformMediaUrl();
                if (cachedUrl == null || !cachedUrl.startsWith("/api/file/view/")) {
                    log.info("检测到过期缓存记录，重新下载图片: {}", originalUrl);
                    String localUrl = originalUrl;
                    try {
                        String fileName = java.util.UUID.randomUUID().toString() + ".jpg";
                        java.nio.file.Path targetPath = java.nio.file.Paths.get("uploads", fileName);
                        
                        org.springframework.http.HttpHeaders headers2 = new org.springframework.http.HttpHeaders();
                        headers2.set(org.springframework.http.HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                        
                        if (originalUrl.contains("toutiaoimg.com") || originalUrl.contains("pstatp.com")) {
                            headers2.set(org.springframework.http.HttpHeaders.REFERER, "https://www.toutiao.com/");
                        }
                        
                        org.springframework.http.HttpEntity<String> entity2 = new org.springframework.http.HttpEntity<>(headers2);
                        
                        org.springframework.http.ResponseEntity<byte[]> res2 = restTemplate.exchange(
                                originalUrl, org.springframework.http.HttpMethod.GET, entity2, byte[].class);
                        byte[] imageBytes2 = res2.getBody();
                        if (imageBytes2 != null) {
                            java.nio.file.Files.createDirectories(targetPath.getParent());
                            java.nio.file.Files.write(targetPath, imageBytes2);
                            localUrl = "/api/file/view/" + fileName;
                            log.info("旧缓存已更新为本地地址: {}", localUrl);
                            resource.setPlatformMediaUrl(localUrl);
                            mediaRepository.save(resource);
                            cachedUrl = localUrl;
                        } else {
                            throw new RuntimeException("返回的图片内容为空");
                        }
                    } catch (Exception e) {
                        log.error("重新下载旧缓存图片失败: {}", originalUrl, e);
                        // 【核心修复】：更新下载失败必须抛出异常阻断保存
                        throw new RuntimeException("更新旧缓存图片失败，终端分发: " + originalUrl, e);
                    }
                }
    
                // 4. 替换 HTML 中的链接为本地链接库
                img.attr("src", cachedUrl);
            } catch (RuntimeException re) {
                log.warn("Java下载突破防盗链失败, 保留原始链接交由 Python 浏览器接手处理: {}", originalUrl);
                // 不执行替换逻辑，originalUrl保留在HTML文本内向下游流转
                continue;
            }
        }

        return doc.body().html();
    }
}
