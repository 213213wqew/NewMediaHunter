package com.news.publish.service;

import com.news.publish.model.entity.Platform;
import java.util.List;

public interface PlatformService {
    List<Platform> getAllPlatforms();
    Platform getPlatformById(Long id);
}
