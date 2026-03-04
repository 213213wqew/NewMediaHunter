package com.news.publish.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.news.publish.model.dto.ArticleRecord;
import com.news.publish.model.entity.Article;
import com.news.publish.service.ArticleFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ArticleFileStorageImpl implements ArticleFileStorage {

    private static final String DIR = ".news-publisher";
    private static final String FILE = "articles.json";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Path filePath;
    private final AtomicLong nextIdHolder = new AtomicLong(1L);

    @PostConstruct
    public void init() {
        String base = System.getProperty("user.home");
        Path dir = Paths.get(base, DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("创建文章存储目录失败: {}", dir, e);
        }
        filePath = dir.resolve(FILE);
    }

    @lombok.Data
    private static class ArticlesHolder {
        private long nextId = 1L;
        private List<ArticleRecord> list = new ArrayList<>();
    }

    private ArticlesHolder load() {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            ArticlesHolder h = new ArticlesHolder();
            h.nextId = nextIdHolder.get();
            return h;
        }
        try {
            String json = Files.readString(filePath);
            ArticlesHolder h = objectMapper.readValue(json, ArticlesHolder.class);
            if (h.list == null) h.list = new ArrayList<>();
            nextIdHolder.set(h.nextId);
            return h;
        } catch (Exception e) {
            log.warn("读取文章文件失败，使用空列表: {}", e.getMessage());
            ArticlesHolder h = new ArticlesHolder();
            h.nextId = nextIdHolder.get();
            h.list = new ArrayList<>();
            return h;
        }
    }

    private void save(ArticlesHolder holder) {
        if (filePath == null) return;
        try {
            holder.nextId = nextIdHolder.get();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), holder);
        } catch (IOException e) {
            log.error("保存文章文件失败", e);
            throw new RuntimeException("保存文章失败: " + e.getMessage());
        }
    }

    private static Article toEntity(ArticleRecord r) {
        Article a = new Article();
        a.setId(r.getId());
        a.setUserId(r.getUserId());
        a.setTitle(r.getTitle());
        a.setContent(r.getContent() != null ? r.getContent() : "");
        a.setContentType(r.getContentType());
        a.setAuthor(r.getAuthor());
        a.setSummary(r.getSummary());
        a.setCoverImage(r.getCoverImage());
        a.setVideoUrl(r.getVideoUrl());
        a.setCategory(r.getCategory());
        a.setTags(r.getTags());
        a.setStatus(r.getStatus());
        a.setCreateTime(r.getCreateTime());
        a.setUpdateTime(r.getUpdateTime());
        a.setPlatformSettings(r.getPlatformSettings());
        return a;
    }

    private static ArticleRecord toRecord(Article a) {
        ArticleRecord r = new ArticleRecord();
        r.setId(a.getId());
        r.setUserId(a.getUserId());
        r.setTitle(a.getTitle());
        r.setContent(a.getContent());
        r.setContentType(a.getContentType());
        r.setAuthor(a.getAuthor());
        r.setSummary(a.getSummary());
        r.setCoverImage(a.getCoverImage());
        r.setVideoUrl(a.getVideoUrl());
        r.setCategory(a.getCategory());
        r.setTags(a.getTags());
        r.setStatus(a.getStatus());
        r.setCreateTime(a.getCreateTime());
        r.setUpdateTime(a.getUpdateTime());
        r.setPlatformSettings(a.getPlatformSettings());
        return r;
    }

    @Override
    public List<Article> findAll() {
        return load().list.stream().map(ArticleFileStorageImpl::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<Article> findByUserId(Long userId) {
        if (userId == null) return findAll();
        return load().list.stream()
                .filter(r -> userId.equals(r.getUserId()))
                .map(ArticleFileStorageImpl::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Article> findById(Long id) {
        if (id == null) return Optional.empty();
        return load().list.stream()
                .filter(r -> id.equals(r.getId()))
                .findFirst()
                .map(ArticleFileStorageImpl::toEntity);
    }

    @Override
    public Article save(Article article) {
        ArticlesHolder holder = load();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (article.getId() == null) {
            long id = nextIdHolder.getAndIncrement();
            article.setId(id);
            article.setCreateTime(now);
            article.setUpdateTime(now);
            if (article.getStatus() == null) article.setStatus(0);
            holder.list.add(toRecord(article));
        } else {
            holder.list.removeIf(r -> article.getId().equals(r.getId()));
            if (article.getCreateTime() == null) article.setCreateTime(now);
            article.setUpdateTime(now);
            holder.list.add(toRecord(article));
        }
        save(holder);
        return article;
    }

    @Override
    public void deleteById(Long id) {
        ArticlesHolder holder = load();
        holder.list.removeIf(r -> id.equals(r.getId()));
        save(holder);
    }
}
