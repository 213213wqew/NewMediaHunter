package com.news.publish.service;

import com.news.publish.model.entity.PlatformTask;
import java.util.List;

public interface PlatformTaskFileStorage {
    void deleteByPlatformKey(String platformKey);
    PlatformTask save(PlatformTask task);
    List<PlatformTask> findByPlatformKey(String platformKey);
}
