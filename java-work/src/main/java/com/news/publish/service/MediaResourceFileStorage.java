package com.news.publish.service;

import com.news.publish.model.entity.MediaResource;
import java.util.List;
import java.util.Optional;

public interface MediaResourceFileStorage {
    List<MediaResource> findAll();
    List<MediaResource> findByUserId(Long userId);
    List<MediaResource> findByUserIdAndFileType(Long userId, String fileType);
    Optional<MediaResource> findByArticleIdAndOriginalUrlAndPlatformId(Long articleId, String originalUrl, Long platformId);
    Optional<MediaResource> findByOriginalUrl(String originalUrl);
    List<MediaResource> findByFileType(String fileType);
    MediaResource save(MediaResource resource);
    void deleteById(Long id);
    void deleteByIds(java.util.List<Long> ids);
    void deleteAllByUserId(Long userId);
}
