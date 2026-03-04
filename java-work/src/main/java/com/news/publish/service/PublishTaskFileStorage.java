package com.news.publish.service;

import com.news.publish.model.entity.PublishTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 发布任务本地文件存储：不依赖数据库，读写本地 JSON 文件。
 */
public interface PublishTaskFileStorage {
    List<PublishTask> findAll();

    List<PublishTask> findByUserId(Long userId);

    List<PublishTask> findByPublishStatusAndScheduledTimeBefore(Integer status, LocalDateTime time);

    Optional<PublishTask> findById(Long id);

    PublishTask save(PublishTask task);
}
