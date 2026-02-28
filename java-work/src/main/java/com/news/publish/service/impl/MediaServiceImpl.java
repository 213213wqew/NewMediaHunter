package com.news.publish.service.impl;

import com.news.publish.model.entity.Account;
import com.news.publish.model.entity.Article;
import com.news.publish.model.entity.MediaResource;
import com.news.publish.repository.MediaResourceRepository;
import com.news.publish.repository.PlatformRepository;
import com.news.publish.service.MediaService;
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

    private final MediaResourceRepository mediaRepository;
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

        // 动态匹配平台适配器
        String platformKey = platformRepository.findById(account.getPlatformId())
                .map(p -> p.getPlatformKey())
                .orElse(null);

        if (platformKey == null) {
            log.warn("未知平台 ID: {}, 跳过媒体洗涤", account.getPlatformId());
            return originalContent;
        }

        // 找到对应的适配器
        PlatformAdapter adapter = adapters.stream()
                .filter(a -> a.getPlatformKey().equalsIgnoreCase(platformKey))
                .findFirst()
                .orElse(null);

        if (adapter == null) {
            log.warn("未找到平台 [{}] 的适配器，跳过媒体洗涤", platformKey);
            return originalContent;
        }

        for (Element img : imgs) {
            String originalUrl = img.attr("src");
            if (originalUrl == null || originalUrl.isEmpty()) continue;

            // 1. 检查是否已经洗过（缓存）
            MediaResource resource = mediaRepository.findByArticleIdAndOriginalUrlAndPlatformId(
                    article.getId(), originalUrl, account.getPlatformId()
            ).orElseGet(() -> {
                // 2. 如果没洗过，调用适配器模拟上传
                log.info("正在为平台 {} 账户 {} 洗涤图片: {}", platformKey, account.getAccountName(), originalUrl);
                String platformUrl = adapter.uploadSingleMedia(originalUrl, account);

                MediaResource newResource = new MediaResource();
                newResource.setArticleId(article.getId());
                newResource.setOriginalUrl(originalUrl);
                newResource.setPlatformId(account.getPlatformId());
                newResource.setPlatformMediaUrl(platformUrl);
                newResource.setUploadStatus(1);
                newResource.setUserId(article.getUserId());
                return mediaRepository.save(newResource);
            });

            // 3. 替换 HTML 中的链接
            img.attr("src", resource.getPlatformMediaUrl());
        }

        return doc.body().html();
    }
}
