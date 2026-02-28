package com.news.publish.repository;

import com.news.publish.model.entity.PublishTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PublishTaskRepository extends JpaRepository<PublishTask, Long> {
    List<PublishTask> findByUserId(Long userId);
    List<PublishTask> findByPublishStatus(Integer status);

    /**
     * 查询到达预定时间且状态为待处理的任务
     */
    List<PublishTask> findByPublishStatusAndScheduledTimeBefore(Integer status, LocalDateTime time);
}
