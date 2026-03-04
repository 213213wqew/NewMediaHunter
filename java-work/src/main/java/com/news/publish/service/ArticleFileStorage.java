package com.news.publish.service;

import com.news.publish.model.entity.Article;

import java.util.List;
import java.util.Optional;

/**
 * 文章本地文件存储：不依赖数据库，读写本地 JSON 文件。
 */
public interface ArticleFileStorage {
    List<Article> findAll();

    List<Article> findByUserId(Long userId);

    Optional<Article> findById(Long id);

    Article save(Article article);

    void deleteById(Long id);
}
