package com.news.publish.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.news.publish.model.dto.PublishTaskRecord;
import com.news.publish.model.entity.PublishTask;
import com.news.publish.service.PublishTaskFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PublishTaskFileStorageImpl implements PublishTaskFileStorage {

    private static final String DIR = ".news-publisher";
    private static final String FILE = "publish_tasks.json";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Path filePath;
    private final AtomicLong nextIdHolder = new AtomicLong(1L);
    private final Object fileLock = new Object();

    @PostConstruct
    public void init() {
        String base = System.getProperty("user.home");
        Path dir = Paths.get(base, DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("创建发布任务存储目录失败: {}", dir, e);
        }
        filePath = dir.resolve(FILE);
    }

    @lombok.Data
    private static class TasksHolder {
        private long nextId = 1L;
        private List<PublishTaskRecord> list = new ArrayList<>();
    }

    private TasksHolder load() {
        synchronized (fileLock) {
            if (filePath == null || !Files.isRegularFile(filePath)) {
                TasksHolder h = new TasksHolder();
                h.nextId = nextIdHolder.get();
                return h;
            }
            try {
                String json = Files.readString(filePath);
                TasksHolder h = objectMapper.readValue(json, TasksHolder.class);
                if (h.list == null) h.list = new ArrayList<>();
                nextIdHolder.set(h.nextId);
                return h;
            } catch (Exception e) {
                log.warn("读取发布任务文件失败，使用空列表: {}", e.getMessage());
                TasksHolder h = new TasksHolder();
                h.nextId = nextIdHolder.get();
                h.list = new ArrayList<>();
                return h;
            }
        }
    }

    private void save(TasksHolder holder) {
        if (filePath == null) return;
        synchronized (fileLock) {
            holder.nextId = nextIdHolder.get();
            Path tmpPath = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmpPath.toFile(), holder);
                try {
                    Files.move(tmpPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Files.move(tmpPath, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                try { Files.deleteIfExists(tmpPath); } catch (IOException ignored) {}
                log.error("保存发布任务文件失败", e);
                throw new RuntimeException("保存发布任务失败: " + e.getMessage());
            }
        }
    }

    private static PublishTask toEntity(PublishTaskRecord r) {
        PublishTask t = new PublishTask();
        t.setId(r.getId());
        t.setUserId(r.getUserId());
        t.setArticleId(r.getArticleId());
        t.setAccountId(r.getAccountId());
        t.setPublishStatus(r.getPublishStatus());
        t.setPlatformArticleId(r.getPlatformArticleId());
        t.setPlatformArticleUrl(r.getPlatformArticleUrl());
        t.setErrorMessage(r.getErrorMessage());
        t.setPublishTime(r.getPublishTime());
        t.setCreateTime(r.getCreateTime());
        t.setUpdateTime(r.getUpdateTime());
        t.setScheduledTime(r.getScheduledTime());
        t.setBatchId(r.getBatchId());
        t.setAccountSequenceIndex(r.getAccountSequenceIndex());
        return t;
    }

    private static PublishTaskRecord toRecord(PublishTask t) {
        PublishTaskRecord r = new PublishTaskRecord();
        r.setId(t.getId());
        r.setUserId(t.getUserId());
        r.setArticleId(t.getArticleId());
        r.setAccountId(t.getAccountId());
        r.setPublishStatus(t.getPublishStatus());
        r.setPlatformArticleId(t.getPlatformArticleId());
        r.setPlatformArticleUrl(t.getPlatformArticleUrl());
        r.setErrorMessage(t.getErrorMessage());
        r.setPublishTime(t.getPublishTime());
        r.setCreateTime(t.getCreateTime());
        r.setUpdateTime(t.getUpdateTime());
        r.setScheduledTime(t.getScheduledTime());
        r.setBatchId(t.getBatchId());
        r.setAccountSequenceIndex(t.getAccountSequenceIndex());
        return r;
    }

    @Override
    public List<PublishTask> findAll() {
        return load().list.stream().map(PublishTaskFileStorageImpl::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<PublishTask> findByUserId(Long userId) {
        if (userId == null) return findAll();
        return load().list.stream()
                .filter(r -> userId.equals(r.getUserId()))
                .map(PublishTaskFileStorageImpl::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<PublishTask> findByPublishStatusAndScheduledTimeBefore(Integer status, LocalDateTime time) {
        if (time == null) return List.of();
        return load().list.stream()
                .filter(r -> status.equals(r.getPublishStatus()))
                .filter(r -> r.getScheduledTime() != null && !r.getScheduledTime().isAfter(time))
                .map(PublishTaskFileStorageImpl::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<PublishTask> findPendingByBatchId(Long batchId) {
        if (batchId == null) return List.of();
        return load().list.stream()
                .filter(r -> batchId.equals(r.getBatchId()) && Integer.valueOf(0).equals(r.getPublishStatus()))
                .map(PublishTaskFileStorageImpl::toEntity)
                .sorted((a, b) -> {
                    int ac = Long.compare(a.getAccountId(), b.getAccountId());
                    if (ac != 0) return ac;
                    int ai = (a.getAccountSequenceIndex() != null) ? a.getAccountSequenceIndex() : 0;
                    int bi = (b.getAccountSequenceIndex() != null) ? b.getAccountSequenceIndex() : 0;
                    return Integer.compare(ai, bi);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<PublishTask> findPendingBatchTasks() {
        return load().list.stream()
                .filter(r -> Integer.valueOf(0).equals(r.getPublishStatus()))
                .map(PublishTaskFileStorageImpl::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<PublishTask> findByBatchId(Long batchId) {
        if (batchId == null) return List.of();
        return load().list.stream()
                .filter(r -> batchId.equals(r.getBatchId()))
                .map(PublishTaskFileStorageImpl::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<PublishTask> findById(Long id) {
        if (id == null) return Optional.empty();
        return load().list.stream()
                .filter(r -> id.equals(r.getId()))
                .findFirst()
                .map(PublishTaskFileStorageImpl::toEntity);
    }

    @Override
    public PublishTask save(PublishTask task) {
        TasksHolder holder = load();
        LocalDateTime now = LocalDateTime.now();
        if (task.getId() == null) {
            long id = nextIdHolder.getAndIncrement();
            task.setId(id);
            task.setCreateTime(now);
            task.setUpdateTime(now);
            if (task.getPublishStatus() == null) task.setPublishStatus(0);
            holder.list.add(toRecord(task));
        } else {
            holder.list.removeIf(r -> task.getId().equals(r.getId()));
            if (task.getCreateTime() == null) task.setCreateTime(now);
            task.setUpdateTime(now);
            holder.list.add(toRecord(task));
        }
        save(holder);
        return task;
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) return;
        TasksHolder holder = load();
        boolean removed = holder.list.removeIf(r -> id.equals(r.getId()));
        if (removed) {
            save(holder);
        }
    }
}
