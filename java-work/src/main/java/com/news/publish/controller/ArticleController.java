package com.news.publish.controller;

import com.news.publish.model.entity.Article;
import com.news.publish.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/article")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/list")
    public List<Article> listArticles() {
        return articleService.getAllArticles();
    }

    @PostMapping("/save")
    public Article saveArticle(@RequestBody Article article) {
        return articleService.saveArticle(article);
    }

    @GetMapping("/{id}")
    public Article getArticle(@PathVariable Long id) {
        return articleService.getArticleById(id);
    }

    @DeleteMapping("/{id}")
    public void deleteArticle(@PathVariable Long id) {
        articleService.deleteArticle(id);
    }

    @DeleteMapping("/batch")
    public void deleteArticles(@RequestBody List<Long> ids) {
        articleService.deleteArticles(ids);
    }
}
