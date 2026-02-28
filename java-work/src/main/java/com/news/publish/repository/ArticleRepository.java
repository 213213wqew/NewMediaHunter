package com.news.publish.repository;

import com.news.publish.model.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findByUserId(Long userId);
    List<Article> findByUserIdAndStatus(Long userId, Integer status);
}
