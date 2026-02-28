package com.news.publish.service.impl;

import com.news.publish.model.entity.Article;
import com.news.publish.repository.ArticleRepository;
import com.news.publish.service.ArticleService;
import com.news.publish.interceptor.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl implements ArticleService {
    private final ArticleRepository articleRepository;

    @Override
    public Article saveArticle(Article article) {
        if (article.getId() == null) {
            article.setUserId(UserContext.getUserId());
        } else {
            Article existing = articleRepository.findById(article.getId()).orElseThrow();
            if (!UserContext.isAdmin() && !existing.getUserId().equals(UserContext.getUserId())) {
                throw new RuntimeException("无权修改此文章");
            }
            article.setUserId(existing.getUserId());
        }
        return articleRepository.save(article);
    }

    @Override
    public Article getArticleById(Long id) {
        return articleRepository.findById(id).orElse(null);
    }

    @Override
    public List<Article> getAllArticles() {
        if (UserContext.isAdmin()) {
            return articleRepository.findAll();
        }
        return articleRepository.findByUserId(UserContext.getUserId());
    }

    @Override
    public void deleteArticle(Long id) {
        articleRepository.deleteById(id);
    }
}
