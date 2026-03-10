package com.news.publish.service;

import com.news.publish.model.entity.PublishLog;
import java.util.List;

public interface PublishLogFileStorage {
    PublishLog save(PublishLog log);
    List<PublishLog> findByTaskIdOrderByCreateTimeDesc(Long taskId);
}
