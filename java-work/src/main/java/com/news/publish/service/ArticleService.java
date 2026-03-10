package com.news.publish.service;

import com.news.publish.model.entity.Article;
import java.util.List;

public interface ArticleService {
    Article saveArticle(Article article);
    Article getArticleById(Long id);
    List<Article> getAllArticles();
    void deleteArticle(Long id);
    void deleteArticles(List<Long> ids);
}
