package com.news.publish.service.impl;

import com.news.publish.model.entity.Article;
import com.news.publish.service.ArticleFileStorage;
import com.news.publish.service.ArticleService;
import com.news.publish.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 使用本地文件存储文章，不依赖数据库。
 */
@Service
@Primary
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {
    private final ArticleFileStorage articleFileStorage;

    @Override
    public Article saveArticle(Article article) {
        if (article.getId() == null) {
            article.setUserId(UserContext.getUserId());
        } else {
            Article existing = articleFileStorage.findById(article.getId()).orElseThrow(() -> new RuntimeException("文章不存在"));
            if (!UserContext.isAdmin() && !existing.getUserId().equals(UserContext.getUserId())) {
                throw new RuntimeException("无权修改此文章");
            }
            article.setUserId(existing.getUserId());
        }
        return articleFileStorage.save(article);
    }

    @Override
    public Article getArticleById(Long id) {
        return articleFileStorage.findById(id).orElse(null);
    }

    @Override
    public List<Article> getAllArticles() {
        List<Article> articles;
        if (UserContext.isAdmin()) {
            articles = articleFileStorage.findAll();
        } else {
            articles = articleFileStorage.findByUserId(UserContext.getUserId());
        }
        // 按最后更新时间或 ID 倒序排列（最新的在最上面）
        articles.sort((a, b) -> {
            if (a.getCreateTime() != null && b.getCreateTime() != null) {
                return b.getCreateTime().compareTo(a.getCreateTime());
            }
            return Long.compare(b.getId() != null ? b.getId() : 0, a.getId() != null ? a.getId() : 0);
        });
        return articles;
    }

    @Override
    public void deleteArticle(Long id) {
        articleFileStorage.deleteById(id);
    }

    @Override
    public void deleteArticles(List<Long> ids) {
        if (ids != null) {
            ids.forEach(this::deleteArticle);
        }
    }
}
