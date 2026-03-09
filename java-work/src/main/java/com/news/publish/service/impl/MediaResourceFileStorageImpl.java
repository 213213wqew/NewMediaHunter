package com.news.publish.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.news.publish.model.entity.MediaResource;
import com.news.publish.service.MediaResourceFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MediaResourceFileStorageImpl implements MediaResourceFileStorage {

    private static final String DIR = ".news-publisher";
    private static final String FILE = "media_resources.json";

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
            log.warn("创建媒体资源存储目录失败: {}", dir, e);
        }
        filePath = dir.resolve(FILE);
    }

    @lombok.Data
    private static class MediaHolder {
        private long nextId = 1L;
        private List<MediaResource> list = new ArrayList<>();
    }

    private MediaHolder load() {
        if (filePath == null || !Files.isRegularFile(filePath)) {
            MediaHolder h = new MediaHolder();
            h.nextId = nextIdHolder.get();
            return h;
        }
        try {
            String json = Files.readString(filePath);
            MediaHolder h = objectMapper.readValue(json, MediaHolder.class);
            if (h.list == null) h.list = new ArrayList<>();
            nextIdHolder.set(h.nextId);
            return h;
        } catch (Exception e) {
            log.warn("读取媒体资源文件失败，使用空列表: {}", e.getMessage());
            MediaHolder h = new MediaHolder();
            h.nextId = nextIdHolder.get();
            h.list = new ArrayList<>();
            return h;
        }
    }

    private void save(MediaHolder holder) {
        if (filePath == null) return;
        try {
            holder.nextId = nextIdHolder.get();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), holder);
        } catch (IOException e) {
            log.error("保存媒体资源文件失败", e);
            throw new RuntimeException("保存媒体资源文件失败: " + e.getMessage());
        }
    }

    @Override
    public List<MediaResource> findAll() {
        return load().list.stream()
            .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
            .collect(Collectors.toList());
    }

    @Override
    public List<MediaResource> findByUserId(Long userId) {
        if (userId == null) return findAll();
        return load().list.stream()
                .filter(m -> userId.equals(m.getUserId()))
                .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
                .collect(Collectors.toList());
    }

    @Override
    public List<MediaResource> findByUserIdAndFileType(Long userId, String fileType) {
        if (userId == null) return findByFileType(fileType);
        return load().list.stream()
                .filter(m -> userId.equals(m.getUserId()) && (fileType == null || fileType.equals(m.getFileType())))
                .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<MediaResource> findByArticleIdAndOriginalUrlAndPlatformId(Long articleId, String originalUrl, Long platformId) {
        if (articleId == null || originalUrl == null || platformId == null) return Optional.empty();
        return load().list.stream()
                .filter(m -> articleId.equals(m.getArticleId()) &&
                             originalUrl.equals(m.getOriginalUrl()) &&
                             platformId.equals(m.getPlatformId()))
                .findFirst();
    }

    @Override
    public Optional<MediaResource> findByOriginalUrl(String originalUrl) {
        if (originalUrl == null) return Optional.empty();
        return load().list.stream()
                .filter(m -> originalUrl.equals(m.getOriginalUrl()))
                .filter(m -> m.getPlatformMediaUrl() != null && m.getPlatformMediaUrl().startsWith("/api/file/view/"))
                .findFirst();
    }

    @Override
    public List<MediaResource> findByFileType(String fileType) {
         return load().list.stream()
                .filter(m -> fileType == null || fileType.equals(m.getFileType()))
                .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
                .collect(Collectors.toList());
    }

    @Override
    public MediaResource save(MediaResource resource) {
        MediaHolder holder = load();
        LocalDateTime now = LocalDateTime.now();
        if (resource.getId() == null) {
            long id = nextIdHolder.getAndIncrement();
            resource.setId(id);
            if (resource.getCreateTime() == null) resource.setCreateTime(now);
            if (resource.getUploadStatus() == null) resource.setUploadStatus(0);
            if (resource.getFileType() == null) resource.setFileType("image");
            holder.list.add(resource);
        } else {
            holder.list.removeIf(r -> resource.getId().equals(r.getId()));
            holder.list.add(resource);
        }
        save(holder);
        return resource;
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) return;
        MediaHolder holder = load();
        Optional<MediaResource> target = holder.list.stream()
                .filter(r -> id.equals(r.getId()))
                .findFirst();
        
        if (target.isPresent()) {
            MediaResource resource = target.get();
            deletePhysicalFile(resource);
            holder.list.remove(resource);
            save(holder);
            log.info("从存储库删除素材记录成功: id={}", id);
        }
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        MediaHolder holder = load();
        List<MediaResource> toDelete = holder.list.stream()
                .filter(r -> ids.contains(r.getId()))
                .collect(Collectors.toList());
        
        for (MediaResource resource : toDelete) {
            deletePhysicalFile(resource);
        }
        
        holder.list.removeAll(toDelete);
        save(holder);
        log.info("从存储库批量删除素材记录成功: size={}", toDelete.size());
    }

    @Override
    public void deleteAllByUserId(Long userId) {
        MediaHolder holder = load();
        List<MediaResource> toDelete;
        if (userId == null) {
            toDelete = new ArrayList<>(holder.list);
        } else {
            toDelete = holder.list.stream()
                    .filter(r -> userId.equals(r.getUserId()))
                    .collect(Collectors.toList());
        }
        
        for (MediaResource resource : toDelete) {
            deletePhysicalFile(resource);
        }
        
        holder.list.removeAll(toDelete);
        save(holder);
        log.info("从存储库清空素材记录成功: userId={}, size={}", userId, toDelete.size());
    }

    private void deletePhysicalFile(MediaResource resource) {
        String url = resource.getPlatformMediaUrl();
        if (url != null && url.startsWith("/api/file/view/")) {
            String fileName = url.substring("/api/file/view/".length());
            try {
                Path path = Paths.get("uploads", fileName);
                if (Files.exists(path)) {
                    Files.delete(path);
                    log.info("物理删除素材文件成功: {}", path);
                }
            } catch (IOException e) {
                log.warn("物理删除素材文件失败: {}", url, e);
            }
        }
    }
}
