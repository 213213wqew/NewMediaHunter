package com.news.publish.repository;

import com.news.publish.model.entity.PublishLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PublishLogRepository extends JpaRepository<PublishLog, Long> {
    List<PublishLog> findByTaskIdOrderByCreateTimeDesc(Long taskId);
}
