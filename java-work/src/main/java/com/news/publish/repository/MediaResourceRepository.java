package com.news.publish.repository;

import com.news.publish.model.entity.MediaResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaResourceRepository extends JpaRepository<MediaResource, Long> {
    List<MediaResource> findByUserIdOrderByCreateTimeDesc(Long userId);
    List<MediaResource> findByUserIdAndFileTypeOrderByCreateTimeDesc(Long userId, String fileType);
    Optional<MediaResource> findByArticleIdAndOriginalUrlAndPlatformId(Long articleId, String originalUrl, Long platformId);
    List<MediaResource> findByFileTypeOrderByCreateTimeDesc(String fileType);
    List<MediaResource> findAllByOrderByCreateTimeDesc();
}
