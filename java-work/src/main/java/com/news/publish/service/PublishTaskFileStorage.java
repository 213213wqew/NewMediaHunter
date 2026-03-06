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

    /** 某批次下状态为待处理(0)的任务，用于按账号串行调度 */
    List<PublishTask> findPendingByBatchId(Long batchId);

    /** 所有带批次的待处理任务(0)，用于调度时按账号取下一个 */
    List<PublishTask> findPendingBatchTasks();

    Optional<PublishTask> findById(Long id);

    PublishTask save(PublishTask task);
}
